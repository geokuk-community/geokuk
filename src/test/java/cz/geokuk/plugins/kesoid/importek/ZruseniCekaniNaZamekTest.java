package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.sql.*;
import java.util.concurrent.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Čekání na zámek cizí databáze skončí, jakmile se načítání zruší. */
public class ZruseniCekaniNaZamekTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final ExecutorService vlakna = Executors.newCachedThreadPool();
	private final CountDownLatch pustit = new CountDownLatch(1);
	private File db;

	@Before
	public void setUp() throws Exception {
		db = tmp.newFile("geoget.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE geocache (id TEXT)");
		}
		final CountDownLatch zamceno = new CountDownLatch(1);
		vlakna.submit(() -> {
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

	@After
	public void uklid() {
		pustit.countDown();
		vlakna.shutdownNow();
	}

	@Test
	public void zruseniUkonciCekani() throws Exception {
		final CompletableFuture<Void> nacitani = new CompletableFuture<>();
		final Future<Long> dotaz = vlakna.submit(() -> cti(nacitani, DatabazeJinehoProgramu.CEKANI_NA_ZAMEK_MS));
		Thread.sleep(300);
		nacitani.cancel(false);
		final long trvani = dotaz.get(10, TimeUnit.SECONDS);
		Assert.assertTrue("po zrušení se na zámek nečeká, trvalo " + trvani + " ms", trvani < 5_000);
	}

	@Test
	public void bezZruseniSeCekaDoLimitu() throws Exception {
		final long trvani = vlakna.submit(() -> cti(new CompletableFuture<Void>(), 500)).get(10, TimeUnit.SECONDS);
		Assert.assertTrue("čeká se celý limit, trvalo " + trvani + " ms", trvani >= 450);
	}

	/** Doba, než dotaz skončil chybou zámku. */
	private long cti(final Future<?> nacitani, final int limitMs) throws Exception {
		DatabazeJinehoProgramu.setNacitani(nacitani);
		final long start = System.nanoTime();
		try (Connection c = DatabazeJinehoProgramu.otevri(db, limitMs); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM geocache")) {
			Assert.fail("databáze je zamčená");
		} catch (final SQLException e) {
			Assert.assertTrue(e.toString(), DatabazeJinehoProgramu.jeZamcena(e));
		} finally {
			DatabazeJinehoProgramu.setNacitani(null);
		}
		return (System.nanoTime() - start) / 1_000_000;
	}
}
