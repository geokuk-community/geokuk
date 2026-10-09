package cz.geokuk.plugins.kesoid.importek;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.*;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;
import cz.geokuk.plugins.kesoid.mvc.GccomNick;
import cz.geokuk.plugins.kesoid.mvc.GsakParametryNacitani;
import cz.geokuk.util.file.*;

/** URL kešoidu je přesně odkaz, který dal loader, a stejné názvy přídavných waypointů jsou jeden objekt. Databáze GeoGetu, GSAKu, OpenSAKu i GPX. */
public class UrlANazvyKesoiduTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Odkaz z loaderu podle kódu waypointu. */
	private final Map<String, String> odkazy = new HashMap<>();

	@Test
	public void urlVsechKesoiduJakoOdkazZLoaderuANazvyPridavnychSdilene() throws Exception {
		final KesBag bag = nacti();
		int kesi = 0;
		for (final Kesoid k : bag.getKesoidy()) {
			if (k instanceof Kes) {
				kesi++;
				Assert.assertEquals(k.getIdentifier(), odkazy.get(k.getIdentifier()), k.getUrl());
			}
		}
		Assert.assertEquals("keše ze všech zdrojů", 9, kesi);

		final Map<String, Wpt> prvniSNazvem = new HashMap<>();
		int pridavnych = 0;
		for (final Wpt w : bag.getWpts()) {
			if (w != w.getKesoid().getFirstWpt()) {
				pridavnych++;
				final Wpt prvni = prvniSNazvem.putIfAbsent(w.getNazev(), w);
				if (prvni != null) {
					Assert.assertSame(w.getName(), prvni.getNazev(), w.getNazev());
				}
			}
		}
		Assert.assertEquals(6, pridavnych);
		Assert.assertEquals(new HashSet<>(Arrays.asList("Parkoviště", "Parking Area")), prvniSNazvem.keySet());
	}

	@Test
	public void urlKoordInfoSeNedrziUKazdeKese() throws Exception {
		final Kes a = kes("GC1AAA", "http://coord.info/GC1AAA");
		final Kes b = kes("GC2BBB", "http://coord.info/GC2BBB");
		final Kes c = kes("GC3CCC", "https://coord.info/GC3CCC");
		final Kes d = kes("GC4DDD", "https://coord.info/GC4DDD");
		Assert.assertEquals("http://coord.info/GC1AAA", a.getUrl());
		Assert.assertEquals("https://coord.info/GC3CCC", c.getUrl());
		Assert.assertSame(zbytekUrl(a), zbytekUrl(b));
		Assert.assertSame(zbytekUrl(c), zbytekUrl(d));
	}

	@Test
	public void ostatniUrlBezeZmeny() throws Exception {
		for (final String url : new String[] { "http://coord.info/GC9999", "http://coord.info/GC1AAAX", "https://coord.info/", "http://www.geocaching.com/seek/cache_details.aspx?guid=abc",
				"http://www.waymarking.com/waymarks/WM1", "https://www.geocaching.com/geocache/GC1AAA", "", "GC1AAA" }) {
			Assert.assertEquals(url, kes("GC1AAA", url).getUrl());
		}
		Assert.assertNull(kes("GC1AAA", null).getUrl());
		final Kes bezKodu = new Kes();
		bezKodu.setUrl("http://coord.info/GC1AAA");
		bezKodu.setIdentifier("GC1AAA");
		Assert.assertEquals("http://coord.info/GC1AAA", bezKodu.getUrl());
	}

	@Test
	public void zmenaKoduPoNastaveniUrlUrlNezmeni() throws Exception {
		final Kes k = kes("GC1AAA", "http://coord.info/GC1AAA");
		k.setIdentifier("GC2BBB");
		Assert.assertEquals("http://coord.info/GC1AAA", k.getUrl());
	}

	private static Kes kes(final String kod, final String url) {
		final Kes k = new Kes();
		k.setIdentifier(kod);
		k.setUrl(url);
		return k;
	}

	private static Object zbytekUrl(final Kesoid k) throws Exception {
		final Field f = Kesoid.class.getDeclaredField("zbytekUrl");
		f.setAccessible(true);
		return f.get(k);
	}

	private KesBag nacti() throws Exception {
		final File geoget = new File(tmp.getRoot(), "geoget.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + geoget); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE geocache (id TEXT, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
					+ " cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT, dtfound INTEGER)");
			s.execute("CREATE TABLE geolist (id TEXT, shortdesc BLOB, hint TEXT)");
			s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, prefixid TEXT, wpttype TEXT, name TEXT)");
			s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)");
			s.execute("CREATE TABLE geotagcategory (key INTEGER, value TEXT)");
			s.execute("CREATE TABLE geotagvalue (key INTEGER, value TEXT)");
			s.execute("INSERT INTO geocache VALUES ('GC00001', 50.1, 14.4, 'První', 'Autor', 'Traditional Cache', 'Small', '2', '3', 0, 1, 20190305, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geocache VALUES ('GC00002', 50.2, 14.5, 'Druhá', 'Autor', 'Multi-cache', 'Small', '2', '3', 0, 1, 20190305, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geocache VALUES ('GC00003', 50.3, 14.6, 'Třetí', 'Autor', 'Traditional Cache', 'Small', '2', '3', 0, 1, 20190305, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO waypoint VALUES ('GC00001', 50.101, 14.401, 'PK', 'Parking Area', 'Parkoviště')");
			s.execute("INSERT INTO waypoint VALUES ('GC00002', 50.201, 14.501, 'PK', 'Parking Area', 'Parkoviště')");
			s.execute("INSERT INTO waypoint VALUES ('GC00003', 50.301, 14.601, 'PK', 'Parking Area', 'Parkoviště')");
		}
		final File gsak = new File(tmp.getRoot(), "sqlite.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + gsak); Statement s = c.createStatement()) {
			for (final String t : new String[] { "Attributes", "CacheImages", "Corrected", "Filter", "Ignore", "LogImages", "LogMemo", "Logs" }) {
				s.execute("CREATE TABLE " + t + " (Code TEXT)");
			}
			s.execute("CREATE TABLE Caches (Code TEXT, Name TEXT, PlacedBy TEXT, Archived INTEGER, CacheType TEXT, Container TEXT, County TEXT, Country TEXT, Difficulty TEXT,"
					+ " FoundByMeDate TEXT, Latitude REAL, Longitude REAL, OwnerId INTEGER, OwnerName TEXT, PlacedDate TEXT, State TEXT, TempDisabled INTEGER, Terrain TEXT,"
					+ " LatOriginal REAL, LonOriginal REAL, Elevation INTEGER, FavPoints INTEGER)");
			s.execute("CREATE TABLE CacheMemo (Code TEXT, ShortDescription TEXT, Hints TEXT)");
			s.execute("CREATE TABLE Waypoints (cParent TEXT, cCode TEXT, cPrefix TEXT, cName TEXT, cType TEXT, cLat REAL, cLon REAL, cByuser INTEGER, cDate TEXT, cFlag INTEGER, sB1 INTEGER)");
			s.execute("CREATE TABLE Custom (Code TEXT, barva TEXT, CasNalezu TEXT)");
			s.execute("INSERT INTO Caches VALUES ('GC10001', 'Gsak', 'Kačer', 0, 'T', 'Small', 'Praha', 'Czech Republic', '2', '', 49.1, 15.4, 7, 'Kačer', '2015-06-01', 'Praha', 0, '2',"
					+ " 0, 0, 0, 0)");
			s.execute("INSERT INTO Caches VALUES ('GC10002', 'Gsak 2', 'Kačer', 0, 'T', 'Small', 'Praha', 'Czech Republic', '2', '', 49.2, 15.5, 7, 'Kačer', '2015-06-01', 'Praha', 0, '2',"
					+ " 0, 0, 0, 0)");
			s.execute("INSERT INTO CacheMemo VALUES ('GC10001', '', ''), ('GC10002', '', '')");
			s.execute("INSERT INTO Waypoints VALUES ('GC10001', 'PK10001', 'PK', 'Parking Area', 'Parking Area', 49.11, 15.41, 0, '', 0, 0)");
			s.execute("INSERT INTO Waypoints VALUES ('GC10002', 'PK10002', 'PK', 'Parking Area', 'Parking Area', 49.21, 15.51, 0, '', 0, 0)");
		}
		final File opensak = new File(tmp.getRoot(), "opensak.db");
		OpensakTestDb.zaloz(opensak, OpensakTestDb.verze());
		OpensakTestDb.vlozKes(opensak, 1, "GC20001", "Traditional Cache", 48.1, 16.4);
		final String gpx = ImportKesiTest.gpx(
				ImportKesiTest.kes("GC30001", "Geocache", "Traditional Cache", "Kačer", 7, true, false, "2", "<url>https://coord.info/GC30001</url>").replace("lat=\"50.1\"", "lat=\"47.1\""),
				ImportKesiTest.kes("GC30002", "Geocache", "Traditional Cache", "Kačer", 7, true, false, "2", "<url>http://www.geocaching.com/seek/cache_details.aspx?guid=a1b2</url>")
						.replace("lat=\"50.1\"", "lat=\"47.2\""),
				ImportKesiTest.kes("GC30003", "Geocache", "Traditional Cache", "Kačer", 7, true, false, "2", "<url>https://coord.info/GC99999</url>").replace("lat=\"50.1\"", "lat=\"47.3\""),
				"<wpt lat=\"47.11\" lon=\"14.4\"><name>PK30001</name><desc>Parkoviště</desc><sym>Parking Area</sym><type>Waypoint|Parking Area</type></wpt>\n");

		final ProgressModel progress = new ProgressModel();
		progress.inject(u -> {});
		final KesoidImportBuilder builder = new KesoidImportBuilder(new Genom(), new GccomNick("Ja", 42), progress, new KesoidPluginManager());
		builder.init();
		final IImportBuilder zapis = new IImportBuilder() {
			@Override
			public void addGpxWpt(final GpxWpt w) {
				odkazy.put(w.name, w.link.href);
				builder.addGpxWpt(w);
			}

			@Override
			public void init() {}

			@Override
			public void done() {}

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
		};
		zdroj(builder, geoget);
		new GeogetLoader().nacti(geoget, zapis, null, progress);
		zdroj(builder, gsak);
		new GsakDbLoader(GsakParametryNacitani::new).nacti(gsak, zapis, null, progress);
		zdroj(builder, opensak);
		new OpensakDbLoader().nacti(opensak, zapis, null, progress);
		final File gpxSoubor = new File(tmp.getRoot(), "a.gpx");
		zdroj(builder, gpxSoubor);
		new NacitacGpx().nacti(new ByteArrayInputStream(gpx.getBytes(StandardCharsets.UTF_8)), "a.gpx", zapis, null);
		builder.done();
		return builder.getKesBag();
	}

	private static void zdroj(final KesoidImportBuilder builder, final File f) {
		builder.setCurrentlyLoading(new KeFile(new FileAndTime(f, 0), new Root(f.getParentFile(), new Root.Def(0, null, null))), true);
	}
}
