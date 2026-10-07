package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mvc.GccomNick;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.kesoid.mvc.StavZdrojuEvent;
import cz.geokuk.util.file.DirScanner;

/** Stav po položce zdrojů při načítání a hromadné přepínání. */
public class StavZdrojuNacitaniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final MyPreferences pref = MyPreferences.current().node("test-stav-zdroju");
	private final ExecutorService geoget = Executors.newCachedThreadPool();
	private final List<CountDownLatch> zamky = new ArrayList<>();
	private final List<StavZdroju> udalosti = new CopyOnWriteArrayList<>();
	private final AtomicInteger pocetUdalosti = new AtomicInteger();
	private final Genom genom = new Genom();
	private File gpx;
	private File slozkaGeogetu;
	private KesoidModel model;
	private MultiNacitac nacitac;

	@Before
	public void setUp() throws Exception {
		gpx = tmp.newFolder("gpx");
		slozkaGeogetu = tmp.newFolder("geoget");
		zapisGpx("a.gpx", "GC1111");
		model = model();
		nacitac = new MultiNacitac(model, new DirScanner(), model.getRegistrStavuZdroju());
	}

	@After
	public void uklid() throws Exception {
		zamky.forEach(CountDownLatch::countDown);
		geoget.shutdown();
		pref.removeNode();
	}

	private StavPolozky polozka(final String jmeno) {
		return nacitac.getRegistr().getSnimek().getPolozky().stream().filter(p -> p.getSoubor().getName().equals(jmeno)).findFirst().get();
	}

	@Test
	public void stavyPoNacteni() throws Exception {
		zalozGeoget("a.db3", "GC000A");
		start();
		Assert.assertNotNull(nacitac.nacti(null, genom));

		final StavPolozky g = polozka("a.gpx");
		Assert.assertEquals("a.gpx", g.getNazev());
		Assert.assertEquals(new File(gpx, "a.gpx").getPath(), g.getCesta());
		Assert.assertEquals(TypZdroje.GPX, g.getTyp());
		Assert.assertEquals(StavZdroje.NACTENO, g.getStav());
		Assert.assertEquals(1, g.getWpCelkem());
		Assert.assertEquals(1, g.getWpBrano());
		Assert.assertTrue(g.getVelikostNaDisku() > 0);
		final StavPolozky db = polozka("a.db3");
		Assert.assertEquals(TypZdroje.GEOGET, db.getTyp());
		Assert.assertEquals(StavZdroje.NACTENO, db.getStav());
		Assert.assertEquals(1, db.getWpCelkem());
		Assert.assertTrue(db.getVelikostNaDisku() > 0);
		Assert.assertTrue(db.isZapnuto());
	}

	@Test
	public void nazevGpxVPodslozceJeCestaKeKoreni() throws Exception {
		Assert.assertTrue(new File(gpx, "sub").mkdir());
		Files.copy(new File(gpx, "a.gpx").toPath(), new File(gpx, "sub/x.gpx").toPath());
		start();
		nacitac.nacti(null, genom);
		Assert.assertEquals("sub" + File.separator + "x.gpx", polozka("x.gpx").getNazev());
	}

	@Test
	public void duplicitaSeUkazeRozdilem() throws Exception {
		zapisGpx("b.gpx", "GC1111");
		start();
		Assert.assertNotNull(nacitac.nacti(null, genom));
		final StavPolozky a = polozka("a.gpx");
		final StavPolozky b = polozka("b.gpx");
		Assert.assertEquals(1, a.getWpCelkem());
		Assert.assertEquals(1, b.getWpCelkem());
		Assert.assertEquals("keš se bere jen z jednoho zdroje", 1, a.getWpBrano() + b.getWpBrano());
		Assert.assertEquals(1, a.getPocetDuplicit() + b.getPocetDuplicit());
	}

	@Test
	public void vadnaDatabazeMaStavChyba() throws Exception {
		zalozGeoget("a.db3", "GC000A");
		Files.write(new File(slozkaGeogetu, "b.db3").toPath(), "to neni sqlite, jen text delsi nez hlavicka souboru sqlite".getBytes(StandardCharsets.UTF_8));
		start();
		nacitac.nacti(null, genom);
		Assert.assertEquals(StavZdroje.CHYBA, polozka("b.db3").getStav());
		Assert.assertNotNull(polozka("b.db3").getChyba());
		Assert.assertEquals(StavZdroje.NACTENO, polozka("a.db3").getStav());
		Assert.assertEquals(StavZdroje.CHYBA, nacitac.getRegistr().getSnimek().getStavTypu(TypZdroje.GEOGET));
	}

	@Test
	public void zamcenaDatabazeCekaNaZapis() throws Exception {
		final File db = zalozGeoget("a.db3", "GC000A");
		final CountDownLatch pustit = zamkni(db);
		start();
		nacitac.nacti(null, genom);
		Assert.assertEquals(StavZdroje.CEKA_NA_ZAPIS, polozka("a.db3").getStav());
		Assert.assertEquals(StavZdroje.NACTENO, polozka("a.gpx").getStav());
		Assert.assertEquals(StavZdroje.CEKA_NA_ZAPIS, nacitac.getRegistr().getSnimek().getStavTypu(TypZdroje.GEOGET));

		pustit.countDown();
		Thread.sleep(300);
		Assert.assertNotNull(nacitac.nacti(null, genom));
		Assert.assertEquals(StavZdroje.NACTENO, polozka("a.db3").getStav());
	}

	@Test
	public void vypnutyZdrojSeNenacita() throws Exception {
		final File db = zalozGeoget("a.db3", "GC000A");
		start();
		nacitac.nacti(null, genom);
		model.setNacitatTyp(TypZdroje.GEOGET, false);
		Assert.assertEquals(StavZdroje.VYPNUTO, polozka("a.db3").getStav());
		Assert.assertEquals("počet z minula zůstane", 1, polozka("a.db3").getWpCelkem());
		Assert.assertEquals(StavZdroje.NACTENO, polozka("a.gpx").getStav());
		Assert.assertFalse(model.maSeNacist(db));
		Assert.assertEquals("stejný formát nastavení jako dosud", Collections.singleton(db), new HashSet<>(pref.node("kesoid").getFileCollection("blokovaneZdroje", null)));
	}

	@Test
	public void hromadnePrepnutiJeJednaUdalostAJednaZmenaStavu() throws Exception {
		zalozGeoget("a.db3", "GC000A");
		zalozGeoget("b.db3", "GC000B");
		start();
		nacitac.nacti(null, genom);
		SwingUtilities.invokeAndWait(() -> {});
		final int pred = pocetUdalosti.get();

		model.setNacitatVse(false);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(pred + 1, pocetUdalosti.get());
		for (final StavPolozky p : model.getStavZdroju().getPolozky()) {
			Assert.assertEquals(p.toString(), StavZdroje.VYPNUTO, p.getStav());
		}
		Assert.assertFalse(model.getStavZdroju().isTypZapnut(TypZdroje.GPX));

		model.setNacitatTyp(TypZdroje.GEOGET, true);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(pred + 2, pocetUdalosti.get());
		Assert.assertEquals(StavZdroje.CEKA_NA_RADU, polozka("a.db3").getStav());
		Assert.assertEquals(StavZdroje.CEKA_NA_RADU, polozka("b.db3").getStav());
		Assert.assertEquals(StavZdroje.VYPNUTO, polozka("a.gpx").getStav());
		Assert.assertTrue(model.getStavZdroju().isCelyTypZapnut(TypZdroje.GEOGET));

		start(); // jako startLoad po změně nastavení
		Assert.assertNotNull(nacitac.nacti(null, genom));
		Assert.assertEquals(StavZdroje.NACTENO, polozka("a.db3").getStav());
		Assert.assertEquals(StavZdroje.VYPNUTO, polozka("a.gpx").getStav());
	}

	private CountDownLatch zamkni(final File db) throws Exception {
		return zamkni(db, null);
	}

	/** Zamkne databázi; když je zadaný kód, jiný program do ní během zámku přidá keš. */
	private CountDownLatch zamkni(final File db, final String novaKes) throws Exception {
		final CountDownLatch zamceno = new CountDownLatch(1);
		final CountDownLatch pustit = new CountDownLatch(1);
		zamky.add(pustit);
		geoget.submit(() -> {
			try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
				s.execute("BEGIN EXCLUSIVE");
				if (novaKes != null) {
					s.execute(insertKese(novaKes));
				}
				zamceno.countDown();
				pustit.await();
				s.execute("COMMIT");
			}
			return null;
		});
		zamceno.await();
		return pustit;
	}

	private void zapisGpx(final String jmeno, final String kod) throws Exception {
		Files.write(new File(gpx, jmeno).toPath(), ImportKesiTest.gpx(ImportKesiTest.kes(kod, "Geocache", "Traditional Cache", "Cizí", 1, true, false, "2", "")).getBytes(StandardCharsets.UTF_8));
	}

	private File zalozGeoget(final String jmeno, final String kod) throws SQLException {
		final File db = new File(slozkaGeogetu, jmeno);
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE geocache (id TEXT, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
					+ " cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT, dtfound INTEGER)");
			s.execute("CREATE TABLE geolist (id TEXT, shortdesc BLOB, hint TEXT)");
			s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, prefixid TEXT, wpttype TEXT, name TEXT)");
			s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)");
			s.execute("CREATE TABLE geotagcategory (key INTEGER, value TEXT)");
			s.execute("CREATE TABLE geotagvalue (key INTEGER, value TEXT)");
			s.execute(insertKese(kod));
		}
		return db;
	}

	private static String insertKese(final String kod) {
		return "INSERT INTO geocache VALUES ('" + kod + "', 50.1, 14.4, 'Keš', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)";
	}

	private void start() {
		nacitac.setRootDirs(true, gpx, slozkaGeogetu, null, Collections.emptySet());
	}

	private KesoidModel model() {
		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		final KesoidModel m = new KesoidModel() {
			@Override
			protected MyPreferences currPrefe() {
				return pref;
			}

			@Override
			public GccomNick getGccomNick() {
				return new GccomNick("Ja", 42);
			}

			@Override
			public KesBag getVsechnyKesoidy() {
				return null;
			}

			@Override
			public void fire(final Event0<?> udalost) {
				if (udalost instanceof StavZdrojuEvent) {
					pocetUdalosti.incrementAndGet();
					udalosti.add(((StavZdrojuEvent) udalost).getStav());
				}
			}

			@Override
			public void setNacitaneZdroje(final InformaceOZdrojich zdroje) {}

			@Override
			public void zaradGsakDatabaze(final Set<File> databaze, final Set<File> nedostupne) {}

			@Override
			public void zaradOpensakDatabaze(final Set<File> databaze, final Set<File> nedostupne) {}
		};
		m.inject(progress);
		m.inject(new KesoidPluginManager());
		return m;
	}
}
