package cz.geokuk.plugins.mapy.kachle.podklady;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class KachleZiskavacVlaknaTest {

	@Test
	public void vlaknaProCteniZCache() {
		final int jader = Runtime.getRuntime().availableProcessors();
		assertTrue(KachleZiskavac.NTHREADS_DISK >= 2 && KachleZiskavac.NTHREADS_DISK <= 4);
		assertTrue(jader < 4 || KachleZiskavac.NTHREADS_DISK == 4);
	}

	@Test(timeout = 30000)
	public void priUkonceniSeNahledyZCacheVypnou() throws InterruptedException {
		final KachleZiskavac ziskavac = new KachleZiskavac();
		final CountDownLatch pred = new CountDownLatch(1);
		ziskavac.naPozadiProNahled(pred::countDown);
		assertTrue("před ukončením náhledy běží", pred.await(10, TimeUnit.SECONDS));

		ziskavac.dopisNaDisk();

		final CountDownLatch po = new CountDownLatch(1);
		ziskavac.naPozadiProNahled(po::countDown);
		assertFalse("po ukončení se náhled nespustí", po.await(300, TimeUnit.MILLISECONDS));
	}
}
