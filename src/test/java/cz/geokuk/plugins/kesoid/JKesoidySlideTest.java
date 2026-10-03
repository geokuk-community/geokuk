package cz.geokuk.plugins.kesoid;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;

public class JKesoidySlideTest {

	/** Bez sady ikon výpočet sklivce selže; vlákno musí přežít a zpracovat další požadavky. */
	@Test(timeout = 30000)
	public void paintovaciVlaknoPrezijeVyjimku() throws Exception {
		final JKesoidySlide slide = new JKesoidySlide(false);
		slide.zaplanujNaplneniSklivce(new Wpt(), new Mou(0, 0));
		pockejNaPrazdnouFrontu(slide);
		slide.zaplanujNaplneniSklivce(new Wpt(), new Mou(0, 0));
		pockejNaPrazdnouFrontu(slide);
	}

	private static void pockejNaPrazdnouFrontu(final JKesoidySlide slide) throws InterruptedException {
		final long konec = System.currentTimeMillis() + 5000;
		while (!slide.frontaWaypointu.isEmpty() && System.currentTimeMillis() < konec) {
			Thread.sleep(10);
		}
		assertTrue("Požadavek ve frontě nikdo nezpracoval", slide.frontaWaypointu.isEmpty());
	}
}
