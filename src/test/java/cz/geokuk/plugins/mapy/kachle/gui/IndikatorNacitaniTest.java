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
}
