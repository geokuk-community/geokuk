package cz.geokuk.plugins.challenge;

import java.awt.Rectangle;

import org.junit.Assert;
import org.junit.Test;

/** Umisťování štítků kandidátů kolem ikon keší; bez displeje. */
public class RozmisteniStitkuTest {

	private static Rectangle r(final RozmisteniStitku.Umisteni u, final int w, final int h) {
		return new Rectangle(u.x, u.y, w, h);
	}

	@Test
	public void volnyStitekLeziTesneVedleIkonyVPravo() {
		final RozmisteniStitku s = new RozmisteniStitku(800, 600);
		s.pridej(100, 100, 10, 8);
		final RozmisteniStitku.Umisteni u = s.umisti(100, 100, 10, 8, 24, 13);
		Assert.assertNotNull(u);
		Assert.assertEquals(100 + 10 + RozmisteniStitku.MEZERA, u.x);
		Assert.assertFalse(u.spojnice);
	}

	@Test
	public void odstupSeSkalujeSVelikostiIkony() {
		final RozmisteniStitku s = new RozmisteniStitku(800, 600);
		s.pridej(100, 100, 32, 32);
		final RozmisteniStitku.Umisteni u = s.umisti(100, 100, 32, 32, 24, 13);
		Assert.assertEquals("těsně vedle velké ikony, ne uvnitř ní", 100 + 32 + RozmisteniStitku.MEZERA, u.x);
	}

	@Test
	public void stitekNekryjeIkonuSousedniKese() {
		final RozmisteniStitku s = new RozmisteniStitku(800, 600);
		final Rectangle vlastni = new Rectangle(100, 100, 10, 8);
		final Rectangle soused = new Rectangle(113, 99, 10, 8);
		s.pridej(vlastni.x, vlastni.y, vlastni.width, vlastni.height);
		s.pridej(soused.x, soused.y, soused.width, soused.height);
		final RozmisteniStitku.Umisteni u = s.umisti(vlastni.x, vlastni.y, vlastni.width, vlastni.height, 24, 13);
		Assert.assertNotNull(u);
		Assert.assertFalse("nekryje souseda", r(u, 24, 13).intersects(soused));
		Assert.assertFalse("nekryje vlastní ikonu", r(u, 24, 13).intersects(vlastni));
	}

	@Test
	public void kdyzKolemIkonyNeniMistoStitekJdeDalSeSpojnici() {
		final RozmisteniStitku s = new RozmisteniStitku(800, 600);
		s.pridej(100, 100, 10, 8);
		// osm sousedních ikon kolem zahradí všech osm těsných poloh
		for (int dx = -13; dx <= 13; dx += 13) {
			for (int dy = -13; dy <= 13; dy += 13) {
				if (dx != 0 || dy != 0) {
					s.pridej(100 + dx, 100 + dy, 10, 8);
				}
			}
		}
		final RozmisteniStitku.Umisteni u = s.umisti(100, 100, 10, 8, 24, 13);
		Assert.assertNotNull("o kus dál je místo", u);
		Assert.assertTrue(u.spojnice);
	}

	@Test
	public void kdyzNeniMistoNikdeStitekSeVynecha() {
		final RozmisteniStitku s = new RozmisteniStitku(800, 600);
		s.pridej(100, 100, 10, 8);
		s.pridej(0, 0, 800, 90);
		s.pridej(0, 120, 800, 400);
		s.pridej(0, 90, 98, 30);
		s.pridej(112, 90, 688, 30);
		Assert.assertNull(s.umisti(100, 100, 10, 8, 24, 13));
	}

	@Test
	public void dvaStitkyNaSebeNelezi() {
		final RozmisteniStitku s = new RozmisteniStitku(800, 600);
		s.pridej(100, 100, 10, 8);
		s.pridej(112, 104, 10, 8);
		final RozmisteniStitku.Umisteni a = s.umisti(100, 100, 10, 8, 24, 13);
		final RozmisteniStitku.Umisteni b = s.umisti(112, 104, 10, 8, 24, 13);
		Assert.assertNotNull(a);
		Assert.assertNotNull(b);
		Assert.assertFalse(r(a, 24, 13).intersects(r(b, 24, 13)));
	}

	@Test
	public void kontrolaJeUmerosOkoliAnePoctuVsechIkon() {
		final RozmisteniStitku s = new RozmisteniStitku(1920, 1080);
		for (int i = 0; i < 20_000; i++) {
			s.pridej(i % 1900, (i / 1900) * 50 + (i % 7) * 3, 10, 8);
		}
		final long t0 = System.nanoTime();
		int umisteno = 0;
		for (int i = 0; i < 200; i++) {
			final int x = 40 + i * 9;
			final int y = 60 + (i % 10) * 70;
			s.pridej(x, y, 10, 8);
			if (s.umisti(x, y, 10, 8, 24, 13) != null) {
				umisteno++;
			}
		}
		final double ms = (System.nanoTime() - t0) / 1e6;
		Assert.assertTrue("umístěno " + umisteno, umisteno > 0);
		Assert.assertTrue("200 štítků při 20 000 ikonách trvá " + ms + " ms", ms < 400);
	}
}
