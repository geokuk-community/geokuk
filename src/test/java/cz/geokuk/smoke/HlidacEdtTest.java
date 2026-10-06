package cz.geokuk.smoke;

import java.awt.EventQueue;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class HlidacEdtTest {

	@Test(timeout = 30_000)
	public void pomalaUdalostMaZasobnikEdt() throws Exception {
		final HlidacEdt hlidac = HlidacEdt.zapni(200);
		try {
			hlidac.konecStartu();
			EventQueue.invokeAndWait(() -> {});
			EventQueue.invokeAndWait(HlidacEdtTest::dlouhaObsluha);
			EventQueue.invokeAndWait(HlidacEdtTest::kratkaPomalaObsluha);
			// invokeAndWait se vrátí před koncem dispatchEvent hlídače, zápis pomalé události počká na další událost.
			EventQueue.invokeAndWait(() -> {});
			final List<String> pomale = hlidac.getPomale();
			Assert.assertEquals(pomale.toString(), 2, pomale.size());
			Assert.assertTrue(pomale.get(0), pomale.get(0).contains("Zásobník EDT po 1500 ms:"));
			Assert.assertTrue(pomale.get(0), pomale.get(0).contains("HlidacEdtTest.dlouhaObsluha"));
			Assert.assertFalse("pod prahem zásobníku se nevzorkuje: " + pomale.get(1), pomale.get(1).contains("Zásobník"));
		} finally {
			hlidac.vypni();
		}
	}

	private static void dlouhaObsluha() {
		spi(HlidacEdt.PRAH_ZASOBNIKU_MS + 700);
	}

	private static void kratkaPomalaObsluha() {
		spi(400);
	}

	private static void spi(final long ms) {
		try {
			Thread.sleep(ms);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
