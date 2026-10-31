package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Složka offline map se při nečinnosti neprochází; nová nebo smazaná mapa se projeví i tak. */
public class OfflineMapySledovaniSlozkyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final AtomicInteger zmen = new AtomicInteger();
	private File slozka;
	private OfflineMapy mapy;

	@Before
	public void setUp() throws Exception {
		slozka = tmp.newFolder("offline-mapy");
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		mapy = new OfflineMapy(zmen::incrementAndGet);
		mapy.kontrolaSlozkyNs = 0;
		mapy.nastav(slozka, TemaOfflineMapy.VYCHOZI);
		mapy.pouzij().skonci();
		mapy.zkontrolujSlozku();
	}

	@After
	public void tearDown() {
		mapy.zavri();
	}

	private boolean pockejNaZmenu(final int ocekavano) throws Exception {
		final long konec = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
		while (zmen.get() < ocekavano && System.nanoTime() < konec) {
			mapy.zkontrolujSlozku();
			Thread.sleep(20);
		}
		return zmen.get() >= ocekavano;
	}

	@Test(timeout = 60000)
	public void beznaSlozkaSeBezZmenyNeprochazi() throws Exception {
		final long pruchodu = mapy.pruchodu;
		for (int i = 0; i < 100; i++) {
			mapy.zkontrolujSlozku();
		}
		Assert.assertEquals("při nezměněné složce žádný průchod navíc", pruchodu, mapy.pruchodu);
		Assert.assertEquals(0, zmen.get());
	}

	@Test(timeout = 60000)
	public void novaMapaSeProjevi() throws Exception {
		OfflineMapyTest.zkopirujMapu(slozka, "druha.map");
		Assert.assertTrue("nová mapa se projeví", pockejNaZmenu(1));
	}

	@Test(timeout = 60000)
	public void smazanaMapaSeProjevi() throws Exception {
		// Otevřená mapa se ve Windows maže až po zavření souboru, do té doby ji složka dál ukazuje.
		Assume.assumeFalse("otevřený soubor jde ve Windows smazat až po zavření", System.getProperty("os.name", "").startsWith("Windows"));
		OfflineMapyTest.zkopirujMapu(slozka, "druha.map");
		Assert.assertTrue(pockejNaZmenu(1));
		Assert.assertTrue(new File(slozka, "druha.map").delete());
		Assert.assertTrue("smazaná mapa se projeví", pockejNaZmenu(2));
	}

	@Test(timeout = 60000)
	public void bezSledovaniSeProchaziPriKazdeKontrole() throws Exception {
		mapy.sledovatSlozku = false;
		final long pruchodu = mapy.pruchodu;
		for (int i = 0; i < 10; i++) {
			mapy.zkontrolujSlozku();
		}
		Assert.assertEquals(pruchodu + 10, mapy.pruchodu);
	}
}
