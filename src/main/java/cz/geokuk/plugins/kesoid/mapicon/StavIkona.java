package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import cz.geokuk.plugins.kesoid.genetika.*;

/** Ikony alel na toolbaru: disablovaná je šedá ikona keše pod červeným zákazem, archivovaná vyblednutá ikona keše s červenou značkou v rohu, nevyluštěná dílek skládačky; ostatní ikony se srovnají na stejnou velikost. */
public final class StavIkona {

	/** Strana značky archivu v rohu. */
	static final int SYMBOL = 11;

	private static final Color CERVENA = new Color(0xD8, 0x1E, 0x1E);
	private static final Color ZAKAZ = new Color(0xD8, 0x1E, 0x1E, 200);
	private static final Color OBRYS = new Color(0x40, 0x40, 0x40);
	private static final float PRUHLEDNOST_DUCHA = 0.5f;
	/** Podíl strany ikony, který zabere keš uvnitř zákazu. */
	private static final double VYPLN_ZAKAZU = 0.58;
	/** Podíl strany ikony, který zabere obsah ikon Nehledané, Nalezené a Moje vlastní. */
	private static final double VYPLN_VZTAHU = 0.85;

	private StavIkona() {}

	/** Ikona alely na toolbaru; stav disablovaná a archivovaná a vyluštěnost mají vlastní podobu, ostatní jsou ikony sady, mimo druhy waypointů na stejnou velikost. */
	public static Icon ikonaToolbaru(final IkonBag bag, final Alela alela) {
		return ikona(bag, alela, JToolbarOvladaceAlel.VYSKA_IKONY, false);
	}

	/** Táž ikona jako na toolbaru, ale ve čtverci o dané straně (dialogy a nabídky); i ikony druhů waypointů se srovnají na tuto velikost. */
	public static Icon ikonaAlely(final IkonBag bag, final Alela alela, final int velikost) {
		return ikona(bag, alela, velikost, true);
	}

	private static Icon ikona(final IkonBag bag, final Alela alela, final int velikost, final boolean iDruhy) {
		final Genom genom = bag.getGenom();
		final Genotyp vychozi = genom.UNIVERZALNI_DRUH.genotypVychozi();
		if (alela == genom.ALELA_dsbl) {
			return zakaz(bag.seekIkon(vychozi), velikost);
		}
		if (alela == genom.ALELA_arch) {
			return archiv(bag.seekIkon(vychozi), velikost);
		}
		if (alela == genom.ALELA_nevyluste) {
			return nevyluste(velikost);
		}
		final Icon ikona = bag.seekIkon(vychozi.with(alela));
		if (alela.getGen() == genom.symGen) {
			return iDruhy ? naCtverec(ikona, velikost) : ikona;
		}
		final boolean vztah = alela == genom.ALELA_hnf || alela == genom.ALELA_fnd || alela == genom.ALELA_own;
		return naCtverec(ikona, velikost, vztah ? VYPLN_VZTAHU : 1.0);
	}

	/** Ikona vycentrovaná do čtverce o dané straně, menší se zvětší, větší zmenší; poměr stran zůstává. */
	static Icon naCtverec(final Icon ikona, final int velikost) {
		return naCtverec(ikona, velikost, 1.0);
	}

	/** Jako {@link #naCtverec(Icon, int)}, obsah ale zabere jen {@code vypln} strany čtverce. */
	static Icon naCtverec(final Icon ikona, final int velikost, final double vypln) {
		if (ikona == null || vypln == 1.0 && ikona.getIconWidth() == velikost && ikona.getIconHeight() == velikost) {
			return ikona;
		}
		final BufferedImage puvodni = new BufferedImage(ikona.getIconWidth(), ikona.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		final Graphics2D gp = puvodni.createGraphics();
		ikona.paintIcon(null, gp, 0, 0);
		gp.dispose();
		final double meritko = vypln * velikost / Math.max(puvodni.getWidth(), puvodni.getHeight());
		return new CtvercovaIkona(velikost) {
			@Override
			void kresli(final Graphics2D g) {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				g.translate((velikost - puvodni.getWidth() * meritko) / 2, (velikost - puvodni.getHeight() * meritko) / 2);
				g.scale(meritko, meritko);
				g.drawImage(puvodni, 0, 0, null);
			}
		};
	}

	/** Dílek skládačky. */
	static Icon nevyluste(final int velikost) {
		return new CtvercovaIkona(velikost) {
			@Override
			void kresli(final Graphics2D g) {
				g.scale(velikost / 24.0, velikost / 24.0);
				kresliDilek(g);
			}
		};
	}

	/** Čtvercová ikona, která se kreslí přímo do cílové grafiky, takže na displejích se zvětšením zůstává ostrá. */
	private abstract static class CtvercovaIkona implements Icon {
		private final int velikost;

		CtvercovaIkona(final int velikost) {
			this.velikost = velikost;
		}

		/** Kreslí do čtverce o straně {@code velikost} s počátkem v bodě 0, 0. */
		abstract void kresli(Graphics2D g);

		@Override
		public int getIconWidth() {
			return velikost;
		}

		@Override
		public int getIconHeight() {
			return velikost;
		}

		@Override
		public void paintIcon(final Component c, final Graphics g0, final int x, final int y) {
			final Graphics2D g = (Graphics2D) g0.create();
			try {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
				g.translate(x, y);
				kresli(g);
			} finally {
				g.dispose();
			}
		}
	}

	private static void kresliDilek(final Graphics2D g) {
		final Area dilek = new Area(new RoundRectangle2D.Double(4.5, 7.5, 14, 13.5, 2, 2));
		dilek.add(new Area(new Ellipse2D.Double(7.7, 3.4, 5.6, 5.6)));
		dilek.add(new Area(new Ellipse2D.Double(17.2, 11, 5.6, 5.6)));
		dilek.subtract(new Area(new Ellipse2D.Double(2.2, 11.6, 5.4, 5.4)));
		g.setColor(new Color(0x8A, 0x5C, 0xC8));
		g.fill(dilek);
		g.setColor(OBRYS);
		g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(dilek);
	}

	/** Šedá ikona keše uvnitř červeného kruhu s šikmou čárou přes celou plochu ikony. */
	static Icon zakaz(final Icon zaklad, final int velikost) {
		final BufferedImage img = new BufferedImage(velikost, velikost, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = kresleni(img);
		try {
			final BufferedImage puvodni = new BufferedImage(zaklad.getIconWidth(), zaklad.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
			final Graphics2D gp = puvodni.createGraphics();
			zaklad.paintIcon(null, gp, 0, 0);
			gp.dispose();
			final double meritko = Math.min(1.0, VYPLN_ZAKAZU * velikost / Math.max(puvodni.getWidth(), puvodni.getHeight()));
			final int sirka = Math.max(1, (int) Math.round(puvodni.getWidth() * meritko));
			final int vyska = Math.max(1, (int) Math.round(puvodni.getHeight() * meritko));
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.drawImage(sedy(puvodni), (velikost - sirka) / 2, (velikost - vyska) / 2, sirka, vyska, null);
			g.setColor(ZAKAZ);
			g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
			final double m = 2.5;
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
			final double znacka = Math.round(SYMBOL * Math.min(1.0, velikost / 24.0));
			kresliZnacku(g, velikost - znacka, velikost - znacka, znacka);
		} finally {
			g.dispose();
		}
		return new ImageIcon(img);
	}

	private static BufferedImage sedy(final BufferedImage puvodni) {
		final BufferedImage vysledek = new BufferedImage(puvodni.getWidth(), puvodni.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < puvodni.getHeight(); y++) {
			for (int x = 0; x < puvodni.getWidth(); x++) {
				final int p = puvodni.getRGB(x, y);
				final int sedost = (int) (0.3 * ((p >> 16) & 0xFF) + 0.59 * ((p >> 8) & 0xFF) + 0.11 * (p & 0xFF));
				vysledek.setRGB(x, y, (p & 0xFF000000) | (sedost << 16) | (sedost << 8) | sedost);
			}
		}
		return vysledek;
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
