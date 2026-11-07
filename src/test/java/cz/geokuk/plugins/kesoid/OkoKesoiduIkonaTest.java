package cz.geokuk.plugins.kesoid;

import java.awt.Color;
import java.awt.image.BufferedImage;

import javax.swing.JToggleButton;

import org.junit.Assert;
import org.junit.Test;

/** Ikona „Zobrazovat kešoidy (F5)“: červená přeškrtnutí jen u skrytých kešoidů. */
public class OkoKesoiduIkonaTest {

	private static int cervenych(final boolean zobrazuji) {
		final JToggleButton b = new JToggleButton();
		b.setSelected(zobrazuji);
		b.setForeground(Color.BLACK);
		final OkoKesoiduIkona ikona = new OkoKesoiduIkona();
		final BufferedImage obraz = new BufferedImage(ikona.getIconWidth(), ikona.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g = obraz.createGraphics();
		ikona.paintIcon(b, g, 0, 0);
		g.dispose();
		int n = 0;
		for (int y = 0; y < obraz.getHeight(); y++) {
			for (int x = 0; x < obraz.getWidth(); x++) {
				final int argb = obraz.getRGB(x, y);
				if ((argb >>> 24) > 128 && ((argb >> 16) & 0xff) > 150 && ((argb >> 8) & 0xff) < 90) {
					n++;
				}
			}
		}
		return n;
	}

	private static int nakreslenych(final boolean zobrazuji) {
		final JToggleButton b = new JToggleButton();
		b.setSelected(zobrazuji);
		b.setForeground(Color.BLACK);
		final OkoKesoiduIkona ikona = new OkoKesoiduIkona();
		final BufferedImage obraz = new BufferedImage(ikona.getIconWidth(), ikona.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g = obraz.createGraphics();
		ikona.paintIcon(b, g, 0, 0);
		g.dispose();
		int n = 0;
		for (int y = 0; y < obraz.getHeight(); y++) {
			for (int x = 0; x < obraz.getWidth(); x++) {
				if ((obraz.getRGB(x, y) >>> 24) > 0) {
					n++;
				}
			}
		}
		return n;
	}

	@Test
	public void skrytaOkoMaCervenouCaruZobrazeneNe() {
		Assert.assertEquals("zobrazené oko je bez červené", 0, cervenych(true));
		Assert.assertTrue("skryté oko má červenou čáru: " + cervenych(false), cervenych(false) > 10);
	}

	@Test
	public void ikonaSeKresliVObouStavech() {
		Assert.assertTrue(nakreslenych(true) > 40);
		Assert.assertTrue(nakreslenych(false) > nakreslenych(true));
		Assert.assertEquals(20, new OkoKesoiduIkona().getIconWidth());
	}

	private static BufferedImage nakresli(final boolean zobrazuji, final boolean povoleno, final boolean barevne) {
		final JToggleButton b = new JToggleButton();
		b.setSelected(zobrazuji);
		b.setEnabled(povoleno);
		b.setForeground(Color.BLACK);
		final OkoKesoiduIkona ikona = new OkoKesoiduIkona(barevne);
		final BufferedImage obraz = new BufferedImage(ikona.getIconWidth(), ikona.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g = obraz.createGraphics();
		ikona.paintIcon(b, g, 0, 0);
		g.dispose();
		return obraz;
	}

	private static int pixelu(final BufferedImage obraz) {
		int n = 0;
		for (int y = 0; y < obraz.getHeight(); y++) {
			for (int x = 0; x < obraz.getWidth(); x++) {
				if ((obraz.getRGB(x, y) >>> 24) > 128) {
					n++;
				}
			}
		}
		return n;
	}

	@Test
	public void ikonaMaRozmer20x20AKresliSeVeVsechStavechIVariantach() {
		for (final boolean barevne : new boolean[] { false, true }) {
			for (final boolean zobrazuji : new boolean[] { false, true }) {
				for (final boolean povoleno : new boolean[] { false, true }) {
					final BufferedImage o = nakresli(zobrazuji, povoleno, barevne);
					Assert.assertEquals(20, o.getWidth());
					Assert.assertEquals(20, o.getHeight());
					Assert.assertTrue("kreslí se: " + zobrazuji + povoleno + barevne, pixelu(o) > 60);
				}
			}
		}
	}

	@Test
	public void zakazaneOkoJeSede() {
		final BufferedImage o = nakresli(true, false, false);
		for (int y = 0; y < o.getHeight(); y++) {
			for (int x = 0; x < o.getWidth(); x++) {
				final int argb = o.getRGB(x, y);
				if ((argb >>> 24) > 200) {
					Assert.assertEquals(128, (argb >> 16) & 0xff, 2);
				}
			}
		}
	}

	@Test
	public void cestaZeSvgSeNacteZMoveLineCurveClose() {
		final java.awt.geom.Rectangle2D r = OkoKesoiduIkona.cesta("M0 0L10 0L10 5Z").getBounds2D();
		Assert.assertEquals(10, r.getWidth(), 1e-9);
		Assert.assertEquals(5, r.getHeight(), 1e-9);
	}

	@Test
	public void skryteOkoJeZesvetleneAZobrazeneJeCelistve() {
		Assert.assertTrue("zobrazené oko má plné pixely", maxAlfa(nakresli(true, true, false), 0, 6) > 250);
		Assert.assertTrue("skryté oko je vlevo průsvitné", maxAlfa(nakresli(false, true, false), 0, 6) < 200);
	}

	/** Největší krytí pixelů v levém horním rohu ikony (mimo přeškrtnutí). */
	private static int maxAlfa(final BufferedImage o, final int odX, final int doX) {
		int m = 0;
		for (int y = 0; y < 8; y++) {
			for (int x = odX; x < doX; x++) {
				m = Math.max(m, o.getRGB(x, y) >>> 24);
			}
		}
		return m;
	}

	@Test
	public void barevnaVarianta() {
		final BufferedImage o = nakresli(true, true, true);
		int zelenych = 0;
		for (int y = 0; y < o.getHeight(); y++) {
			for (int x = 0; x < o.getWidth(); x++) {
				final int argb = o.getRGB(x, y);
				if ((argb >>> 24) > 200 && ((argb >> 8) & 0xff) > ((argb) & 0xff) + 80) {
					zelenych++;
				}
			}
		}
		Assert.assertTrue("barevné oko má zelenožluté pixely: " + zelenych, zelenych > 20);
	}
}
