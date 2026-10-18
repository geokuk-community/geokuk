package cz.geokuk.smoke;

import java.awt.EventQueue;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
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

	/** Pomalá událost uvnitř vnořené smyčky (modální dialog) má vlastní zásobník a ke každé pomalé události se připíše krok scénáře a GC. */
	@Test(timeout = 30_000)
	public void vnorenaPomalaUdalostMaZasobnikAKrok() throws Exception {
		final HlidacEdt hlidac = HlidacEdt.zapni(200);
		try {
			hlidac.konecStartu();
			hlidac.krok("Menu > Položka");
			EventQueue.invokeAndWait(() -> {
				final SecondaryLoop smycka = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
				EventQueue.invokeLater(() -> {
					vnorenaDlouhaObsluha();
					smycka.exit();
				});
				smycka.enter();
			});
			hlidac.krok(null);
			EventQueue.invokeAndWait(() -> {});
			final List<String> pomale = hlidac.getPomale();
			final String vnorena = pomale.stream().filter(p -> p.contains("Vnořená událost")).findFirst().orElse(pomale.toString());
			Assert.assertTrue(vnorena, vnorena.contains("Zásobník EDT po 1500 ms:"));
			Assert.assertTrue(vnorena, vnorena.contains("HlidacEdtTest.vnorenaDlouhaObsluha"));
			Assert.assertTrue(vnorena, vnorena.contains("\nKrok: Menu > Položka"));
			Assert.assertTrue(vnorena, vnorena.contains("\nGC během události "));
			Assert.assertTrue(vnorena, vnorena.contains(", vzorkovač naposledy tikl před "));
		} finally {
			hlidac.vypni();
		}
	}

	/** Zásek EDT, při kterém vzorkovač tiká dál, je náš kód: počítá se celý. */
	@Test(timeout = 30_000)
	public void zasekEdtSeCelyPocita() throws Exception {
		final HlidacEdt hlidac = HlidacEdt.zapni(200);
		try {
			hlidac.konecStartu();
			EventQueue.invokeAndWait(() -> spi(HlidacEdt.PAUZA_MS + 500));
			EventQueue.invokeAndWait(() -> {});
			Assert.assertTrue("nejdelší " + hlidac.getNejdelsiMs(), hlidac.getNejdelsiMs() >= HlidacEdt.PAUZA_MS + 400);
			Assert.assertEquals(0, hlidac.getPocetPauz());
			Assert.assertFalse(hlidac.getPomale().toString(), hlidac.getPomale().toString().contains("Pauza JVM"));
		} finally {
			hlidac.vypni();
		}
	}

	/** Pauza JVM, která trvá ještě na konci události (vzorkovač od té doby netikl), se do délky události nepočítá. */
	@Test(timeout = 30_000)
	public void pauzaJvmDoKonceUdalostiSeNepocita() throws Exception {
		final HlidacEdt hlidac = HlidacEdt.zapni(200);
		try {
			hlidac.konecStartu();
			EventQueue.invokeAndWait(() -> {
				hlidac.zastavVzorkovac(true);
				spi(HlidacEdt.PAUZA_MS + 500);
			});
			hlidac.zastavVzorkovac(false);
			EventQueue.invokeAndWait(() -> {});
			Assert.assertTrue("nejdelší " + hlidac.getNejdelsiMs(), hlidac.getNejdelsiMs() < 400);
			final String pomale = hlidac.getPomale().toString();
			Assert.assertTrue(pomale, pomale.contains("\nPauza JVM "));
			Assert.assertTrue(pomale, pomale.contains(" se nepočítá, celkem "));
		} finally {
			hlidac.vypni();
		}
	}

	/** Pauza JVM uprostřed události se odečte, zbytek události se počítá. */
	@Test(timeout = 30_000)
	public void pauzaJvmUprostredUdalostiSeOdecte() throws Exception {
		final HlidacEdt hlidac = HlidacEdt.zapni(200);
		try {
			hlidac.konecStartu();
			EventQueue.invokeAndWait(() -> {
				hlidac.zastavVzorkovac(true);
				spi(HlidacEdt.PAUZA_MS + 500);
				hlidac.zastavVzorkovac(false);
				spi(600);
			});
			EventQueue.invokeAndWait(() -> {});
			Assert.assertEquals(1, hlidac.getPocetPauz());
			Assert.assertTrue("pauza " + hlidac.getNejdelsiPauzaMs(), hlidac.getNejdelsiPauzaMs() >= HlidacEdt.PAUZA_MS + 400);
			Assert.assertTrue("nejdelší " + hlidac.getNejdelsiMs(), hlidac.getNejdelsiMs() >= 450 && hlidac.getNejdelsiMs() < 1000);
		} finally {
			hlidac.vypni();
		}
	}

	private static void vnorenaDlouhaObsluha() {
		spi(HlidacEdt.PRAH_ZASOBNIKU_MS + 700);
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
