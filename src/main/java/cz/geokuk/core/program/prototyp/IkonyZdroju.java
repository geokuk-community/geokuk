package cz.geokuk.core.program.prototyp;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

import javax.swing.Icon;

import cz.geokuk.core.program.prototyp.ZdrojeModel.Stav;

/** Malé ikony stavů zdrojů kreslené v kódu, aby nezávisely na rastrových souborech. */
final class IkonyZdroju {

	static final int VELIKOST = 14;

	private static final Color ZELENA = new Color(0x2E9E4F);
	private static final Color SEDA = new Color(0x8A8A8A);
	private static final Color ORANZOVA = new Color(0xD06000);
	private static final Color CERVENA = new Color(0xC62828);

	private IkonyZdroju() {
	}

	private interface Kresleni {
		void kresli(Graphics2D g);
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

	static Icon zapnuto() {
		return ikona(g -> {
			g.setColor(ZELENA);
			g.fill(new Ellipse2D.Double(1, 1, 12, 12));
			g.setColor(Color.WHITE);
			g.drawPolyline(new int[] { 4, 6, 10 }, new int[] { 7, 9, 5 }, 3);
		});
	}

	static Icon vypnuto() {
		return ikona(g -> {
			g.setColor(SEDA);
			g.draw(new Ellipse2D.Double(1.5, 1.5, 11, 11));
			g.drawLine(3, 11, 11, 3);
		});
	}

	static Icon zamek() {
		return ikona(g -> {
			g.setColor(ORANZOVA);
			g.draw(new Arc2D.Double(4, 1, 6, 8, 0, 180, Arc2D.OPEN));
			g.fillRoundRect(2, 6, 10, 7, 2, 2);
		});
	}

	static Icon nacitaSe() {
		return ikona(g -> {
			g.setColor(ZELENA);
			g.draw(new Arc2D.Double(2, 2, 10, 10, 60, 270, Arc2D.OPEN));
		});
	}

	static Icon chyba() {
		return ikona(g -> {
			g.setColor(CERVENA);
			g.fill(new Ellipse2D.Double(1, 1, 12, 12));
			g.setColor(Color.WHITE);
			g.drawLine(7, 4, 7, 8);
			g.drawLine(7, 10, 7, 10);
		});
	}

	static Icon pro(final Stav stav) {
		switch (stav) {
		case NACTENO:
			return zapnuto();
		case NACITA_SE:
		case CEKA_NA_ZAPIS:
			return nacitaSe();
		case ZAMCENO:
			return zamek();
		case CHYBA:
			return chyba();
		default:
			return vypnuto();
		}
	}
}
