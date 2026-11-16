package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Nastavení offline map se mění z EDT a nesmí čekat na vlákno, které pod zámkem právě otevírá mapy (u velkých map a témat to trvá sekundy, #823).
 */
public class OfflineMapyEdtTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Jak dlouho vlákno drží zámek, jako by otevíralo mapy. */
	private static final long DRZENI_MS = 3000;

	/** Spustí vlákno, které drží zámek mapy, dokud se neuvolní {@code pust}; vrátí se, až zámek drží. */
	static Thread drzZamek(final Object mapy, final CountDownLatch pust) throws InterruptedException {
		final CountDownLatch drzi = new CountDownLatch(1);
		final Thread t = new Thread(() -> {
			synchronized (mapy) {
				drzi.countDown();
				try {
					pust.await(DRZENI_MS, TimeUnit.MILLISECONDS);
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}, "otevírání map");
		t.setDaemon(true);
		t.start();
		Assert.assertTrue(drzi.await(5, TimeUnit.SECONDS));
		return t;
	}

	/** Volání na jiném vlákně; vrátí, za kolik ms skončilo (nebo -1, když neskončilo do limitu). */
	static long trvaniMs(final Runnable r, final long limitMs) throws InterruptedException {
		final CountDownLatch hotovo = new CountDownLatch(1);
		final long start = System.nanoTime();
		final Thread t = new Thread(() -> {
			r.run();
			hotovo.countDown();
		}, "EDT");
		t.setDaemon(true);
		t.start();
		return hotovo.await(limitMs, TimeUnit.MILLISECONDS) ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) : -1;
	}

	@Test(timeout = 20000)
	public void stinovaniNecekaNaOtevirani() throws Exception {
		final OfflineMapy mapy = new OfflineMapy(() -> {});
		final CountDownLatch pust = new CountDownLatch(1);
		final Thread otevirani = drzZamek(mapy, pust);
		try {
			final long ms = trvaniMs(() -> {
				mapy.setStinovani(false); // změna
				mapy.setStinovani(false); // beze změny: i to dřív bralo zámek
			}, 1000);
			System.out.println("#823 měření: setStinovani při drženém zámku trvalo " + (ms < 0 ? ">1000" : ms) + " ms");
			Assert.assertTrue("setStinovani čeká na zámek otevírání map", ms >= 0 && ms < 500);
		} finally {
			pust.countDown();
			otevirani.join();
		}
	}

	/** Změna stínování bez zámku se projeví: další použití mapy otevře znovu a ohlásí změnu; stejná hodnota znovu neotvírá. */
	@Test(timeout = 20000)
	public void zmenaStinovaniOtevreMapyZnovu() throws Exception {
		final File slozka = tmp.newFolder("offline-mapy");
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final AtomicInteger zmen = new AtomicInteger();
		final OfflineMapy mapy = new OfflineMapy(zmen::incrementAndGet);
		mapy.kontrolaSlozkyNs = 0;
		mapy.nastav(slozka, TemaOfflineMapy.VYCHOZI);
		try {
			mapy.pouzij().skonci();
			Assert.assertEquals("první otevření změnu nehlásí", 0, zmen.get());
			mapy.setStinovani(true);
			mapy.pouzij().skonci();
			Assert.assertEquals("stejné stínování mapy znovu neotvírá", 0, zmen.get());
			mapy.setStinovani(false);
			mapy.pouzij().skonci();
			Assert.assertEquals("vypnuté stínování mapy otevřelo znovu", 1, zmen.get());
			mapy.pouzij().skonci();
			Assert.assertEquals(1, zmen.get());
		} finally {
			mapy.zavri();
		}
	}
}
