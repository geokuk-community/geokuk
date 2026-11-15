package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.EKesVztah;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;
import cz.geokuk.plugins.kesoid.mvc.GccomNick;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.util.file.KeFile;

/** Táž keš ve více zdrojích: nalezená je, když ji jako nalezenou má kterýkoli zdroj; ostatní údaje jsou z nejnovějšího zdroje. */
public class NalezyViceZdrojuTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final Set<File> vypnute = new HashSet<>();
	private final Genom genom = new Genom();
	private File gpx;
	private File geoget;
	private MultiNacitac nacitac;
	private KesBag zobrazene;
	private long hodiny = 1_700_000_000_000L;
	private final ExecutorService jinyProgram = Executors.newSingleThreadExecutor();
	private final List<CountDownLatch> zamky = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		gpx = tmp.newFolder("gpx");
		geoget = tmp.newFolder("geoget");
		nacitac = new MultiNacitac(model(), new CasyDatZdroju());
	}

	@After
	public void uklid() {
		zamky.forEach(CountDownLatch::countDown);
		jinyProgram.shutdown();
	}

	@Test
	public void nectitelneDatumUJedneKopieNevyhraje() throws Exception {
		gpx("a.gpx", kes("GC0001", "a", "2019-1-5"), kes("GC0002", "a", "nevím"), kes("GC0003", "a", ""));
		gpx("b.gpx", kes("GC0001", "b", "2020-01-01"), kes("GC0002", "b", "2020-01-01"), kes("GC0003", "b", null));
		gpx("c-nejnovejsi.gpx", kes("GC0003", "c", null));
		nacti();
		Assert.assertEquals("neúplné datum", "2020-01-01", kes("GC0001").getFountTime());
		Assert.assertEquals("nečitelné datum", "2020-01-01", kes("GC0002").getFountTime());
		Assert.assertEquals("nález bez data", EKesVztah.FOUND, kes("GC0003").getVztah());
		Assert.assertNull(kes("GC0003").getFountTime());
	}

	@Test(timeout = 60_000)
	public void novejsiZamcenaDatabazeMaStavZMinula() throws Exception {
		final File starsi = gpx("a-stary.gpx", kes("GC0001", "stary", "2021-03-04"));
		final File db = geogetDb("novy.db3", "GC0001", 0);
		nacti();
		Assert.assertEquals("novy.db3", kes("GC0001").getAuthor());
		Assert.assertEquals(EKesVztah.FOUND, kes("GC0001").getVztah());
		zamkni(db);
		final long cas = starsi.lastModified();
		gpx("a-stary.gpx", kes("GC0001", "stary", "2015-06-07"));
		Assert.assertTrue(starsi.setLastModified(cas - 5_000));
		nacti();
		Assert.assertEquals("keš převzatá ze zamčené databáze se nemění", "2021-03-04", kes("GC0001").getFountTime());
		vypnute.add(starsi);
		nacti();
		Assert.assertEquals("převzatá ze zamčené databáze zůstane nalezená", EKesVztah.FOUND, kes("GC0001").getVztah());
	}

	@Test
	public void novejsiBezNalezuStarsiSNalezem() throws Exception {
		gpx("a-stary.gpx", kes("GC0001", "stary", "2021-03-04"), kes("GC0002", "stary", null));
		gpx("b-novy.gpx", kes("GC0001", "novy", null), kes("GC0002", "novy", null));
		nacti();
		Assert.assertEquals("údaje z nejnovějšího zdroje", "novy", kes("GC0001").getAuthor());
		Assert.assertEquals(EKesVztah.FOUND, kes("GC0001").getVztah());
		Assert.assertEquals("2021-03-04", kes("GC0001").getFountTime());
		Assert.assertEquals("bez nálezu nikde", EKesVztah.NORMAL, kes("GC0002").getVztah());
	}

	@Test
	public void novejsiSNalezemStarsiBez() throws Exception {
		gpx("a-stary.gpx", kes("GC0001", "stary", null));
		gpx("b-novy.gpx", kes("GC0001", "novy", "2022-05-06"));
		nacti();
		Assert.assertEquals("novy", kes("GC0001").getAuthor());
		Assert.assertEquals(EKesVztah.FOUND, kes("GC0001").getVztah());
		Assert.assertEquals("2022-05-06", kes("GC0001").getFountTime());
	}

	@Test
	public void obaSNalezemDatumZeStarsiho() throws Exception {
		gpx("a-stary.gpx", kes("GC0001", "stary", "2018-01-02"));
		gpx("b-novy.gpx", kes("GC0001", "novy", "2023-04-05"));
		nacti();
		Assert.assertEquals("novy", kes("GC0001").getAuthor());
		Assert.assertEquals("2018-01-02", kes("GC0001").getFountTime());
	}

	@Test
	public void triZdrojeDatumZNejstarsihoNalezu() throws Exception {
		geogetDb("a.db3", "GC0001", 20190102);
		gpx("b.gpx", kes("GC0001", "b", "2020-07-08T10:00:00Z"));
		gpx("c-nejnovejsi.gpx", kes("GC0001", "c", null));
		nacti();
		Assert.assertEquals("c", kes("GC0001").getAuthor());
		Assert.assertEquals(EKesVztah.FOUND, kes("GC0001").getVztah());
		Assert.assertEquals("nejstarší nález (GeoGet)", "20190102", kes("GC0001").getFountTime());
	}

	@Test
	public void nalezPrezijePrevzetiSkupinyAZmiziPoVypnutiZdroje() throws Exception {
		final File stary = gpx("a-stary.gpx", kes("GC0001", "stary", "2021-03-04"));
		gpx("b-novy.gpx", kes("GC0001", "novy", null));
		nacti();
		Assert.assertEquals(EKesVztah.FOUND, kes("GC0001").getVztah());
		nacti();
		Assert.assertEquals("převzatá skupina", EKesVztah.FOUND, kes("GC0001").getVztah());
		vypnute.add(stary);
		nacti();
		Assert.assertEquals("zdroj s nálezem vypnutý", EKesVztah.NORMAL, kes("GC0001").getVztah());
		Assert.assertNull(kes("GC0001").getFountTime());
		vypnute.clear();
		nacti();
		Assert.assertEquals("zdroj s nálezem znovu zapnutý", EKesVztah.FOUND, kes("GC0001").getVztah());
		Assert.assertEquals("2021-03-04", kes("GC0001").getFountTime());
	}

	@Test
	public void vlastniKesZustaneVlastni() throws Exception {
		gpx("a-stary.gpx", kes("GC0001", "stary", "2021-03-04"));
		gpx("b-novy.gpx", kes("GC0001", "Ja", null));
		nacti();
		Assert.assertEquals(EKesVztah.OWN, kes("GC0001").getVztah());
	}

	// ---------- data ----------

	private Kes kes(final String kod) {
		for (final Kesoid k : zobrazene.getKesoidy()) {
			if (k.getIdentifier().equals(kod)) {
				return (Kes) k;
			}
		}
		throw new AssertionError("chybí " + kod);
	}

	private void nacti() throws Exception {
		nacitac.setRootDirs(true, gpx, geoget, null, Collections.<File> emptySet());
		final KesBag bag = nacitac.nacti(null, genom);
		if (bag != null) {
			zobrazene = bag;
		}
	}

	/** Autor keše nese jméno zdroje; datum nálezu null = nenalezená. */
	private static String kes(final String kod, final String zdroj, final String nalezeno) {
		final String extra = nalezeno == null ? "" : "<gpxg:GeogetExtension xmlns:gpxg=\"https://www.geoget.cz/GpxExtensions/v2\"><gpxg:Found>" + nalezeno + "</gpxg:Found></gpxg:GeogetExtension>";
		return ImportKesiTest.kes(kod, nalezeno == null ? "Geocache" : "Geocache Found", "Traditional Cache", zdroj, 1, true, false, "2", extra);
	}

	private File gpx(final String jmeno, final String... wpts) throws Exception {
		final File f = new File(gpx, jmeno);
		Files.write(f.toPath(), ImportKesiTest.gpx(wpts).getBytes(StandardCharsets.UTF_8));
		dotkniSe(f);
		return f;
	}

	private File geogetDb(final String jmeno, final String kod, final long dtfound) throws SQLException {
		final File db = new File(geoget, jmeno);
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE geocache (id TEXT, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
					+ " cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT, dtfound INTEGER)");
			s.execute("CREATE TABLE geolist (id TEXT, shortdesc BLOB, hint TEXT)");
			s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, prefixid TEXT, wpttype TEXT, name TEXT)");
			s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)");
			s.execute("CREATE TABLE geotagcategory (key INTEGER, value TEXT)");
			s.execute("CREATE TABLE geotagvalue (key INTEGER, value TEXT)");
			s.execute("INSERT INTO geocache VALUES ('" + kod + "', 50.1, 14.4, 'Keš', '" + jmeno + "', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', " + dtfound
					+ ")");
		}
		dotkniSe(db);
		return db;
	}

	private void zamkni(final File db) throws Exception {
		final CountDownLatch zamceno = new CountDownLatch(1);
		final CountDownLatch pustit = new CountDownLatch(1);
		zamky.add(pustit);
		jinyProgram.submit(() -> {
			try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
				s.execute("BEGIN EXCLUSIVE");
				zamceno.countDown();
				pustit.await();
				s.execute("COMMIT");
			}
			return null;
		});
		zamceno.await();
	}

	private void dotkniSe(final File f) {
		hodiny += 10_000;
		Assert.assertTrue(f.setLastModified(hodiny));
	}

	private KesoidModel model() {
		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		final KesoidModel model = new KesoidModel() {
			private Set<String> casy = new LinkedHashSet<>();

			@Override
			public GccomNick getGccomNick() {
				return new GccomNick("Ja", 42);
			}

			@Override
			public KesBag getVsechnyKesoidy() {
				return zobrazene;
			}

			@Override
			public void fire(final Event0<?> udalost) {}

			@Override
			public void setNacitaneZdroje(final InformaceOZdrojich zdroje) {}

			@Override
			public void zaradGsakDatabaze(final Set<File> databaze, final Set<File> nedostupne) {}

			@Override
			public void zaradOpensakDatabaze(final Set<File> databaze, final Set<File> nedostupne) {}

			@Override
			public void zaradGeogetDatabaze(final Set<File> databaze, final Set<File> nedostupne) {}

			@Override
			public boolean maSeNacist(final File zdroj) {
				return !vypnute.contains(zdroj);
			}

			@Override
			public boolean maSeNacist(final KeFile zdroj) {
				return maSeNacist(zdroj.getFile());
			}

			@Override
			public synchronized Set<String> getCasyDatZdroju() {
				return casy;
			}

			@Override
			public synchronized void setCasyDatZdroju(final Set<String> zaznamy) {
				casy = new LinkedHashSet<>(zaznamy);
			}
		};
		model.inject(progress);
		model.inject(new KesoidPluginManager());
		return model;
	}
}
