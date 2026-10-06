package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.mvc.GsakParametryNacitani;
import cz.geokuk.util.exception.*;

/** Databáze GeoGetu a GSAKu se schématem jako ve skutečných programech; chybějící část schématu se ohlásí nebo načte bez ní. */
public class SchemaCiziDatabazeTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Založí databázi podle skriptu; {@code bez} jsou sloupce „tabulka.sloupec“ nebo celé tabulky, které v ní nebudou. */
	private File zaloz(final String jmeno, final String skript, final String[] data, final String... bez) throws Exception {
		final File db = new File(tmp.newFolder(), jmeno);
		final String sql;
		try (InputStream is = getClass().getResourceAsStream("/" + skript)) {
			sql = new String(com.google.common.io.ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
		}
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			for (final String prikaz : sql.split(";\\s*\\n")) {
				s.execute(prikaz);
			}
			for (final String d : data) {
				s.execute(d);
			}
			for (final String b : bez) {
				if (b.contains(".")) {
					s.execute("ALTER TABLE " + b.substring(0, b.indexOf('.')) + " DROP COLUMN " + b.substring(b.indexOf('.') + 1));
				} else {
					s.execute("DROP TABLE " + b);
				}
			}
		}
		return db;
	}

	private File geoget(final String... bez) throws Exception {
		return zaloz("geoget.db3", "geoget-schema.sql", new String[] {
			"INSERT INTO geocache (id, x, y, name, author, cachetype) VALUES ('GC1111', '50.1', '14.4', 'První', 'Kačer', 'Traditional Cache')",
			"INSERT INTO waypoint (id, x, y, name, prefixid, wpttype) VALUES ('GC1111', '50.2', '14.5', 'Parkoviště', 'PK', 'Parking Area')" }, bez);
	}

	private File gsak(final String... bez) throws Exception {
		return zaloz("sqlite.db3", "gsak-schema.sql", new String[] {
			"INSERT INTO Caches (Code, Name, PlacedBy, CacheType, Latitude, Longitude) VALUES ('GC1111', 'První', 'Kačer', 'T', '50.1', '14.4')",
			"INSERT INTO CacheMemo (Code) VALUES ('GC1111')",
			"INSERT INTO Waypoints (cParent, cCode, cPrefix, cName, cType, cLat, cLon) VALUES ('GC1111', 'PK1111', 'PK', 'Parkoviště', 'Parking Area', '50.2', '14.5')" }, bez);
	}

	private static ProgressModel progress() {
		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		return progress;
	}

	private static Map<String, GpxWpt> nacti(final Nacitac0 loader, final File db) throws IOException {
		return nacti(loader, db, g -> {});
	}

	private static Map<String, GpxWpt> nacti(final Nacitac0 loader, final File db, final java.util.function.Consumer<GpxWpt> kontrola) throws IOException {
		final Map<String, GpxWpt> w = new LinkedHashMap<>();
		loader.nacti(db, new IImportBuilder() {
			@Override
			public void addGpxWpt(final GpxWpt g) {
				kontrola.accept(g);
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
		}, null, progress());
		return w;
	}

	private static GsakDbLoader gsakLoader() {
		return new GsakDbLoader(GsakParametryNacitani::new);
	}

	private static void ocekavejJineSchema(final Nacitac0 loader, final File db, final String program, final String... chybi) throws IOException {
		try {
			nacti(loader, db);
			Assert.fail("Databáze s jinou strukturou se nemá načíst");
		} catch (final DatabazeJinehoProgramu.JineSchema e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains(program));
			for (final String c : chybi) {
				Assert.assertTrue(e.getMessage(), e.getMessage().contains(c));
			}
		}
	}

	@Test
	public void geogetSeSkutecnymSchematemSeNacte() throws Exception {
		final Map<String, GpxWpt> w = nacti(new GeogetLoader(), geoget());
		Assert.assertEquals(new HashSet<>(Arrays.asList("GC1111", "PK1111")), w.keySet());
		Assert.assertEquals(50.1, w.get("GC1111").wgs.lat, 1e-9);
		Assert.assertEquals("Kačer", w.get("GC1111").groundspeak.placedBy);
	}

	@Test
	public void geogetBezVolitelnychSloupcuSeNacte() throws Exception {
		final Map<String, GpxWpt> w = nacti(new GeogetLoader(), geoget("geocache.author", "geocache.country", "geocache.dtfound", "waypoint.wpttype"));
		Assert.assertEquals(new HashSet<>(Arrays.asList("GC1111", "PK1111")), w.keySet());
		Assert.assertNull(w.get("GC1111").groundspeak.placedBy);
	}

	@Test
	public void geogetBezPovinnehoSloupceSePreskoci() throws Exception {
		ocekavejJineSchema(new GeogetLoader(), geoget("geocache.y"), "GeoGetu", "geocache.y");
		ocekavejJineSchema(new GeogetLoader(), geoget("waypoint.x"), "GeoGetu", "waypoint.x");
	}

	@Test
	public void geogetBezPovinneTabulkyNeniGeoget() throws Exception {
		Assert.assertFalse(new GeogetLoader().umiNacist(geoget("waypoint")));
	}

	@Test
	public void gsakSeSkutecnymSchematemSeNacte() throws Exception {
		final Map<String, GpxWpt> w = nacti(gsakLoader(), gsak());
		Assert.assertEquals(new HashSet<>(Arrays.asList("GC1111", "PK1111")), w.keySet());
		Assert.assertEquals(14.4, w.get("GC1111").wgs.lon, 1e-9);
	}

	@Test
	public void gsakBezVolitelnychSloupcuSeNacte() throws Exception {
		final Map<String, GpxWpt> w = nacti(gsakLoader(), gsak("Caches.Elevation", "Caches.FavPoints", "Caches.LatOriginal", "Caches.LonOriginal", "Caches.OwnerName"));
		Assert.assertEquals(new HashSet<>(Arrays.asList("GC1111", "PK1111")), w.keySet());
	}

	@Test
	public void gsakBezWaypointuSeNacte() throws Exception {
		Assert.assertEquals(Collections.singleton("GC1111"), nacti(gsakLoader(), gsak("Waypoints")).keySet());
	}

	@Test
	public void gsakBezPovinnehoSloupceSePreskoci() throws Exception {
		ocekavejJineSchema(gsakLoader(), gsak("Caches.Latitude"), "GSAKu", "Caches.Latitude");
		ocekavejJineSchema(gsakLoader(), gsak("Waypoints.cLon"), "GSAKu", "Waypoints.cLon");
	}

	@Test
	public void gsakBezPovinneTabulkyNeniGsak() throws Exception {
		Assert.assertFalse(gsakLoader().umiNacist(gsak("CacheMemo")));
	}

	@Test
	public void geogetSVetsinouNectitelnychKesiSeOhlasi() throws Exception {
		final File db = geoget();
		final int dobrych = 10;
		final int vadnych = Preskocene.MIN_KESI_PRO_HLASKU + 5;
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			for (int i = 1; i <= dobrych + vadnych; i++) {
				s.execute("INSERT INTO geocache (id, x, y) VALUES ('GC" + (100 + i) + "', '50.0', '14.0')");
			}
		}
		final List<String> hlasky = hlaseni(() -> nacti(new GeogetLoader(), db, vadneOdKodu(1, dobrych)));
		Assert.assertEquals(hlasky.toString(), 1, hlasky.size());
		Assert.assertTrue(hlasky.get(0), hlasky.get(0).startsWith("DISPLAY") && hlasky.get(0).contains(vadnych + " z " + (dobrych + vadnych + 1)));
	}

	@Test
	public void gsakSVetsinouNectitelnychKesiSeOhlasi() throws Exception {
		final File db = gsak();
		final int dobrych = 10;
		final int vadnych = Preskocene.MIN_KESI_PRO_HLASKU + 5;
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			for (int i = 1; i <= dobrych + vadnych; i++) {
				s.execute("INSERT INTO Caches (Code, Latitude, Longitude) VALUES ('GC" + (100 + i) + "', '50.0', '14.0')");
			}
		}
		final List<String> hlasky = hlaseni(() -> nacti(gsakLoader(), db, vadneOdKodu(1, dobrych)));
		Assert.assertEquals(hlasky.toString(), 1, hlasky.size());
		Assert.assertTrue(hlasky.get(0), hlasky.get(0).startsWith("DISPLAY") && hlasky.get(0).contains(vadnych + " z " + (dobrych + vadnych + 1)));
	}

	/** Keš GC1111 a prvních {@code dobrych} dalších projde, ostatní spadnou při přidání. */
	private static java.util.function.Consumer<GpxWpt> vadneOdKodu(final int od, final int dobrych) {
		final int[] poradi = { 0 };
		return g -> {
			if (g.groundspeak != null && ++poradi[0] > dobrych + 1) {
				throw new IllegalStateException("vadná keš");
			}
		};
	}

	private static List<String> hlaseni(final Callable0 akce) throws Exception {
		final List<String> hlasky = new ArrayList<>();
		FExceptionDumper.setExceptionDumper(new ExceptionDumper() {
			@Override
			public synchronized AExcId dump(final Throwable t, final EExceptionSeverity s, final String okolnost, final ExceptionDumperRepositorySpi r) {
				hlasky.add(s + ": " + okolnost + ": " + t.getMessage());
				return null;
			}
		});
		try {
			akce.run();
		} finally {
			FExceptionDumper.setExceptionDumper(null);
		}
		return hlasky;
	}

	private interface Callable0 {
		void run() throws Exception;
	}
}
