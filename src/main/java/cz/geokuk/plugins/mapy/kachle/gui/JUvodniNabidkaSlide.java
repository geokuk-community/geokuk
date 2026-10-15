package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.framework.MouseGestureContext;
import cz.geokuk.plugins.mapy.MapyModel;
import cz.geokuk.plugins.mapy.ZmenaMapNastalaEvent;
import cz.geokuk.plugins.mapy.sprava.SpravaMapEvent;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.plugins.mapy.sprava.gui.RadaOfflineMapy;

/**
 * Nová instalace začíná ortofotem. Nabídka nahoře uprostřed mapy radí stáhnout turistickou mapu, která funguje i bez internetu; po stažení se program přepne na
 * offline mapu. Jen na obrazovce, do tisku a exportu nejde. Kreslí se nad ostatními vrstvami a klik na odkaz dostane dřív než ostatní vrstvy.
 */
public class JUvodniNabidkaSlide extends JSingleSlide0 {

	private static final long serialVersionUID = 1L;

	public static final String NADPIS = "Turistická mapa i bez internetu";
	public static final String NYNI_NE = "Nyní ne";
	private static final Color ODKAZ = new Color(0x1A5FB4);

	private transient MapyModel mapyModel;
	private transient SpravaMapModel spravaMap;
	private transient RadaOfflineMapy rada;
	private Rectangle odkazStazeni;
	private Rectangle odkazNyniNe;
	private Point mys;

	public JUvodniNabidkaSlide() {
		setOpaque(false);
		setCursor(null);
	}

	public void inject(final MapyModel mapyModel) {
		this.mapyModel = mapyModel;
	}

	public void inject(final SpravaMapModel spravaMap) {
		this.spravaMap = spravaMap;
	}

	public void onEvent(final SpravaMapEvent event) {
		repaint();
	}

	public void onEvent(final ZmenaMapNastalaEvent event) {
		repaint();
	}

	boolean jeNabidka() {
		return mapyModel != null && spravaMap != null && isSoordInitialized() && mapyModel.isNabidkaPrvnihoStazeni();
	}

	@Override
	protected void paintComponent(final Graphics g0) {
		odkazStazeni = null;
		odkazNyniNe = null;
		rada = null;
		if (!jeNabidka()) {
			return;
		}
		rada = RadaOfflineMapy.pro(spravaMap.getKatalog(), spravaMap::getStav, getSoord().getMoustred().toWgs(),
				spravaMap.getBeziciStahovani().map(StavMapy::getMapa).orElse(null));
		if (rada.mapa == null) {
			rada = null;
			return;
		}
		final Graphics2D g = (Graphics2D) g0.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			final Font tucne = getFont().deriveFont(Font.BOLD, 13f);
			final Font obycejne = getFont().deriveFont(Font.PLAIN, 12f);
			final FontMetrics fmTucne = g.getFontMetrics(tucne);
			final FontMetrics fm = g.getFontMetrics(obycejne);
			final List<String> radky = rada.text == null ? new ArrayList<>() : JKachlovnikPresCele.zalom(rada.text, fm, Math.max(200, getWidth() - 60));
			int w = fmTucne.stringWidth(NADPIS);
			for (final String r : radky) {
				w = Math.max(w, fm.stringWidth(r));
			}
			if (rada.odkaz != null) {
				w = Math.max(w, fm.stringWidth(rada.odkaz));
			}
			w += 24;
			final int h = fmTucne.getHeight() + (radky.size() + (rada.odkaz != null ? 1 : 0) + 1) * fm.getHeight() + 16;
			final int x = (getWidth() - w) / 2;
			final int y = 12;
			g.setColor(new Color(255, 255, 255, 235));
			g.fillRoundRect(x, y, w, h, 10, 10);
			g.setColor(new Color(0x1A, 0x5F, 0xB4, 160));
			g.drawRoundRect(x, y, w, h, 10, 10);
			int yy = y + 8 + fmTucne.getAscent();
			g.setColor(Color.DARK_GRAY);
			g.setFont(tucne);
			g.drawString(NADPIS, x + 12, yy);
			g.setFont(obycejne);
			yy += fmTucne.getDescent() + fm.getAscent() + fmTucne.getLeading() + 2;
			for (final String r : radky) {
				g.drawString(r, x + 12, yy);
				yy += fm.getHeight();
			}
			if (rada.odkaz != null) {
				g.setColor(ODKAZ);
				g.drawString(rada.odkaz, x + 12, yy);
				final int sirka = fm.stringWidth(rada.odkaz);
				g.drawLine(x + 12, yy + 2, x + 12 + sirka, yy + 2);
				odkazStazeni = new Rectangle(x + 8, yy - fm.getAscent(), sirka + 8, fm.getHeight());
				yy += fm.getHeight();
			}
			g.setColor(ODKAZ);
			g.setFont(obycejne.deriveFont(11f));
			final FontMetrics fmMaly = g.getFontMetrics();
			g.drawString(NYNI_NE, x + 12, yy);
			odkazNyniNe = new Rectangle(x + 8, yy - fmMaly.getAscent(), fmMaly.stringWidth(NYNI_NE) + 8, fmMaly.getHeight());
		} finally {
			g.dispose();
		}
	}

	boolean jeNadOdkazem(final Point bod) {
		return bod != null && (odkazStazeni != null && odkazStazeni.contains(bod) || odkazNyniNe != null && odkazNyniNe.contains(bod));
	}

	/** Klik na odkaz stáhne nabízenou mapu, nebo nabídku zruší; vrací, jestli klik patřil odkazu. */
	boolean klik(final Point bod) {
		if (odkazStazeni != null && odkazStazeni.contains(bod) && rada != null && rada.mapa != null && spravaMap != null) {
			spravaMap.stahni(rada.mapa);
			return true;
		}
		if (odkazNyniNe != null && odkazNyniNe.contains(bod) && mapyModel != null) {
			mapyModel.zrusNabidkuPrvnihoStazeni();
			return true;
		}
		return false;
	}

	@Override
	public void mouseClicked(final MouseEvent e, final MouseGestureContext ctx) {
		if (SwingUtilities.isLeftMouseButton(e) && klik(e.getPoint())) {
			return;
		}
		chain().mouseClicked(e, ctx);
	}

	@Override
	public void mousePressed(final MouseEvent e, final MouseGestureContext ctx) {
		if (SwingUtilities.isLeftMouseButton(e) && jeNadOdkazem(e.getPoint())) {
			return;
		}
		chain().mousePressed(e, ctx);
	}

	@Override
	public void mouseReleased(final MouseEvent e, final MouseGestureContext ctx) {
		if (SwingUtilities.isLeftMouseButton(e) && jeNadOdkazem(e.getPoint())) {
			return;
		}
		chain().mouseReleased(e, ctx);
	}

	@Override
	public void mouseMoved(final MouseEvent e, final MouseGestureContext ctx) {
		mys = e.getPoint();
		chain().mouseMoved(e, ctx);
	}

	@Override
	public Cursor getMouseCursor(final boolean pressed) {
		if (jeNadOdkazem(mys)) {
			return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
		}
		return chain().getMouseCursor(pressed);
	}
}
