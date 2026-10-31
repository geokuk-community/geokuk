package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Rectangle;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.core.coord.PrvniNacitaniMapy;

/** Proužek načítání se ukáže až po prodlevě a zmizí s poslední dlaždicí. */
public class IndikatorNacitaniTest {

	@Test
	public void kratkeCekaniProuzekNeukaze() {
		final IndikatorNacitani i = new IndikatorNacitani();
		Assert.assertFalse(i.aktualizuj(1000, 5));
		Assert.assertFalse(i.aktualizuj(1000 + IndikatorNacitani.PRODLEVA_MS - 1, 3));
		Assert.assertFalse(i.aktualizuj(1100, 0));
		Assert.assertFalse(i.cekaSe());
	}

	@Test
	public void dlouheCekaniProuzekUkaze() {
		final IndikatorNacitani i = new IndikatorNacitani();
		i.aktualizuj(1000, 5);
		Assert.assertTrue(i.aktualizuj(1000 + IndikatorNacitani.PRODLEVA_MS, 2));
		Assert.assertFalse("po načtení poslední zmizí hned", i.aktualizuj(2000, 0));
		Assert.assertFalse("nové čekání začíná prodlevou znovu", i.aktualizuj(2100, 4));
	}

	@Test
	public void prouzekZustaneVOblastiMapyAJeTenky() {
		for (long t = 0; t < 3000; t += 37) {
			final Rectangle p = IndikatorNacitani.prouzek(t, 800);
			Assert.assertTrue(p.x >= 0 && p.x + p.width <= 800);
			Assert.assertEquals(IndikatorNacitani.VYSKA, p.height);
			Assert.assertTrue(p.width <= 800 / 4);
		}
	}

	@Test
	public void slideKresliJenPoProdleveAPoDoruceniPrestane() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		// Při prvním načítání mapy kreslí čekání logo, proužek se až pak.
		PrvniNacitaniMapy.ukonci();
		try {
			final AtomicInteger cekajici = new AtomicInteger(3);
			final JIndikatorNacitaniSlide slide = new JIndikatorNacitaniSlide(cekajici::get);
			slide.tik();
			Assert.assertFalse(slide.isKresli());
			Thread.sleep(IndikatorNacitani.PRODLEVA_MS + 50);
			slide.tik();
			Assert.assertTrue(slide.isKresli());
			cekajici.set(0);
			slide.tik();
			Assert.assertFalse(slide.isKresli());
		} finally {
			PrvniNacitaniMapy.obnovProTesty();
		}
	}

	@Test
	public void priLoguPrvnihoNacitaniSeProuzekNeukaze() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		PrvniNacitaniMapy.obnovProTesty();
		Assert.assertTrue("první načítání probíhá", PrvniNacitaniMapy.probiha());
		try {
			final JIndikatorNacitaniSlide slide = new JIndikatorNacitaniSlide(() -> 3);
			slide.tik();
			Thread.sleep(IndikatorNacitani.PRODLEVA_MS + 50);
			slide.tik();
			Assert.assertFalse("logo má přednost před proužkem", slide.isKresli());
			PrvniNacitaniMapy.ukonci();
			slide.tik();
			Assert.assertTrue("po skončení prvního načítání se proužek ukáže", slide.isKresli());
		} finally {
			PrvniNacitaniMapy.obnovProTesty();
		}
	}

	@Test
	public void casovacSePoDokonceniCekaniZastavi() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		PrvniNacitaniMapy.ukonci();
		try {
			final AtomicInteger cekajici = new AtomicInteger(2);
			final JIndikatorNacitaniSlide slide = new JIndikatorNacitaniSlide(cekajici::get);
			Assert.assertFalse(slide.bezi());
			slide.probud();
			Assert.assertTrue("po nové zakázce časovač běží", slide.bezi());
			slide.tik();
			Assert.assertTrue("dokud se čeká, běží dál", slide.bezi());
			cekajici.set(0);
			slide.tik();
			Assert.assertFalse("po načtení poslední dlaždice se zastaví, ať netiká věčně", slide.bezi());
		} finally {
			PrvniNacitaniMapy.obnovProTesty();
		}
	}

	@Test
	public void cekaniZacinajiciVNulovemCaseMaProdlevu() {
		final IndikatorNacitani i = new IndikatorNacitani();
		Assert.assertFalse(i.aktualizuj(0, 4));
		Assert.assertTrue("začátek čekání se nepřepisuje", i.aktualizuj(IndikatorNacitani.PRODLEVA_MS, 4));
	}

	@Test
	public void geometrieProuzkuJenaPrikladech() {
		final int sirka = 800;
		final long perioda = IndikatorNacitani.PERIODA_MS;
		Assert.assertEquals("na začátku je celý mimo okno", 0, IndikatorNacitani.prouzek(0, sirka).width);
		final Rectangle pul = IndikatorNacitani.prouzek(perioda / 2, sirka);
		Assert.assertEquals(new Rectangle(300, 0, 200, IndikatorNacitani.VYSKA), pul);
		final Rectangle vpravo = IndikatorNacitani.prouzek(perioda * 9 / 10, sirka);
		Assert.assertEquals("u pravého okraje se ořízne", sirka, vpravo.x + vpravo.width);
		Assert.assertTrue(vpravo.width > 0 && vpravo.width < 200);
		Assert.assertEquals("perioda se opakuje", IndikatorNacitani.prouzek(100, sirka), IndikatorNacitani.prouzek(100 + 3 * perioda, sirka));
	}
}
