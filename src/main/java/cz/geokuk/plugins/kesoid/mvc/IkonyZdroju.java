package cz.geokuk.plugins.kesoid.mvc;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

import javax.swing.Icon;

import cz.geokuk.plugins.kesoid.importek.StavZdroje;
import cz.geokuk.plugins.kesoid.importek.StavZdroju.StavVyberu;

/** Malé ikony stavů zdrojů kreslené v kódu. */
final class IkonyZdroju {

	static final int VELIKOST = 14;

	private static final Color ZELENA = new Color(0x2E9E4F);
	private static final Color ORANZOVA = new Color(0xD06000);
	private static final Color CERVENA = new Color(0xC62828);

	private interface Kresleni {
		void kresli(Graphics2D g);
	}

	private static final Icon PRAZDNA = ikona(g -> {});

	private static final Icon NACTENO = ikona(g -> {
		g.setColor(ZELENA);
		g.fill(new Ellipse2D.Double(1, 1, 12, 12));
		g.setColor(Color.WHITE);
		g.drawPolyline(new int[] { 4, 6, 10 }, new int[] { 7, 9, 5 }, 3);
	});

	private static final Icon NACITA_SE = ikona(g -> {
		g.setColor(ZELENA);
		g.draw(new Arc2D.Double(2, 2, 10, 10, 60, 270, Arc2D.OPEN));
	});

	private static final Icon ZAMEK = ikona(g -> {
		g.setColor(ORANZOVA);
		g.draw(new Arc2D.Double(4, 1, 6, 8, 0, 180, Arc2D.OPEN));
		g.fillRoundRect(2, 6, 10, 7, 2, 2);
	});

	private static final Icon CHYBA = ikona(g -> {
		g.setColor(CERVENA);
		g.fill(new Ellipse2D.Double(1, 1, 12, 12));
		g.setColor(Color.WHITE);
		g.drawLine(7, 4, 7, 8);
		g.drawLine(7, 10, 7, 10);
	});

	private IkonyZdroju() {}

	private static Icon ikona(final Kresleni kresleni) {
		return new Icon() {
			@Override
			public void paintIcon(final Component c, final Graphics g0, final int x, final int y) {
				final Graphics2D g = (Graphics2D) g0.create(x, y, VELIKOST, VELIKOST);
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				kresleni.kresli(g);
				g.dispose();
			}

			@Override
			public int getIconWidth() {
				return VELIKOST;
			}

			@Override
			public int getIconHeight() {
				return VELIKOST;
			}
		};
	}

	/** Vypnutý zdroj má prázdnou ikonu, místo zůstává. */
	static Icon prazdna() {
		return PRAZDNA;
	}

	/** Ikona stavu; položka čekající na řadu ještě žádnou nemá. */
	static Icon pro(final StavZdroje stav) {
		switch (stav) {
		case NACTENO:
			return NACTENO;
		case NACITA_SE:
			return NACITA_SE;
		case CEKA_NA_ZAPIS:
			return ZAMEK;
		case CHYBA:
			return CHYBA;
		default:
			return PRAZDNA;
		}
	}

	/** Zaškrtávátko typu se třemi stavy; „částečně“ je plný čtverec. */
	static Icon zaskrtavatko(final StavVyberu volba) {
		return ZASKRTAVATKA[volba.ordinal()];
	}

	/** Zaškrtávátko položky vypnutého typu: volba zůstává vidět, ale šedě. */
	static Icon zaskrtavatkoSede(final StavVyberu volba) {
		return SEDA_ZASKRTAVATKA[volba.ordinal()];
	}

	private static final Icon[] ZASKRTAVATKA = zaskrtavatka(new Color(0x5A5A5A), new Color(0x2B4F7E));
	private static final Icon[] SEDA_ZASKRTAVATKA = zaskrtavatka(new Color(0xB0B0B0), new Color(0xA0A0A0));

	private static Icon[] zaskrtavatka(final Color okraj, final Color znacka) {
		final Icon[] ikony = new Icon[StavVyberu.values().length];
		for (final StavVyberu volba : StavVyberu.values()) {
			ikony[volba.ordinal()] = ikona(g -> {
				g.setStroke(new BasicStroke(1.2f));
				g.setColor(Color.WHITE);
				g.fillRoundRect(1, 1, 12, 12, 3, 3);
				g.setColor(okraj);
				g.drawRoundRect(1, 1, 12, 12, 3, 3);
				g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.setColor(znacka);
				if (volba == StavVyberu.ZAPNUTO) {
					g.drawPolyline(new int[] { 4, 6, 10 }, new int[] { 7, 9, 4 }, 3);
				} else if (volba == StavVyberu.CASTECNE) {
					g.fillRect(4, 4, 6, 6);
				}
			});
		}
		return ikony;
	}
}
