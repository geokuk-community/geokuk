package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

/** Při ukončení programu se dlaždice čekající na zápis zapíšou celé, i když disk zapisuje pomalu. */
public class DopsaniNaDiskTest {

	@Test
	public void pomalyZapisSeDokonci() throws Exception {
		final ThreadPoolExecutor zapis = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
		final AtomicInteger zapsano = new AtomicInteger();
		for (int i = 0; i < 6; i++) {
			zapis.execute(() -> {
				spi(100);
				zapsano.incrementAndGet();
			});
		}
		zapis.shutdown();
		Assert.assertTrue(KachleZiskavac.pockejNaZapis(zapis, 250, 10_000));
		Assert.assertEquals(6, zapsano.get());
	}

	@Test
	public void zaseknutyZapisNezdrzi() throws Exception {
		final ThreadPoolExecutor zapis = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
		final CountDownLatch pustit = new CountDownLatch(1);
		zapis.execute(() -> {
			try {
				pustit.await();
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		zapis.shutdown();
		final long t = System.currentTimeMillis();
		Assert.assertFalse(KachleZiskavac.pockejNaZapis(zapis, 200, 10_000));
		Assert.assertTrue("čeká jen jeden krok", System.currentTimeMillis() - t < 2_000);
		pustit.countDown();
	}

	private static void spi(final long ms) {
		try {
			Thread.sleep(ms);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
