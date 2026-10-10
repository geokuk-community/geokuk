package cz.geokuk.plugins.mrizky;

import java.awt.Dimension;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;

/** Měřítková lišta: dílky 1–2–5, zhruba stálá délka, hystereze na hranici dílku, popisky a šířka. */
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

	/** Posun přes celou Evropu na malém měřítku: lišta zůstane zhruba stejně dlouhá (dřív 160 až 1152 px). */
	@Test
	public void delkaListySePosunemMeniMalo() {
		for (final int zoom : new int[] { 3, 4, 5, 9, 15 }) {
			final JMeritko meritko = new JMeritko();
			meritko.setMaximalniSirkaMeritka(1152);
			for (double sirka = 0; sirka <= 75; sirka += 0.5) {
				meritko.setPixluNaMetr(pixluNaMetr(zoom, sirka));
				final int delka = meritko.getPocetDilku() * meritko.getPixluNaDilek();
				Assert.assertTrue("z" + zoom + " " + sirka + "°: " + delka + " px", delka >= 140 && delka <= 300);
			}
		}
	}

	@Test
	public void naHraniciDilekNeskaceTamAZpet() {
		final JMeritko meritko = new JMeritko();
		meritko.setPixluNaMetr(41.0 / 100_000);
		Assert.assertEquals(100_000, meritko.getMetruNaDilek(), 0);
		meritko.setPixluNaMetr(39.0 / 100_000);
		Assert.assertEquals("pod hranicí o méně než 10 % zůstává", 100_000, meritko.getMetruNaDilek(), 0);
		meritko.setPixluNaMetr(41.0 / 100_000);
		Assert.assertEquals(100_000, meritko.getMetruNaDilek(), 0);
		meritko.setPixluNaMetr(35.0 / 100_000);
		Assert.assertEquals(200_000, meritko.getMetruNaDilek(), 0);
	}

	@Test
	public void popiskyVJednotceDilku() {
		final JMeritko meritko = new JMeritko();
		meritko.setPixluNaMetr(0.09); // dílek 500 m
		Assert.assertEquals(500, meritko.getMetruNaDilek(), 0);
		Assert.assertEquals("1500", meritko.popisek(1500));
		meritko.setPixluNaMetr(0.02); // dílek 2 km
		Assert.assertEquals(2000, meritko.getMetruNaDilek(), 0);
		Assert.assertEquals("6", meritko.popisek(6000));
	}

	@Test
	public void popisSirkyJenNaMalychMeritkach() {
		final Dimension okno = new Dimension(1000, 800);
		Assert.assertEquals("v šířce 50° s. š.", JMeritkoSlide.popisSirky(new Coord(JMeritkoSlide.MAX_MERITKO_S_POPISEM_SIRKY, new Wgs(50.2, 14.4).toMou(), okno, 0)));
		Assert.assertEquals("v šířce 34° j. š.", JMeritkoSlide.popisSirky(new Coord(4, new Wgs(-33.9, 18.4).toMou(), okno, 0)));
		Assert.assertNull(JMeritkoSlide.popisSirky(new Coord(JMeritkoSlide.MAX_MERITKO_S_POPISEM_SIRKY + 1, new Wgs(50.2, 14.4).toMou(), okno, 0)));
	}
}
