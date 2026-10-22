package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.Color;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;

import javax.swing.JPanel;

/** Řádky popupu Mapy v mřížce: každý druhý viditelný řádek je podbarvený a sloupce vrstev odděluje svislá čárka, aby to bylo čitelné jako tabulka. */
final class TabulkaRadku extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final Color PODBARVENI = new Color(0, 0, 0, 14);
	private static final Color CARA = new Color(0, 0, 0, 40);

	/** Od kterého sloupce mřížky začínají buňky vrstev; před ním je svislá čárka. */
	private final int prvniSloupecVrstev;

	/** Tabulka v posuvníku: posuvník si šířku bere sám, rezerva vpravo odpadá. */
	void bezRezervyVpravo() {
		setBorder(javax.swing.BorderFactory.createEmptyBorder());
	}

	/**
	 * @param rezervaVpravo
	 *            šířka, o kterou je panel užší než seznam v posuvníku, aby sloupce lícovaly s posuvným seznamem
	 */
	TabulkaRadku(final int prvniSloupecVrstev, final int rezervaVpravo) {
		super(new GridBagLayout());
		this.prvniSloupecVrstev = prvniSloupecVrstev;
		setOpaque(false);
		setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, rezervaVpravo));
	}

	@Override
	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		final GridBagLayout lm = (GridBagLayout) getLayout();
		final int[][] rozmery = lm.getLayoutDimensions();
		if (rozmery[0].length == 0) {
			return;
		}
		final Point pocatek = lm.getLayoutOrigin();
		final Insets okraje = getInsets();
		final int sirka = getWidth() - okraje.right;
		int y = pocatek.y;
		int viditelny = 0;
		for (final int vyska : rozmery[1]) {
			if (vyska > 0) {
				if ((viditelny & 1) != 0) {
					g.setColor(PODBARVENI);
					g.fillRect(0, y, sirka, vyska);
				}
				viditelny++;
			}
			y += vyska;
		}
		g.setColor(CARA);
		int x = pocatek.x;
		for (int sloupec = 0; sloupec < rozmery[0].length; sloupec++) {
			if (sloupec >= prvniSloupecVrstev) {
				g.drawLine(x, pocatek.y, x, y);
			}
			x += rozmery[0][sloupec];
		}
	}
}
