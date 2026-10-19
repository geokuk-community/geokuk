package cz.geokuk.plugins.mrizky;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/** Měřítková lišta: dílky 1–2–5, aspoň 4 dílky, zhruba stálá délka podle okna, hystereze na hranici dílku a popisky s délkou a jednotkou na konci. */
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
		over(1152, 320, 560); // cíl 384 px, nejméně 4 dílky po nejvýš 137 px
		over(750, 200, 560); // cíl 250 px
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
		meritko.setMaximalniSirkaMeritka(1000);
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

	/** Okno 1900 a 1000 px na 50° s. š.: na konci lišty je její délka s jednotkou a popisky se nepřekrývají ani nevyčnívají. */
	@Test
	public void naKonciDelkaSJednotkouAAsponCtyriDilky() {
		boolean km = false;
		boolean m = false;
		for (final int sirkaOkna : new int[] { 1900, 1000 }) {
			final JMeritko meritko = new JMeritko();
			meritko.setMaximalniSirkaMeritka(JMeritkoSlide.maximalniSirka(sirkaOkna));
			for (int zoom = 6; zoom <= 18; zoom++) {
				meritko.setPixluNaMetr(pixluNaMetr(zoom, 50));
				final String kde = sirkaOkna + " px z" + zoom + ": ";
				final int pocet = meritko.getPocetDilku();
				final List<String> popisky = meritko.popisky();
				Assert.assertTrue(kde + pocet, pocet >= 4);
				Assert.assertEquals(kde + popisky, pocet + 1, popisky.size());
				Assert.assertEquals(kde + popisky, "0", popisky.get(0));
				final boolean vKm = meritko.getMetruNaDilek() >= 1000;
				final long delka = Math.round(pocet * meritko.getMetruNaDilek() / (vKm ? 1000 : 1));
				Assert.assertEquals(kde + popisky, delka + (vKm ? " km" : " m"), popisky.get(pocet));
				km |= vKm;
				m |= !vKm;
				for (int i = 0; i < pocet; i++) {
					final int sirkaTextu = meritko.getFontMetricsPopisku().stringWidth(popisky.get(i));
					Assert.assertTrue(kde + popisky.get(i) + " " + sirkaTextu + " px", sirkaTextu + 4 <= meritko.getPixluNaDilek());
				}
				Assert.assertTrue(kde + meritko.getPreferredSize().width, meritko.getPreferredSize().width <= JMeritkoSlide.maximalniSirka(sirkaOkna));
			}
		}
		Assert.assertTrue(km && m);
	}
}
