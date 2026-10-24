package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import cz.geokuk.plugins.kesoid.genetika.*;

/** Ikony stavu keše na toolbaru: disablovaná je velký červený zákaz, archivovaná vyblednutá ikona keše s červenou značkou v rohu. */
public final class StavIkona {

	/** Strana značky archivu v rohu. */
	static final int SYMBOL = 11;

	private static final Color CERVENA = new Color(0xD8, 0x1E, 0x1E);
	private static final Color OBRYS = new Color(0x40, 0x40, 0x40);
	private static final float PRUHLEDNOST_DUCHA = 0.5f;

	private StavIkona() {}

	/** Ikona alely na toolbaru; stav disablovaná a archivovaná má vlastní podobu, ostatní jsou ikony sady. */
	public static Icon ikonaToolbaru(final IkonBag bag, final Alela alela) {
		final Genom genom = bag.getGenom();
		final Genotyp vychozi = genom.UNIVERZALNI_DRUH.genotypVychozi();
		if (alela == genom.ALELA_dsbl) {
			return zakaz(JToolbarOvladaceAlel.VYSKA_IKONY);
		}
		if (alela == genom.ALELA_arch) {
			return archiv(bag.seekIkon(vychozi), JToolbarOvladaceAlel.VYSKA_IKONY);
		}
		return bag.seekIkon(vychozi.with(alela));
	}

	/** Červený kruh s šikmou čárou přes celou plochu ikony. */
	static Icon zakaz(final int velikost) {
		final BufferedImage img = new BufferedImage(velikost, velikost, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = kresleni(img);
		try {
			g.setColor(CERVENA);
			g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
			final double m = 2.25;
			g.draw(new Ellipse2D.Double(m, m, velikost - 2 * m, velikost - 2 * m));
			final double stred = velikost / 2.0;
			final double d = (velikost - 2 * m) / 2 * Math.sqrt(0.5);
			g.draw(new Line2D.Double(stred - d, stred + d, stred + d, stred - d));
		} finally {
			g.dispose();
		}
		return new ImageIcon(img);
	}

	/** Odbarvená a průhledná ikona v čárkovaném rámečku, v pravém dolním rohu červený čtvereček s křížkem. */
	static Icon archiv(final Icon zaklad, final int velikost) {
		final BufferedImage img = new BufferedImage(velikost, velikost, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = kresleni(img);
		try {
			final BufferedImage puvodni = new BufferedImage(zaklad.getIconWidth(), zaklad.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
			final Graphics2D gp = puvodni.createGraphics();
			zaklad.paintIcon(null, gp, 0, 0);
			gp.dispose();
			final double meritko = Math.min(1.0, (double) velikost / Math.max(puvodni.getWidth(), puvodni.getHeight()));
			final int sirka = Math.max(1, (int) Math.round(puvodni.getWidth() * meritko));
			final int vyska = Math.max(1, (int) Math.round(puvodni.getHeight() * meritko));
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.drawImage(duch(puvodni), (velikost - sirka) / 2, (velikost - vyska) / 2, sirka, vyska, null);
			g.setColor(new Color(0x70, 0x70, 0x70));
			g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 2f, 2f }, 0f));
			g.draw(new RoundRectangle2D.Double(1.5, 1.5, velikost - 3, velikost - 3, 5, 5));
			kresliZnacku(g, velikost - SYMBOL, velikost - SYMBOL, SYMBOL);
		} finally {
			g.dispose();
		}
		return new ImageIcon(img);
	}

	private static BufferedImage duch(final BufferedImage puvodni) {
		final BufferedImage vysledek = new BufferedImage(puvodni.getWidth(), puvodni.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < puvodni.getHeight(); y++) {
			for (int x = 0; x < puvodni.getWidth(); x++) {
				final int p = puvodni.getRGB(x, y);
				final int alfa = (int) ((p >>> 24) * PRUHLEDNOST_DUCHA);
				final int sedost = (int) (0.3 * ((p >> 16) & 0xFF) + 0.59 * ((p >> 8) & 0xFF) + 0.11 * (p & 0xFF));
				vysledek.setRGB(x, y, (alfa << 24) | (sedost << 16) | (sedost << 8) | sedost);
			}
		}
		return vysledek;
	}

	private static void kresliZnacku(final Graphics2D g, final double x, final double y, final double s) {
		g.setColor(OBRYS);
		g.fill(new Rectangle2D.Double(x - 1, y - 1, s + 2, s + 2));
		g.setColor(Color.WHITE);
		g.fill(new Rectangle2D.Double(x, y, s, s));
		g.setColor(CERVENA);
		g.fill(new Rectangle2D.Double(x + 1.2, y + 1.2, s - 2.4, s - 2.4));
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
		final double a = s * 0.27;
		g.draw(new Line2D.Double(x + a, y + a, x + s - a, y + s - a));
		g.draw(new Line2D.Double(x + a, y + s - a, x + s - a, y + a));
	}

	private static Graphics2D kresleni(final BufferedImage img) {
		final Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		return g;
	}
}
