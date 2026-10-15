package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.framework.MouseGestureContext;

/**
 * Odkaz v radě kachlovníku („Stáhnout mapu …“) se kreslí pod ostatními vrstvami, klik na něj ale musí dostat dřív než ostatní vrstvy (jinak by klik nastavil
 * pozici). Tato vrstva nic nekreslí, je v řetězu myši na začátku.
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
