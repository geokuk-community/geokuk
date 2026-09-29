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
			s.execute("INSERT INTO geocache VALUES ('GC99999', 50.2, 14.5, 'Bez tagů', 'autor', 'Traditional Cache', 'Regular', '1', '1', 0, 1, 20200101, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geotagcategory VALUES (1, 'Hodnoceni'), (2, 'Znamka'), (3, 'favorites'), (4, 'Elevation'), (5, 'BestOf'), (6, 'geokuk_barva')");
			s.execute("INSERT INTO geotagvalue VALUES (1, '80%'), (2, '75'), (3, '12'), (4, '450'), (5, '3'), (6, 'modra')");
			s.execute("INSERT INTO geotag VALUES ('GC12345', 1, 1), ('GC12345', 2, 2), ('GC12345', 3, 3), ('GC12345', 4, 4), ('GC12345', 5, 5), ('GC12345', 6, 6)");
		}

		final Map<String, String> priPridani = new HashMap<>();
		final IImportBuilder builder = new IImportBuilder() {
			@Override
			public void addGpxWpt(final GpxWpt w) {
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

		new GeogetLoader().nacti(db, builder, null, new ProgressModel());

		Assert.assertEquals("80/75/12/450/3/{barva=modra}", priPridani.get("GC12345"));
		Assert.assertEquals("keš bez tagů má hodnocení neuvedené", "-1/-1/-1/0/-1/{}", priPridani.get("GC99999"));
	}
	/** Vadný popis jedné keše nesmí připravit uživatele o celou databázi. */
	@Test
	public void vadnyPopisNeshodiCelouDatabazi() throws Exception {
		final File db = new File(tmp.getRoot(), "vadny.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			vytvorTabulky(s);
			s.execute("INSERT INTO geocache VALUES ('GC00001', 50.1, 14.4, 'První', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geocache VALUES ('GC00002', 50.2, 14.5, 'Druhá', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geocache VALUES ('GC00003', 50.3, 14.6, 'Třetí', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
			try (PreparedStatement ps = c.prepareStatement("INSERT INTO geolist VALUES (?,?,?)")) {
				zapisPopis(ps, "GC00001", zabal("První popis"));
				zapisPopis(ps, "GC00002", "tohle není zabalené".getBytes(StandardCharsets.UTF_8));
				zapisPopis(ps, "GC00003", zabal("Třetí popis"));
			}
		}

		final Map<String, GpxWpt> nactene = nacti(db);
		Assert.assertEquals("vadná keš se přeskočí, ostatní se načtou", new HashSet<>(Arrays.asList("GC00001", "GC00003")), nactene.keySet());
		Assert.assertEquals("První popis", nactene.get("GC00001").groundspeak.shortDescription);
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

	private static void vytvorTabulky(final Statement s) throws SQLException {
		s.execute("CREATE TABLE geocache (id TEXT, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
				+ " cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT, dtfound INTEGER)");
		s.execute("CREATE TABLE geolist (id TEXT, shortdesc BLOB, hint TEXT)");
		s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, prefixid TEXT, wpttype TEXT, name TEXT)");
		s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)");
		s.execute("CREATE TABLE geotagcategory (key INTEGER, value TEXT)");
		s.execute("CREATE TABLE geotagvalue (key INTEGER, value TEXT)");
	}

	private static void zapisPopis(final PreparedStatement ps, final String kod, final byte[] popis) throws SQLException {
		ps.setString(1, kod);
		ps.setBytes(2, popis);
		ps.setString(3, "");
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
		new GeogetLoader().nacti(db, new SbiraciBuilder(nactene), null, new ProgressModel());
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
}
