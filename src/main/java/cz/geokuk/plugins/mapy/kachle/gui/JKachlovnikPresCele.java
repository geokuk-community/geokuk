package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;

public class JKachlovnikPresCele extends JKachlovnik {

	private static final long serialVersionUID = -3170605712662727739L;
	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.core.coord.JSingleSlide0#render(java.awt.Graphics)
	 */

	public JKachlovnikPresCele() {
		super("Hlavní kachlovník", Priority.KACHLE);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.core.coord.JSingleSlide0#createRenderableSlide()
	 */
	@Override
	public JSingleSlide0 createRenderableSlide() {
		return new JKachlovnikRendrovaci();
	}


	/** Atribuci kreslí přes kachle vpravo dole. */
	@Override
	protected void paintChildren(final Graphics g) {
		super.paintChildren(g);
		if (getKachloType() == null) {
			return;
		}
		final String text = getKachloType().getAtribuce();
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
			final FontMetrics fm = g2.getFontMetrics();
			final int w = fm.stringWidth(text) + 8;
			final int h = fm.getHeight() + 2;
			final int x = getWidth() - w;
			final int y = getHeight() - h;
			g2.setColor(new Color(255, 255, 255, 190));
			g2.fillRect(x, y, w, h);
			g2.setColor(Color.DARK_GRAY);
			g2.drawString(text, x + 4, y + 1 + fm.getAscent());
		} finally {
			g2.dispose();
		}
	}
}
