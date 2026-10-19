package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.framework.MouseGestureContext;

/**
 * Odkaz v radě kachlovníku („Stáhnout mapu …“) se kreslí pod ostatními vrstvami, klik na něj ale musí dostat dřív než ostatní vrstvy (jinak by klik nastavil
 * pozici). Tato vrstva je v řetězu myši na začátku a jako nejvyšší vrstva mapy kreslí atribuci kachlovníku, aby ji nic nezakrylo.
 */
public class JOdkazRadySlide extends JSingleSlide0 {

	private static final long serialVersionUID = 1L;

	private final JKachlovnikPresCele kachlovnik;
	private Point mys;

	public JOdkazRadySlide(final JKachlovnikPresCele kachlovnik) {
		this.kachlovnik = kachlovnik;
		setOpaque(false);
		setCursor(null);
	}

	@Override
	protected void paintComponent(final Graphics g) {
		final Point p = SwingUtilities.convertPoint(kachlovnik, 0, 0, this);
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.translate(p.x, p.y);
			kachlovnik.kresliAtribuci(g2);
		} finally {
			g2.dispose();
		}
	}

	@Override
	public void mouseClicked(final MouseEvent e, final MouseGestureContext ctx) {
		if (SwingUtilities.isLeftMouseButton(e) && kachlovnik.klikNaRadu(bodVKachlovniku(e))) {
			return;
		}
		chain().mouseClicked(e, ctx);
	}

	@Override
	public void mousePressed(final MouseEvent e, final MouseGestureContext ctx) {
		if (SwingUtilities.isLeftMouseButton(e) && kachlovnik.jeNadOdkazemRady(bodVKachlovniku(e))) {
			return;
		}
		chain().mousePressed(e, ctx);
	}

	@Override
	public void mouseReleased(final MouseEvent e, final MouseGestureContext ctx) {
		if (SwingUtilities.isLeftMouseButton(e) && kachlovnik.jeNadOdkazemRady(bodVKachlovniku(e))) {
			return;
		}
		chain().mouseReleased(e, ctx);
	}

	@Override
	public void mouseMoved(final MouseEvent e, final MouseGestureContext ctx) {
		mys = bodVKachlovniku(e);
		chain().mouseMoved(e, ctx);
	}

	@Override
	public Cursor getMouseCursor(final boolean pressed) {
		if (mys != null && kachlovnik.jeNadOdkazemRady(mys)) {
			return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
		}
		return chain().getMouseCursor(pressed);
	}

	private Point bodVKachlovniku(final MouseEvent e) {
		return SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), kachlovnik);
	}
}
