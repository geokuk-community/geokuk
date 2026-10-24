package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import cz.geokuk.plugins.kesoid.genetika.*;

/** Ikony stavu keše na toolbaru: základní ikona zmenšená na výšku toolbaru a v pravém dolním rohu symbol stavu. */
public final class StavIkona {

	/** Průměr, resp. strana symbolu. */
	static final int SYMBOL = 12;

	private static final Color CERVENA = new Color(0xD8, 0x1E, 0x1E);

	private StavIkona() {}

	/** Ikona alely na toolbaru; stav disablovaná a archivovaná má symbol, ostatní jsou ikony sady. */
	public static Icon ikonaToolbaru(final IkonBag bag, final Alela alela) {
		final Genom genom = bag.getGenom();
		final Genotyp vychozi = genom.UNIVERZALNI_DRUH.genotypVychozi();
		if (alela == genom.ALELA_dsbl) {
			return zakaz(bag.seekIkon(vychozi), JToolbarOvladaceAlel.VYSKA_IKONY);
		}
		if (alela == genom.ALELA_arch) {
			return archiv(bag.seekIkon(vychozi.with(alela)), JToolbarOvladaceAlel.VYSKA_IKONY);
		}
		return bag.seekIkon(vychozi.with(alela));
	}

	/** Původní ikona se symbolem zákazu (červený kruh s šikmou čárou). */
	static Icon zakaz(final Icon zaklad, final int maxVyska) {
		return nakresli(zaklad, maxVyska, true);
	}

	/** Ikona se symbolem archivu (červený čtvereček s křížkem). */
	static Icon archiv(final Icon zaklad, final int maxVyska) {
		return nakresli(zaklad, maxVyska, false);
	}

	private static Icon nakresli(final Icon zaklad, final int maxVyska, final boolean zakaz) {
		final int vyska = Math.min(zaklad.getIconHeight(), maxVyska);
		final int sirka = Math.max(1, zaklad.getIconWidth() * vyska / zaklad.getIconHeight());
		final BufferedImage img = new BufferedImage(sirka, vyska, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			final BufferedImage puvodni = new BufferedImage(zaklad.getIconWidth(), zaklad.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
			final Graphics2D gp = puvodni.createGraphics();
			zaklad.paintIcon(null, gp, 0, 0);
			gp.dispose();
			g.drawImage(puvodni, 0, 0, sirka, vyska, null);
			final int s = Math.min(SYMBOL, Math.min(sirka, vyska));
			final double x = sirka - s;
			final double y = vyska - s;
			if (zakaz) {
				kresliZakaz(g, x, y, s);
			} else {
				kresliArchiv(g, x, y, s);
			}
		} finally {
			g.dispose();
		}
		return new ImageIcon(img);
	}

	private static void kresliZakaz(final Graphics2D g, final double x, final double y, final double s) {
		g.setColor(Color.WHITE);
		g.fill(new Ellipse2D.Double(x, y, s, s));
		g.setColor(CERVENA);
		g.fill(new Ellipse2D.Double(x + 1, y + 1, s - 2, s - 2));
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
		final double o = s * 0.5;
		final double r = (s - 2) * 0.5 - 0.5;
		final double d = r * Math.sqrt(0.5);
		g.draw(new Line2D.Double(x + o - d, y + o + d, x + o + d, y + o - d));
	}

	private static void kresliArchiv(final Graphics2D g, final double x, final double y, final double s) {
		g.setColor(Color.WHITE);
		g.fill(new Rectangle2D.Double(x, y, s, s));
		g.setColor(CERVENA);
		g.fill(new Rectangle2D.Double(x + 1, y + 1, s - 2, s - 2));
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
		final double a = 2.8;
		g.draw(new Line2D.Double(x + a, y + a, x + s - a, y + s - a));
		g.draw(new Line2D.Double(x + a, y + s - a, x + s - a, y + a));
	}
}
