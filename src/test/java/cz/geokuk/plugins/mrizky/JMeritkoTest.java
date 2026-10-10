package cz.geokuk.plugins.mrizky;

import org.junit.Assert;
import org.junit.Test;

/** Měřítková lišta: dílky 1–2–5, zhruba stálá délka podle okna, hystereze na hranici dílku a popisky. */
public class JMeritkoTest {

	private static double pixluNaMetr(final int zoom, final double sirka) {
		return 256 * Math.pow(2, zoom) / (40_075_016.686 * Math.cos(Math.toRadians(sirka)));
	}

	@Test
	public void dilkyJednaDvaPet() {
		Assert.assertEquals(50, JMeritko.dilek(1, 40), 0);
		Assert.assertEquals(100, JMeritko.dilek(0.4, 40), 0);
		Assert.assertEquals(200, JMeritko.dilek(0.39, 40), 0);
		Assert.assertEquals(500, JMeritko.dilek(0.19, 40), 0);
		Assert.assertEquals(1_000_000, JMeritko.dilek(0.00004, 40), 0);
	}

	/** Posun přes celou Evropu na malém měřítku: lišta zůstane zhruba stejně dlouhá, okolo třetiny nejvyšší šířky. */
	@Test
	public void delkaListySePosunemMeniMalo() {
		over(1152, 320, 450); // okno 1536 px, cíl 384 px
		over(750, 200, 300); // okno 1000 px, cíl 250 px
	}

	private static void over(final int maximalniSirka, final int od, final int doDelky) {
		for (final int zoom : new int[] { 3, 4, 5, 9, 15 }) {
			final JMeritko meritko = new JMeritko();
			meritko.setMaximalniSirkaMeritka(maximalniSirka);
			for (double sirka = 0; sirka <= 75; sirka += 0.5) {
				meritko.setPixluNaMetr(pixluNaMetr(zoom, sirka));
				final int delka = meritko.getPocetDilku() * meritko.getPixluNaDilek();
				Assert.assertTrue(maximalniSirka + " z" + zoom + " " + sirka + "°: " + delka + " px", delka >= od && delka <= doDelky);
			}
		}
	}

	@Test
	public void naHraniciDilekNeskaceTamAZpet() {
		final JMeritko meritko = new JMeritko();
		meritko.setPixluNaMetr(51.0 / 100_000);
		Assert.assertEquals(100_000, meritko.getMetruNaDilek(), 0);
		meritko.setPixluNaMetr(47.0 / 100_000);
		Assert.assertEquals("pod hranicí o méně než 10 % zůstává", 100_000, meritko.getMetruNaDilek(), 0);
		meritko.setPixluNaMetr(51.0 / 100_000);
		Assert.assertEquals(100_000, meritko.getMetruNaDilek(), 0);
		meritko.setPixluNaMetr(44.0 / 100_000);
		Assert.assertEquals(200_000, meritko.getMetruNaDilek(), 0);
	}

	@Test
	public void popiskyVJednotceDilku() {
		final JMeritko meritko = new JMeritko();
		meritko.setPixluNaMetr(0.11); // dílek 500 m
		Assert.assertEquals(500, meritko.getMetruNaDilek(), 0);
		Assert.assertEquals("1500", meritko.popisek(1500));
		meritko.setPixluNaMetr(0.03); // dílek 2 km
		Assert.assertEquals(2000, meritko.getMetruNaDilek(), 0);
		Assert.assertEquals("6", meritko.popisek(6000));
	}
}
