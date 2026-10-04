package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.sql.*;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mvc.GccomNick;
import cz.geokuk.util.file.*;

/** Načtení keší z databáze OpenSAKu. */
public class OpensakDbLoaderTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File db;

	@Before
	public void setUp() throws Exception {
		db = new File(tmp.getRoot(), "Default.db");
		OpensakTestDb.zaloz(db, OpensakTestDb.verze());
		naplnKese(db);
	}

	static void naplnKese(final File db) throws SQLException {
		OpensakTestDb.vlozKes(db, 1, "GC1111", "Unknown Cache", 50.1, 14.4, "difficulty", 3.5, "terrain", 2.0, "container", "Small", "placed_by", "Kačer", "owner_name", "Kačer",
				"owner_id", "7", "hidden_date", "2015-06-01 00:00:00.000000", "country", "Czech Republic", "state", "Praha", "encoded_hints", "Pod kamenem", "favorite_points", 12,
				"elevation", 250.0, "found", 1, "found_date", "2020-05-01 10:15:00.000000");
		OpensakTestDb.vlozKes(db, 2, "GC2222", "Traditional Cache", 49.2, 16.6, "archived", 1, "available", 0);
		OpensakTestDb.vlozKes(db, 3, "GC3333", "Multi-cache", 49.5, 16.0, "available", 0);
		OpensakTestDb.vloz(db, "user_notes", "id", 1, "cache_id", 1, "corrected_lat", 50.2, "corrected_lon", 14.5, "is_corrected", 1, "updated_at", "2020-01-01");
		OpensakTestDb.vloz(db, "user_notes", "id", 2, "cache_id", 2, "corrected_lat", 49.3, "corrected_lon", 16.7, "is_corrected", 0, "updated_at", "2020-01-01");
		OpensakTestDb.vloz(db, "waypoints", "id", 1, "cache_id", 2, "prefix", "PK", "wp_type", "Parking Area", "name", "Parkoviště", "latitude", 49.21, "longitude", 16.61);
		OpensakTestDb.vloz(db, "waypoints", "id", 2, "cache_id", 3, "wp_code", "S13333", "wp_type", "Stages of a Multicache", "description", "Stage 1", "latitude", 49.51,
				"longitude", 16.01);
		OpensakTestDb.vloz(db, "waypoints", "id", 3, "cache_id", 3, "prefix", "RP", "wp_type", "Reference Point", "name", "Bez souřadnic");
	}

	static Map<String, GpxWpt> nacti(final File db) throws Exception {
		final Map<String, GpxWpt> w = new LinkedHashMap<>();
		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		new OpensakDbLoader().nacti(db, new Builder(w), null, progress);
		return w;
	}

	@Test
	public void rozpoznaDatabaziOpensaku() {
		Assert.assertTrue(new OpensakDbLoader().umiNacist(db));
	}

	@Test
	public void nerozpoznaJinouPriponu() throws Exception {
		final File jina = new File(tmp.getRoot(), "Default.db3");
		OpensakTestDb.zaloz(jina, 24);
		Assert.assertFalse(new OpensakDbLoader().umiNacist(jina));
	}

	@Test
	public void nerozpoznaDatabaziGsakuGeogetuAniCiziSqlite() throws Exception {
		final File gsak = new File(tmp.getRoot(), "gsak.db");
		sql(gsak, "CREATE TABLE Caches (Code TEXT, Name TEXT, Latitude REAL, Longitude REAL)", "CREATE TABLE CacheMemo (Code TEXT, Hints TEXT)", "CREATE TABLE Waypoints (cParent TEXT)");
		final File geoget = new File(tmp.getRoot(), "geoget.db");
		sql(geoget, "CREATE TABLE geocache (id TEXT, x REAL, y REAL)", "CREATE TABLE geolist (id TEXT)", "CREATE TABLE waypoint (id TEXT, x REAL, y REAL)");
		final File cizi = new File(tmp.getRoot(), "cizi.db");
		sql(cizi, "CREATE TABLE caches (id INTEGER, url TEXT)", "CREATE TABLE waypoints (id INTEGER)");
		final File bezWaypointu = new File(tmp.getRoot(), "bezwpt.db");
		sql(bezWaypointu, "CREATE TABLE caches (id INTEGER, gc_code TEXT)");
		for (final File f : Arrays.asList(gsak, geoget, cizi, bezWaypointu)) {
			Assert.assertFalse(f.getName(), new OpensakDbLoader().umiNacist(f));
		}
	}

	@Test
	public void kes() throws Exception {
		final GpxWpt kes = nacti(db).get("GC1111");
		Assert.assertEquals("Keš GC1111", kes.groundspeak.name);
		Assert.assertEquals("Unknown Cache", kes.groundspeak.type);
		Assert.assertEquals("3.5", kes.groundspeak.difficulty);
		Assert.assertEquals("2", kes.groundspeak.terrain);
		Assert.assertEquals("Small", kes.groundspeak.container);
		Assert.assertEquals("Kačer", kes.groundspeak.placedBy);
		Assert.assertEquals("Kačer", kes.groundspeak.owner);
		Assert.assertEquals(7, kes.groundspeak.ownerid);
		Assert.assertEquals("Czech Republic", kes.groundspeak.country);
		Assert.assertEquals("Praha", kes.groundspeak.state);
		Assert.assertEquals("2015-06-01T00:00:00", kes.time);
		Assert.assertNull("hint se při načítání nečte", kes.groundspeak.encodedHints);
		Assert.assertEquals("Pod kamenem", kes.groundspeak.hintZDatabaze.get());
		Assert.assertEquals(12, kes.gpxg.favorites);
		Assert.assertEquals(250, kes.gpxg.elevation);
		Assert.assertEquals("http://coord.info/GC1111", kes.link.href);
	}

	@Test
	public void stavy() throws Exception {
		final Map<String, GpxWpt> w = nacti(db);
		final GpxWpt nalezena = w.get("GC1111");
		Assert.assertEquals("Geocache Found", nalezena.sym);
		Assert.assertEquals("2020-05-01T10:15:00", nalezena.gpxg.found);
		Assert.assertFalse(nalezena.groundspeak.archived);
		Assert.assertTrue(nalezena.groundspeak.availaible);

		final GpxWpt archivovana = w.get("GC2222");
		Assert.assertEquals("Geocache", archivovana.sym);
		Assert.assertNull(archivovana.gpxg.found);
		Assert.assertTrue(archivovana.groundspeak.archived);

		final GpxWpt zakazana = w.get("GC3333");
		Assert.assertFalse(zakazana.groundspeak.archived);
		Assert.assertFalse(zakazana.groundspeak.availaible);
	}

	@Test
	public void typyKesiJakoVGpx() {
		Assert.assertEquals("Traditional Cache", OpensakDbLoader.typKese("Traditional Cache"));
		Assert.assertEquals("Traditional Cache", OpensakDbLoader.typKese("Traditional"));
		Assert.assertEquals("Multi-cache", OpensakDbLoader.typKese("multi"));
		Assert.assertEquals("Unknown Cache", OpensakDbLoader.typKese("Mystery"));
		Assert.assertEquals("Earthcache", OpensakDbLoader.typKese("EarthCache"));
		Assert.assertEquals("GPS Adventures Exhibit", OpensakDbLoader.typKese("GPS Adventures Maze"));
		Assert.assertEquals("Lost and Found Event Caches", OpensakDbLoader.typKese("Community Celebration Event"));
		Assert.assertEquals("Project APE Cache", OpensakDbLoader.typKese("Project A.P.E. Cache"));
		Assert.assertEquals("Groundspeak HQ Cache", OpensakDbLoader.typKese("Geocaching HQ Cache"));
		Assert.assertEquals("Hotel/POI", OpensakDbLoader.typKese("Hotel/POI"));
	}

	@Test
	public void opraveneSouradniceJakoFinal() throws Exception {
		final Map<String, GpxWpt> w = nacti(db);
		Assert.assertEquals("keš zůstává na původním místě", 50.1, w.get("GC1111").wgs.lat, 1e-9);
		final GpxWpt fin = w.get("##1111");
		Assert.assertNotNull(fin);
		Assert.assertEquals("Final Location", fin.sym);
		Assert.assertEquals(50.2, fin.wgs.lat, 1e-9);
		Assert.assertEquals(14.5, fin.wgs.lon, 1e-9);
		Assert.assertNull("souřadnice nejsou označené jako opravené", w.get("##2222"));
		Assert.assertNull("bez poznámky není final", w.get("##3333"));
	}

	@Test
	public void waypointy() throws Exception {
		final Map<String, GpxWpt> w = nacti(db);
		final GpxWpt parkoviste = w.get("PK2222");
		Assert.assertNotNull(w.keySet().toString(), parkoviste);
		Assert.assertEquals("Parking Area", parkoviste.sym);
		Assert.assertEquals("Parkoviště", parkoviste.desc);
		Assert.assertEquals(49.21, parkoviste.wgs.lat, 1e-9);
		final GpxWpt stage = w.get("S13333");
		Assert.assertNotNull(w.keySet().toString(), stage);
		Assert.assertEquals("Stage 1", stage.desc);
		Assert.assertNull("waypoint bez souřadnic není na 0, 0", w.get("RP3333"));
	}

	/** OpenSAK ukládá neznámou polohu waypointu i jako 0, 0. */
	@Test
	public void waypointNaNuleSeVynecha() throws Exception {
		OpensakTestDb.vloz(db, "waypoints", "id", 10, "cache_id", 2, "prefix", "TR", "latitude", 0.0, "longitude", 0.0);
		OpensakTestDb.vloz(db, "waypoints", "id", 11, "cache_id", 2, "prefix", "QA", "latitude", 0.0, "longitude", 16.0);
		final Map<String, GpxWpt> w = nacti(db);
		Assert.assertNull(w.get("TR2222"));
		Assert.assertNotNull("jen jedna souřadnice nulová", w.get("QA2222"));
	}

	/** Kódy waypointů se v keši nesmí opakovat, ani když jsou její waypointy v tabulce prokládané waypointy jiné keše. */
	@Test
	public void prokladaneWaypointyMajiJedinecneKody() throws Exception {
		OpensakTestDb.vloz(db, "waypoints", "id", 10, "cache_id", 2, "prefix", "ST", "latitude", 49.22, "longitude", 16.62);
		OpensakTestDb.vloz(db, "waypoints", "id", 11, "cache_id", 3, "prefix", "ST", "latitude", 49.52, "longitude", 16.02);
		OpensakTestDb.vloz(db, "waypoints", "id", 12, "cache_id", 2, "prefix", "ST", "latitude", 49.23, "longitude", 16.63);
		final Map<String, GpxWpt> w = nacti(db);
		Assert.assertNotNull(w.keySet().toString(), w.get("ST2222"));
		Assert.assertNotNull(w.keySet().toString(), w.get("S02222"));
		Assert.assertEquals(49.22, w.get("ST2222").wgs.lat, 1e-9);
		Assert.assertEquals(49.23, w.get("S02222").wgs.lat, 1e-9);
	}

	/** Každý waypoint s polohou patří ke své keši, i když OpenSAK uloží prefix jiné délky nebo stejný prefix víckrát. */
	@Test
	public void waypointyPatriKeKesim() throws Exception {
		final File d = new File(tmp.getRoot(), "Waypointy.db");
		OpensakTestDb.zaloz(d, OpensakTestDb.verze());
		OpensakTestDb.vlozKes(d, 1, "GC7A2JF", "Multi-cache", 50.0, 14.0);
		OpensakTestDb.vlozKes(d, 2, "GC4444", "Unknown Cache", 49.0, 15.0);
		int id = 0;
		for (final Object[] w : new Object[][] { { 1, "T", null }, { 1, "PK", null }, { 1, "PK", null }, { 1, "GC", null }, { 1, "STG", null }, { 1, null, null }, { 1, null, null },
				{ 2, null, "XY4444" }, { 2, "P", null }, { 2, "P0", null } }) {
			id++;
			OpensakTestDb.vloz(d, "waypoints", "id", id, "cache_id", w[0], "prefix", w[1], "wp_code", w[2], "wp_type", "Stages of a Multicache", "latitude", 49.0 + id / 100.0,
					"longitude", 15.0);
		}
		OpensakTestDb.vloz(d, "waypoints", "id", 20, "cache_id", 1, "prefix", "RP", "latitude", 0.0, "longitude", 0.0);
		OpensakTestDb.vloz(d, "waypoints", "id", 21, "cache_id", 99, "prefix", "PK", "latitude", 49.5, "longitude", 15.5);

		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		final KesoidImportBuilder builder = new KesoidImportBuilder(new Genom(), new GccomNick("Ja", 42), progress, new KesoidPluginManager());
		builder.init();
		builder.setCurrentlyLoading(new KeFile(new FileAndTime(d, 0), new Root(tmp.getRoot(), new Root.Def(0, null, null))), true);
		new OpensakDbLoader().nacti(d, builder, null, progress);
		builder.done();

		final Map<String, Kesoid> kesoidy = new HashMap<>();
		for (final Kesoid k : builder.getKesBag().getKesoidy()) {
			kesoidy.put(k.getIdentifier(), k);
		}
		Assert.assertEquals(kesoidy.keySet().toString(), new HashSet<>(Arrays.asList("GC7A2JF", "GC4444")), kesoidy.keySet());
		Assert.assertEquals(1 + 7, kesoidy.get("GC7A2JF").getWptsCount());
		Assert.assertEquals(1 + 3, kesoidy.get("GC4444").getWptsCount());
		Assert.assertEquals(2 + 10, builder.getKesBag().getWpts().size());
	}

	/** Starší OpenSAK nemá některé sloupce ani tabulku poznámek, keše se přesto načtou. */
	@Test
	public void bezVolitelnychSloupcu() throws Exception {
		final File starsi = new File(tmp.getRoot(), "Starsi.db");
		OpensakTestDb.zaloz(starsi, 15, "caches.encoded_hints", "caches.favorite_points", "caches.container", "caches.found_date", "caches.owner_id", "waypoints.prefix", "user_notes");
		OpensakTestDb.vlozKes(starsi, 1, "GC1111", "Traditional Cache", 50.1, 14.4);
		OpensakTestDb.vloz(starsi, "waypoints", "id", 1, "cache_id", 1, "wp_code", "PK1111", "latitude", 50.11, "longitude", 14.41);
		final Map<String, GpxWpt> w = nacti(starsi);
		final GpxWpt kes = w.get("GC1111");
		Assert.assertNotNull(kes);
		Assert.assertNull(kes.groundspeak.hintZDatabaze);
		Assert.assertEquals(Gpxg.NEUVEDENO, kes.gpxg.favorites);
		Assert.assertNotNull(w.get("PK1111"));
	}

	@Test
	public void bezPovinnehoSloupceSePreskoci() throws Exception {
		final File jina = new File(tmp.getRoot(), "Jina.db");
		OpensakTestDb.zaloz(jina, 24, "caches.latitude");
		OpensakTestDb.vloz(jina, "caches", "id", 1, "gc_code", "GC1111", "name", "x", "cache_type", "Traditional Cache", "longitude", 14.4);
		try {
			nacti(jina);
			Assert.fail();
		} catch (final DatabazeJinehoProgramu.JineSchema e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("OpenSAKu") && e.getMessage().contains("caches.latitude"));
		}
	}

	@Test
	public void novejsiSchemaSeNacte() throws Exception {
		final File novejsi = new File(tmp.getRoot(), "Novejsi.db");
		OpensakTestDb.zaloz(novejsi, OpensakTestDb.verze() + 1);
		naplnKese(novejsi);
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + novejsi); Statement s = c.createStatement()) {
			s.execute("ALTER TABLE caches ADD COLUMN novy_sloupec TEXT");
		}
		Assert.assertTrue(new OpensakDbLoader().umiNacist(novejsi));
		Assert.assertEquals("schéma v testu je ověřená verze", OpensakDbLoader.OVERENA_VERZE_SCHEMATU, OpensakTestDb.verze());
		Assert.assertTrue(nacti(novejsi).containsKey("GC1111"));
	}

	private static void sql(final File db, final String... prikazy) throws SQLException {
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			for (final String p : prikazy) {
				s.execute(p);
			}
		}
	}

	static class Builder implements IImportBuilder {
		private final Map<String, GpxWpt> w;

		Builder(final Map<String, GpxWpt> w) {
			this.w = w;
		}

		@Override
		public void addGpxWpt(final GpxWpt g) {
			w.put(g.name, g);
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
