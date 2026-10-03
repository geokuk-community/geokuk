package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;

/** Tagy z databáze GeoGetu musí být u keše už ve chvíli, kdy ji loader předá dál. */
public class GeogetLoaderTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void tagyJsouUKeseUzPriPridani() throws Exception {
		final File db = new File(tmp.getRoot(), "test.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE geocache (id TEXT, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
					+ " cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT, dtfound INTEGER)");
			s.execute("CREATE TABLE geolist (id TEXT, shortdesc BLOB, hint TEXT)");
			s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, prefixid TEXT, wpttype TEXT, name TEXT)");
			s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)");
			s.execute("CREATE TABLE geotagcategory (key INTEGER, value TEXT)");
			s.execute("CREATE TABLE geotagvalue (key INTEGER, value TEXT)");
			s.execute("INSERT INTO geocache VALUES ('GC12345', 50.1, 14.4, 'Keš', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geocache VALUES ('GC99999', 50.2, 14.5, 'Bez tagů', 'autor', 'Traditional Cache', 'Regular', '1', '1', 0, 1, 20191123, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geotagcategory VALUES (1, 'Hodnoceni'), (2, 'Znamka'), (3, 'favorites'), (4, 'Elevation'), (5, 'BestOf'), (6, 'geokuk_barva')");
			s.execute("INSERT INTO geotagvalue VALUES (1, '80%'), (2, '75'), (3, '12'), (4, '450'), (5, '3'), (6, 'modra')");
			s.execute("INSERT INTO geotag VALUES ('GC12345', 1, 1), ('GC12345', 2, 2), ('GC12345', 3, 3), ('GC12345', 4, 4), ('GC12345', 5, 5), ('GC12345', 6, 6)");
		}

		final Map<String, String> priPridani = new HashMap<>();
		final Map<String, String> texty = new HashMap<>();
		final IImportBuilder builder = new IImportBuilder() {
			@Override
			public void addGpxWpt(final GpxWpt w) {
				texty.put(w.name, w.time + "|" + w.desc + "|" + w.link.text);
				priPridani.put(w.name, w.gpxg.hodnoceni + "/" + w.gpxg.znamka + "/" + w.gpxg.favorites + "/" + w.gpxg.elevation + "/" + w.gpxg.bestOf + "/" + w.gpxg.userTags);
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
			public void setTrackName(final String aTrackName) {}
		};

		new GeogetLoader().nacti(db, builder, null, progress());

		Assert.assertEquals("80/75/12/450/3/{barva=modra}", priPridani.get("GC12345"));
		Assert.assertEquals("keš bez tagů má hodnocení neuvedené", "-1/-1/-1/0/-1/{}", priPridani.get("GC99999"));
		Assert.assertEquals("2020-01-01T00:00:00.000|Keš by autor (2 / 3)|Keš by autor", texty.get("GC12345"));
		Assert.assertEquals("2019-11-23T00:00:00.000|Bez tagů by autor (1 / 1)|Bez tagů by autor", texty.get("GC99999"));
	}
	/** Popisy se při načítání nečtou, hint se dotáhne až na požádání. */
	@Test
	public void popisyANapovedyAzNaPozadani() throws Exception {
		final File db = new File(tmp.getRoot(), "vadny.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			vytvorTabulky(s);
			s.execute("INSERT INTO geocache VALUES ('GC00001', 50.1, 14.4, 'První', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geocache VALUES ('GC00002', 50.2, 14.5, 'Druhá', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geocache VALUES ('GC00003', 50.3, 14.6, 'Třetí', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
			try (PreparedStatement ps = c.prepareStatement("INSERT INTO geolist VALUES (?,?,?)")) {
				zapisPopis(ps, "GC00001", zabal("První popis"), "Pod kamenem");
				zapisPopis(ps, "GC00002", "tohle není zabalené".getBytes(StandardCharsets.UTF_8), "");
			}
		}

		final Map<String, GpxWpt> nactene = nacti(db);
		Assert.assertEquals("vadný popis nevadí, popisy se nečtou", new HashSet<>(Arrays.asList("GC00001", "GC00002", "GC00003")), nactene.keySet());
		Assert.assertNull(nactene.get("GC00001").groundspeak.encodedHints);
		Assert.assertEquals("Pod kamenem", nactene.get("GC00001").groundspeak.hintZDatabaze.get());
		Assert.assertNull("keš bez řádku v geolist nemá hint", nactene.get("GC00003").groundspeak.hintZDatabaze.get());
	}

	/** Nesmyslná hodnota tagu nesmí zabránit načtení keše. */
	@Test
	public void vadnaHodnotaTaguNeshodiNacitani() throws Exception {
		final File db = new File(tmp.getRoot(), "tagy.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			vytvorTabulky(s);
			s.execute("INSERT INTO geocache VALUES ('GC00001', 50.1, 14.4, 'První', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geotagcategory VALUES (1, 'Hodnoceni'), (3, 'favorites')");
			s.execute("INSERT INTO geotagvalue VALUES (1, '0'), (2, '7')");
			s.execute("INSERT INTO geotag VALUES ('GC00001', 1, 1), ('GC00001', 3, 2)");
		}

		final Map<String, GpxWpt> nactene = nacti(db);
		Assert.assertEquals(1, nactene.size());
		Assert.assertEquals("nesmyslné hodnocení se zahodí", Gpxg.NEUVEDENO, nactene.get("GC00001").gpxg.hodnoceni);
		Assert.assertEquals("ostatní tagy se načtou", 7, nactene.get("GC00001").gpxg.favorites);
	}

	/** Starší GeoGet nemá některé sloupce, keše se přesto načtou. */
	@Test
	public void starsiSchemaBezNepovinnychSloupcu() throws Exception {
		final File db = new File(tmp.getRoot(), "stary.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE geocache (id TEXT, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
					+ " cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT)");
			s.execute("CREATE TABLE geolist (id TEXT, shortdesc BLOB)");
			s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, wpttype TEXT, name TEXT)");
			s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)");
			s.execute("CREATE TABLE geotagcategory (key INTEGER, value TEXT)");
			s.execute("CREATE TABLE geotagvalue (key INTEGER, value TEXT)");
			s.execute("INSERT INTO geocache VALUES ('GC00001', 50.1, 14.4, 'První', NULL, 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha')");
			s.execute("INSERT INTO waypoint VALUES ('GC00001', 50.11, 14.41, 'Parking Area', 'Parkoviště')");
		}

		final Map<String, GpxWpt> nactene = nacti(db);
		Assert.assertTrue(nactene.containsKey("GC00001"));
		Assert.assertNull(nactene.get("GC00001").groundspeak.encodedHints);
		try {
			nactene.get("GC00001").groundspeak.hintZDatabaze.get();
			Assert.fail("bez sloupce hint se má ohlásit chyba");
		} catch (final java.io.UncheckedIOException e) {
			Assert.assertTrue(e.getCause().getMessage().contains("GC00001"));
		}
		Assert.assertEquals("waypoint se načte i bez prefixu", 2, nactene.size());
	}

	/** Nesmyslná souřadnice jednoho waypointu nesmí zahodit ostatní. */
	@Test
	public void vadnyWaypointNeshodiOstatni() throws Exception {
		final File db = new File(tmp.getRoot(), "wpt.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			vytvorTabulky(s);
			s.execute("INSERT INTO waypoint VALUES ('GC00001', 50.1, 14.4, 'PK', 'Parking Area', 'První')");
			s.execute("INSERT INTO waypoint VALUES ('GC00002', 9e999, 14.4, 'PK', 'Parking Area', 'Nekonečná')");
			s.execute("INSERT INTO waypoint VALUES ('GC00003', 50.3, 14.6, 'PK', 'Parking Area', 'Třetí')");
		}

		Assert.assertEquals(new HashSet<>(Arrays.asList("PK00001", "PK00003")), nacti(db).keySet());
	}

	/** Poškozená tabulka tagů nesmí připravit uživatele o keše. */
	@Test
	public void poskozeneTagyNeshodiKese() throws Exception {
		final File db = new File(tmp.getRoot(), "poskozene.db3");
		final int stranka;
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("PRAGMA page_size = 1024");
			vytvorTabulky(s);
			s.execute("INSERT INTO geocache VALUES ('GC00001', 50.1, 14.4, 'První', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geotagcategory VALUES (3, 'favorites')");
			s.execute("INSERT INTO geotagvalue VALUES (2, '7')");
			s.execute("INSERT INTO geotag VALUES ('GC00001', 3, 2)");
			try (ResultSet rs = s.executeQuery("SELECT rootpage FROM sqlite_master WHERE name = 'geotag'")) {
				stranka = rs.getInt(1);
			}
		}
		try (RandomAccessFile f = new RandomAccessFile(db, "rw")) {
			f.seek((stranka - 1) * 1024L);
			final byte[] smeti = new byte[1024];
			Arrays.fill(smeti, (byte) 0x7f);
			f.write(smeti);
		}

		final Map<String, GpxWpt> nactene = nacti(db);
		Assert.assertEquals(Collections.singleton("GC00001"), nactene.keySet());
	}

	private static void vytvorTabulky(final Statement s) throws SQLException {
		s.execute("CREATE TABLE geocache (id TEXT, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
				+ " cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT, dtfound INTEGER)");
		s.execute("CREATE TABLE geolist (id TEXT, shortdesc BLOB, hint TEXT)");
		s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, prefixid TEXT, wpttype TEXT, name TEXT)");
		s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)");
		s.execute("CREATE TABLE geotagcategory (key INTEGER, value TEXT)");
		s.execute("CREATE TABLE geotagvalue (key INTEGER, value TEXT)");
	}

	private static void zapisPopis(final PreparedStatement ps, final String kod, final byte[] popis, final String hint) throws SQLException {
		ps.setString(1, kod);
		ps.setBytes(2, popis);
		ps.setString(3, hint);
		ps.executeUpdate();
	}

	private static byte[] zabal(final String text) throws IOException {
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (DeflaterOutputStream dos = new DeflaterOutputStream(bos)) {
			dos.write(text.getBytes(StandardCharsets.UTF_8));
		}
		return bos.toByteArray();
	}

	private static Map<String, GpxWpt> nacti(final File db) throws IOException {
		final Map<String, GpxWpt> nactene = new LinkedHashMap<>();
		new GeogetLoader().nacti(db, new SbiraciBuilder(nactene), null, progress());
		return nactene;
	}

	/** Builder, který jen posbírá, co loader předal. */
	private static class SbiraciBuilder implements IImportBuilder {

		private final Map<String, GpxWpt> nactene;

		SbiraciBuilder(final Map<String, GpxWpt> nactene) {
			this.nactene = nactene;
		}

		@Override
		public void addGpxWpt(final GpxWpt w) {
			nactene.put(w.name, w);
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
		public void setTrackName(final String aTrackName) {}
	}

	private static ProgressModel progress() {
		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		return progress;
	}
}
