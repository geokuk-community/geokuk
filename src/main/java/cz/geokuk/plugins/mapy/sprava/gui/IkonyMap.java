package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

import javax.swing.Icon;

/** Ikony stavu offline map: jedna barva = jeden stav. */
final class IkonyMap {

	static final int VELIKOST = 14;

	static final Color ZELENA = new Color(0x2E9E4F);
	static final Color ZLUTA = new Color(0xE0A000);
	static final Color CERVENA = new Color(0xC62828);
	static final Color MODRA = new Color(0x1E6FD0);
	static final Color SEDA = new Color(0x9A9A9A);

	/** Doba jedné otáčky ikony stahování. */
	static final int OTACKA_MS = 1200;

	private interface Kresleni {
		void kresli(Graphics2D g);
	}

	private static final Icon AKTUALNI = ikona(g -> {
		g.setColor(ZELENA);
		g.fill(new Ellipse2D.Double(1, 1, 12, 12));
		g.setColor(Color.WHITE);
		g.drawPolyline(new int[] { 4, 6, 10 }, new int[] { 7, 9, 5 }, 3);
	});

	private static final Icon NOVEJSI = ikona(g -> {
		g.setColor(ZLUTA);
		g.fill(new Ellipse2D.Double(1, 1, 12, 12));
		g.setColor(Color.WHITE);
		g.drawLine(7, 4, 7, 10);
		g.drawPolyline(new int[] { 4, 7, 10 }, new int[] { 7, 4, 7 }, 3);
	});

	private static final Icon CHYBA = ikona(g -> {
		g.setColor(CERVENA);
		g.fill(new Ellipse2D.Double(1, 1, 12, 12));
		g.setColor(Color.WHITE);
		g.drawLine(7, 4, 7, 8);
		g.drawLine(7, 10, 7, 10);
	});

	private static final Icon STAHUJE = ikona(g -> {
		g.setColor(MODRA);
		final double natoceni = System.currentTimeMillis() % OTACKA_MS * 360.0 / OTACKA_MS;
		g.draw(new Arc2D.Double(2, 2, 10, 10, 60 - natoceni, 270, Arc2D.OPEN));
	});

	private static final Icon NEMAME = ikona(g -> {
		g.setColor(SEDA);
		g.draw(new Ellipse2D.Double(1.5, 1.5, 11, 11));
	});

	private IkonyMap() {}

	static Icon pro(final RadekMapy.Stav stav) {
		switch (stav) {
		case AKTUALNI:
			return AKTUALNI;
		case NOVEJSI:
			return NOVEJSI;
		case CHYBA:
			return CHYBA;
		case STAHUJE:
			return STAHUJE;
		case NEMAME:
		default:
			return NEMAME;
		}
	}

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
}
