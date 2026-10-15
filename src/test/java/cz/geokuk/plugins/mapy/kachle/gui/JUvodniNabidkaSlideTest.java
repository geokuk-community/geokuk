package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.MapyModel;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;

/** Úvodní nabídka stažení první mapy: kreslí se jen u nové instalace, odkaz stáhne mapu pod středem, „Nyní ne“ nabídku zruší. */
public class JUvodniNabidkaSlideTest {

	private static final class Stav {
		boolean nabidka = true;
		int zruseno;
		final List<String> stazeno = new ArrayList<>();
	}

	private static JUvodniNabidkaSlide slide(final Stav stav, final Wgs stred) {
		final JUvodniNabidkaSlide s = new JUvodniNabidkaSlide();
		s.inject(new MapyModel() {
			@Override
			public boolean isNabidkaPrvnihoStazeni() {
				return stav.nabidka;
			}

			@Override
			public void zrusNabidkuPrvnihoStazeni() {
				stav.zruseno++;
				stav.nabidka = false;
			}
		});
		s.inject(new SpravaMapModel() {
			@Override
			public boolean stahni(final KatalogovaMapa mapa) {
				stav.stazeno.add(mapa.getId());
				return true;
			}
		});
		s.setSize(900, 600);
		s.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12));
		s.setSoord(new Coord(8, stred.toMou(), new Dimension(900, 600), 0));
		return s;
	}

	private static boolean kresli(final JUvodniNabidkaSlide s) {
		final BufferedImage img = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		s.paintComponent(g);
		g.dispose();
		for (int y = 0; y < 300; y++) {
			for (int x = 0; x < 900; x++) {
				if ((img.getRGB(x, y) >>> 24) > 0) {
					return true;
				}
			}
		}
		return false;
	}

	private static Point najdi(final JUvodniNabidkaSlide s, final boolean nyniNe, final Stav stav) {
		for (int y = 0; y < 300; y += 2) {
			for (int x = 0; x < 900; x += 3) {
				final Point p = new Point(x, y);
				if (s.jeNadOdkazem(p)) {
					final int stazeno = stav.stazeno.size();
					final int zruseno = stav.zruseno;
					if (s.klik(p)) {
						final boolean bylNyniNe = stav.zruseno > zruseno;
						if (bylNyniNe == nyniNe) {
							return p;
						}
						Assert.assertEquals(stazeno + 1, stav.stazeno.size());
					}
				}
			}
		}
		return null;
	}

	@Test
	public void nabidkaSeKresliJenUNoveInstalace() {
		final Stav stav = new Stav();
		final JUvodniNabidkaSlide s = slide(stav, new Wgs(50.08, 14.42));
		Assert.assertTrue(kresli(s));
		stav.nabidka = false;
		Assert.assertFalse(kresli(s));
		Assert.assertFalse(s.klik(new Point(450, 40)));
	}

	@Test
	public void odkazStahneMapuPodStredem() {
		final Stav stav = new Stav();
		final JUvodniNabidkaSlide s = slide(stav, new Wgs(50.08, 14.42));
		kresli(s);
		Assert.assertNotNull(najdi(s, false, stav));
		Assert.assertEquals("europe/czech-republic", stav.stazeno.get(0));
	}

	@Test
	public void nyniNeNabidkuZrusi() {
		final Stav stav = new Stav();
		final JUvodniNabidkaSlide s = slide(stav, new Wgs(50.08, 14.42));
		kresli(s);
		Assert.assertNotNull(najdi(s, true, stav));
		Assert.assertEquals(1, stav.zruseno);
		Assert.assertFalse(kresli(s));
	}

	@Test
	public void mimoKatalogNic() {
		Assert.assertFalse(kresli(slide(new Stav(), new Wgs(35.68, 139.69))));
	}

	/** Stisk, uvolnění i klik na odkaz nabídky se spotřebují, takže nepohnou mapou ani nenastaví pozici. */
	@Test
	public void udalostiNadOdkazemSeSpotrebuji() {
		final Stav stav = new Stav();
		final JUvodniNabidkaSlide s = slide(stav, new Wgs(50.08, 14.42));
		kresli(s);
		final Point p = najdi(s, false, new Stav());
		Assert.assertNotNull(p);
		final java.awt.Component zdroj = new javax.swing.JPanel();
		for (final int id : new int[] { java.awt.event.MouseEvent.MOUSE_PRESSED, java.awt.event.MouseEvent.MOUSE_RELEASED, java.awt.event.MouseEvent.MOUSE_CLICKED }) {
			final java.awt.event.MouseEvent e = new java.awt.event.MouseEvent(zdroj, id, 0, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, p.x, p.y, 1, false, java.awt.event.MouseEvent.BUTTON1);
			if (id == java.awt.event.MouseEvent.MOUSE_PRESSED) {
				s.mousePressed(e, null);
			} else if (id == java.awt.event.MouseEvent.MOUSE_RELEASED) {
				s.mouseReleased(e, null);
			} else {
				s.mouseClicked(e, null);
			}
			Assert.assertTrue("událost " + id + " nad odkazem se spotřebuje", e.isConsumed());
		}
	}
}
