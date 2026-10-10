package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.mvc.GsakParametryNacitani;

/** Texty spojené do jednoho sloupce dají stejné waypointy jako čtení po sloupcích, i s oddělovači, NULL, BLOBy a čísly v textových sloupcích. */
public class SpojeneTextyShodaTest {

	private static final int FUZZ = 10_000;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Hodnoty, které by rozdělení textu mohlo pokazit. */
	static List<Object> neprijemne() {
		final StringBuilder dlouhy = new StringBuilder();
		for (int i = 0; i < 10_000; i++) {
			dlouhy.append("dlouhý ");
		}
		return Arrays.asList(null, "", " ", "Žluťoučký kůň", "😀 keš", "\u001e", "\u001f", "a\u001eb", "a\u001fb", "\u001e\u001f\u001e", "konec\u001e", "\u001fzačátek", "a\u0000b",
				dlouhy.toString(), 2.5, 0.1 + 0.2, 1e300, -0.0, 1234567890123L, 0L, "Keš".getBytes(StandardCharsets.UTF_8), new byte[] { (byte) 0xC3 },
				new byte[] { 'a', (byte) 0xE2, (byte) 0x82 }, new byte[] { (byte) 0xFF, (byte) 0xFE, 0x1E }, new byte[0]);
	}

	/** Náhodný krátký text z písmen, diakritiky a emoji, občas s oddělovačem, občas NULL. */
	static Object nahodny(final Random r) {
		if (r.nextInt(10) == 0) {
			return null;
		}
		final String[] znaky = { "a", "ž", "😀", " ", "0" };
		final StringBuilder sb = new StringBuilder();
		for (int i = r.nextInt(8); i > 0; i--) {
			sb.append(r.nextInt(100) == 0 ? (r.nextBoolean() ? "\u001e" : "\u001f") : znaky[r.nextInt(znaky.length)]);
		}
		return sb.toString();
	}

	/** Hodnota pro řádek {@code i} a sloupec {@code j}: nejdřív každá nepříjemná v každém sloupci, pak náhodné. */
	static Object hodnota(final List<Object> neprijemne, final Random r, final int i, final int j) {
		return i < neprijemne.size() ? neprijemne.get((i + j) % neprijemne.size()) : nahodny(r);
	}

	@Test
	public void geoget() throws Exception {
		final File db = new File(tmp.getRoot(), "geoget.db3");
		final String[] texty = { "id", "name", "author", "cachetype", "cachesize", "difficulty", "terrain", "country", "state" };
		final String[] textyWpt = { "id", "prefixid", "wpttype", "name" };
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE geocache (id TEXT, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
					+ " cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT, dtfound INTEGER)");
			s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, name TEXT, prefixid TEXT, wpttype TEXT)");
			s.execute("CREATE TABLE geolist (id TEXT, hint TEXT)");
			s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)");
			s.execute("CREATE TABLE geotagcategory (key INTEGER PRIMARY KEY, value TEXT)");
			s.execute("CREATE TABLE geotagvalue (key INTEGER PRIMARY KEY, value TEXT)");
			vloz(c, "geocache", texty, new String[] { "x", "y", "cachestatus", "gs_ownerid", "dthidden", "dtfound" },
					(r, i) -> new Object[] { 49 + r.nextDouble(), 14 + r.nextDouble(), r.nextInt(3), r.nextInt(1000), 20200101 + r.nextInt(28), r.nextInt(3) == 0 ? 20210510L : 0L });
			vloz(c, "waypoint", textyWpt, new String[] { "x", "y" }, (r, i) -> new Object[] { 49 + r.nextDouble(), 14 + r.nextDouble() });
		}
		porovnej(new GeogetLoader(), db);
	}

	@Test
	public void gsak() throws Exception {
		final File db = new File(tmp.getRoot(), "sqlite.db3");
		final String[] texty = { "Code", "Name", "PlacedBy", "Container", "County", "Country", "Difficulty", "FoundByMeDate", "OwnerName", "PlacedDate", "State", "Terrain" };
		final String[] textyWpt = { "cParent", "cCode", "cPrefix", "cName", "cType", "cDate" };
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE Caches (Code TEXT, Name TEXT, PlacedBy TEXT, Archived INTEGER, CacheType TEXT, Container TEXT, County TEXT, Country TEXT, Difficulty TEXT,"
					+ " FoundByMeDate TEXT, Latitude REAL, Longitude REAL, OwnerId INTEGER, OwnerName TEXT, PlacedDate TEXT, State TEXT, TempDisabled INTEGER, Terrain TEXT,"
					+ " LatOriginal REAL, LonOriginal REAL, Elevation INTEGER, FavPoints INTEGER, UserData TEXT)");
			s.execute("CREATE TABLE CacheMemo (Code TEXT, Hints TEXT)");
			s.execute("CREATE TABLE Waypoints (cParent TEXT, cCode TEXT, cPrefix TEXT, cName TEXT, cType TEXT, cLat REAL, cLon REAL, cByuser INTEGER, cDate TEXT, cFlag INTEGER, sB1 INTEGER)");
			vloz(c, "Caches", texty, new String[] { "CacheType", "Latitude", "Longitude", "LatOriginal", "LonOriginal", "Archived", "TempDisabled", "OwnerId", "Elevation", "FavPoints" },
					(r, i) -> {
						final double lat = 49 + r.nextDouble();
						final double lon = 14 + r.nextDouble();
						final boolean opravene = r.nextInt(4) == 0;
						return new Object[] { new String[] { "T", "M", "U", "V" }[r.nextInt(4)], lat, lon, opravene ? lat + 0.01 : 0.0, opravene ? lon : 0.0, r.nextInt(2), r.nextInt(2),
								r.nextInt(1000), r.nextInt(900), r.nextInt(50) };
					});
			vloz(c, "Waypoints", textyWpt, new String[] { "cLat", "cLon", "cByuser", "cFlag", "sB1" },
					(r, i) -> new Object[] { 49 + r.nextDouble(), 14 + r.nextDouble(), r.nextInt(2), r.nextInt(2), r.nextInt(2) });
		}
		porovnej(new GsakDbLoader(GsakParametryNacitani::new), db);
	}

	@Test
	public void opensak() throws Exception {
		final File db = new File(tmp.getRoot(), "opensak.db");
		OpensakTestDb.zaloz(db, OpensakTestDb.verze());
		final String[] texty = { "name", "container", "placed_by", "owner_name", "owner_id", "hidden_date", "found_date", "country", "state" };
		final String[] textyWpt = { "prefix", "wp_type", "name", "wp_code", "description" };
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
			// Kód keše je klíč waypointů, proto bez NULL; typ a oddělovače v kódu pokryje jeho hodnota z náhodných textů.
			vloz(c, "caches", texty, new String[] { "id", "gc_code", "cache_type", "latitude", "longitude", "available", "archived", "found", "favorite_points", "elevation", "difficulty",
					"terrain" }, (r, i) -> new Object[] { i, "GC" + i + Objects.toString(nahodny(r), ""), new String[] { "Traditional Cache", "mystery", "jiný typ" }[r.nextInt(3)],
							49 + r.nextDouble(), 14 + r.nextDouble(), r.nextInt(2), r.nextInt(2), r.nextInt(2), r.nextInt(100), r.nextInt(900) + 0.5, 2.5, "3" });
			vloz(c, "waypoints", textyWpt, new String[] { "id", "cache_id", "latitude", "longitude" },
					(r, i) -> new Object[] { i, r.nextInt(FUZZ), 49 + r.nextDouble(), 14 + r.nextDouble() });
		}
		porovnej(new OpensakDbLoader(), db);
	}

	/** Načte databázi po sloupcích a se spojenými texty a porovná všechna pole všech waypointů. */
	private void porovnej(final Nacitac0 nacitac, final File db) throws Exception {
		final List<String> poSloupcich;
		SpojeneTexty.poSloupcich = true;
		try {
			poSloupcich = nacti(nacitac, db);
		} finally {
			SpojeneTexty.poSloupcich = false;
		}
		SpojeneTexty.ZALOZNI_RADKY.reset();
		final List<String> spojene = nacti(nacitac, db);
		Assert.assertTrue(poSloupcich.size() > FUZZ);
		// Řádky s oddělovačem v textu se čtou po sloupcích, ostatní ze spojeného sloupce.
		final long zalozni = SpojeneTexty.ZALOZNI_RADKY.sum();
		Assert.assertTrue("po sloupcích " + zalozni, zalozni > 0 && zalozni < poSloupcich.size() / 2);
		Assert.assertEquals(poSloupcich.size(), spojene.size());
		for (int i = 0; i < poSloupcich.size(); i++) {
			Assert.assertEquals("waypoint " + i, poSloupcich.get(i), spojene.get(i));
		}
	}

	private static List<String> nacti(final Nacitac0 nacitac, final File db) throws Exception {
		final List<String> vysledek = new ArrayList<>();
		final ProgressModel progress = new ProgressModel();
		progress.inject(u -> {});
		nacitac.nacti(db, new Zachytavac(w -> vysledek.add(vypis(w))), null, progress);
		return vysledek;
	}

	interface Radek {
		Object[] cisla(Random r, int i);
	}

	/** Řádky s nepříjemnými a náhodnými texty v textových sloupcích a čísly ze {@code cisla}. */
	private static void vloz(final Connection c, final String tabulka, final String[] texty, final String[] ostatni, final Radek cisla) throws SQLException {
		final List<Object> neprijemne = neprijemne();
		final Random r = new Random(tabulka.hashCode());
		final StringBuilder sloupce = new StringBuilder();
		final StringBuilder otazniky = new StringBuilder();
		for (final String s : texty) {
			sloupce.append(sloupce.length() == 0 ? "" : ", ").append(s);
			otazniky.append(otazniky.length() == 0 ? "?" : ", ?");
		}
		for (final String s : ostatni) {
			sloupce.append(", ").append(s);
			otazniky.append(", ?");
		}
		c.setAutoCommit(false);
		try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + tabulka + " (" + sloupce + ") VALUES (" + otazniky + ")")) {
			for (int i = 0; i < neprijemne.size() + FUZZ; i++) {
				for (int j = 0; j < texty.length; j++) {
					ps.setObject(j + 1, hodnota(neprijemne, r, i, j));
				}
				final Object[] o = cisla.cisla(r, i);
				for (int j = 0; j < o.length; j++) {
					ps.setObject(texty.length + j + 1, o[j]);
				}
				ps.executeUpdate();
			}
		}
		c.commit();
		c.setAutoCommit(true);
	}

	/** Všechna pole waypointu včetně vnořených objektů; čísla bitově. */
	static String vypis(final Object o) {
		final StringBuilder sb = new StringBuilder();
		vypis(o, sb);
		return sb.toString();
	}

	private static void vypis(final Object o, final StringBuilder sb) {
		if (o == null) {
			sb.append("null");
		} else if (o instanceof String) {
			sb.append('"').append(o).append('"');
		} else if (o instanceof Double) {
			sb.append(Double.doubleToRawLongBits((Double) o));
		} else if (o instanceof Number || o instanceof Boolean || o instanceof Character) {
			sb.append(o);
		} else if (o instanceof Map) {
			sb.append(new TreeMap<>((Map<?, ?>) o));
		} else if (o instanceof java.util.function.Function) {
			sb.append("funkce");
		} else {
			sb.append(o.getClass().getSimpleName()).append('{');
			for (Class<?> t = o.getClass(); t != Object.class; t = t.getSuperclass()) {
				for (final Field f : t.getDeclaredFields()) {
					if (Modifier.isStatic(f.getModifiers())) {
						continue;
					}
					f.setAccessible(true);
					sb.append(f.getName()).append('=');
					try {
						vypis(f.get(o), sb);
					} catch (final IllegalAccessException e) {
						throw new IllegalStateException(e);
					}
					sb.append(", ");
				}
			}
			sb.append('}');
		}
	}

	/** Builder, který jen předá waypointy. */
	static final class Zachytavac implements IImportBuilder {
		private final java.util.function.Consumer<GpxWpt> kam;

		Zachytavac(final java.util.function.Consumer<GpxWpt> kam) {
			this.kam = kam;
		}

		@Override
		public void init() {}

		@Override
		public void done() {}

		@Override
		public void addGpxWpt(final GpxWpt w) {
			kam.accept(w);
		}

		@Override
		public void addTrackWpt(final GpxWpt w) {}

		@Override
		public void begTrack() {}

		@Override
		public void begTrackSegment() {}

		@Override
		public void endTrack() {}

		@Override
		public void endTrackSegment() {}

		@Override
		public void setTrackName(final String s) {}
	}
}
