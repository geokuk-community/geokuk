package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.sql.*;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;

/** Tagy keší ze schématu GeoGetu (klíče, indexy) vyjdou stejně jako ze spojení geotag × kategorie × hodnota. */
public class GeogetTagyShodaTest {

	private static final String SPOJENI = "SELECT t.id, c.value, v.value FROM geotag t LEFT JOIN geotagcategory c ON t.ptrkat = c.key LEFT JOIN geotagvalue v ON t.ptrvalue = v.key"
			+ " WHERE (c.value IN ('favorites', 'Elevation', 'Hodnoceni-Pocet', 'Hodnoceni', 'BestOf', 'Znamka') or c.value like 'geokuk_%')";

	private static final String[] KATEGORIE = { "favorites", "Elevation", "Hodnoceni", "Hodnoceni-Pocet", "BestOf", "Znamka", "attribute", "CZ kraj", "geokuk_barva", "geokuk_typ",
			"GEOKUK_velka", "geokukXjina", "Geokuk_smisena" };

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void stejneJakoSpojeniTabulek() throws Exception {
		final File db = new File(tmp.getRoot(), "geoget.db3");
		final Random r = new Random(7);
		final List<String> kody = new ArrayList<>();
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE geocache (key INTEGER PRIMARY KEY, id TEXT, x TEXT, y TEXT, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
					+ " cachestatus INTEGER DEFAULT 0, gs_ownerid TEXT, dthidden INTEGER DEFAULT 0, country TEXT, state TEXT, dtfound INTEGER DEFAULT 0)");
			s.execute("CREATE TABLE geolist (key INTEGER PRIMARY KEY, id TEXT, shortdesc TEXT, hint TEXT)");
			s.execute("CREATE TABLE waypoint (key INTEGER PRIMARY KEY, id TEXT, x TEXT, y TEXT, name TEXT, prefixid TEXT, wpttype TEXT)");
			s.execute("CREATE TABLE geotag (key INTEGER PRIMARY KEY, id TEXT, flag INTEGER DEFAULT 0, ptrkat INTEGER DEFAULT 0, ptrvalue INTEGER DEFAULT 0)");
			s.execute("CREATE TABLE geotagcategory (key INTEGER PRIMARY KEY, value TEXT, flag INTEGER DEFAULT 0)");
			s.execute("CREATE TABLE geotagvalue (key INTEGER PRIMARY KEY, value TEXT, flag INTEGER DEFAULT 0)");
			s.execute("CREATE INDEX gtcvalueidx ON geotagcategory (value)");
			s.execute("CREATE INDEX gtididx ON geotag (id)");
			s.execute("CREATE INDEX gtptrkatidx ON geotag (ptrkat)");
			s.execute("CREATE INDEX gtptrvalueidx ON geotag (ptrvalue)");
			for (int i = 0; i < KATEGORIE.length; i++) {
				s.execute("INSERT INTO geotagcategory (key, value) VALUES (" + (i + 1) * 3 + ", '" + KATEGORIE[i] + "')");
			}
			for (int i = 1; i <= 120; i++) {
				// Hodnoty jsou čísla i s příponou, aby prošla všemi kategoriemi.
				s.execute("INSERT INTO geotagvalue (key, value) VALUES (" + i + ", '" + i + (i % 3 == 0 ? "%" : i % 3 == 1 ? "x" : "") + "')");
			}
			c.setAutoCommit(false);
			try (PreparedStatement kes = c.prepareStatement("INSERT INTO geocache (id, x, y, name, author) VALUES (?, '50.1', '14.4', 'Keš', 'autor')");
					PreparedStatement tag = c.prepareStatement("INSERT INTO geotag (id, ptrkat, ptrvalue) VALUES (?, ?, ?)")) {
				for (int i = 0; i < 2000; i++) {
					final String kod = String.format("GC%05d", i);
					kody.add(kod);
					kes.setString(1, kod);
					kes.executeUpdate();
				}
				for (int i = 0; i < 30000; i++) {
					tag.setString(1, kody.get(r.nextInt(kody.size())));
					// Občas kategorie nebo hodnota, která neexistuje.
					tag.setInt(2, r.nextInt(50) == 0 ? 999 : (r.nextInt(KATEGORIE.length) + 1) * 3);
					tag.setInt(3, r.nextInt(50) == 0 ? 999 : r.nextInt(120) + 1);
					tag.executeUpdate();
				}
			}
			c.commit();
		}

		final Map<String, String> ocekavane = new TreeMap<>();
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(SPOJENI)) {
			final Map<String, Gpxg> tagy = new HashMap<>();
			while (rs.next()) {
				final String kod = rs.getString(1);
				final String kategorie = rs.getString(2);
				final String hodnota = rs.getString(3);
				if (kod == null || kategorie == null || hodnota == null) {
					continue;
				}
				final Gpxg g = tagy.computeIfAbsent(kod, k -> new Gpxg());
				try {
					if (kategorie.startsWith("geokuk_")) {
						g.putUserTag(kategorie.substring("geokuk_".length()), hodnota);
					} else if (kategorie.equals("favorites")) {
						g.favorites = cislo(hodnota);
					} else if (kategorie.equals("Elevation")) {
						g.elevation = cislo(hodnota);
					} else if (kategorie.equals("BestOf")) {
						g.bestOf = cislo(hodnota);
					} else if (kategorie.equals("Znamka")) {
						g.znamka = cislo(hodnota);
					} else if (kategorie.equals("Hodnoceni")) {
						g.hodnoceni = cislo(hodnota.substring(0, hodnota.length() - 1));
					} else if (kategorie.equals("Hodnoceni-Pocet")) {
						g.hodnoceniPocet = cislo(hodnota.substring(0, hodnota.length() - 1));
					}
				} catch (final NumberFormatException e) {
					// vadnou hodnotu loader přeskočí
				}
			}
			for (final String kod : kody) {
				final Gpxg g = new Gpxg();
				final Gpxg z = tagy.get(kod);
				if (z != null) {
					g.favorites = z.favorites;
					g.elevation = z.elevation;
					g.bestOf = z.bestOf;
					g.hodnoceni = z.hodnoceni;
					g.hodnoceniPocet = z.hodnoceniPocet;
					g.znamka = z.znamka;
					g.userTags.putAll(z.userTags);
				}
				ocekavane.put(kod, popis(g));
			}
		}

		final Map<String, String> nactene = new TreeMap<>();
		new GeogetLoader().nacti(db, new SberacKesi(w -> nactene.put(w.name, popis(w.gpxg))), null, progress());

		Assert.assertEquals(ocekavane, nactene);
	}

	private static int cislo(final String text) {
		return Integer.parseInt(text);
	}

	private static String popis(final Gpxg g) {
		return g.favorites + "/" + g.elevation + "/" + g.bestOf + "/" + g.hodnoceni + "/" + g.hodnoceniPocet + "/" + g.znamka + "/" + new ArrayList<>(g.userTags.entrySet());
	}

	private static ProgressModel progress() {
		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		return progress;
	}

	private static final class SberacKesi implements IImportBuilder {
		private final java.util.function.Consumer<GpxWpt> kam;

		SberacKesi(final java.util.function.Consumer<GpxWpt> kam) {
			this.kam = kam;
		}

		@Override
		public void addGpxWpt(final GpxWpt w) {
			kam.accept(w);
		}

		@Override
		public void init() {}

		@Override
		public void done() {}

		@Override
		public void addTrackWpt(final GpxWpt wpt) {}

		@Override
		public void begTrack() {}

		@Override
		public void begTrackSegment() {}

		@Override
		public void endTrack() {}

		@Override
		public void endTrackSegment() {}

		@Override
		public void setTrackName(final String nazev) {}
	}
}
