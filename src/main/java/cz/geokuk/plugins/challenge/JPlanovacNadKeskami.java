package cz.geokuk.plugins.challenge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coord.VyrezModel;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.MouseGestureContext;

/**
 * Vrstva nad kešemi: značka a štítek středu, u okraje mapy šipka ke středu mimo výřez (klik ho vystředí), kroužky a štítky kandidátů (číslo sektoru, D/T) a štítek vybraného sektoru.
 * Štítky se umisťují hladově bez překryvu, nevešlé se vynechají a kroužky zůstanou.
 */
public class JPlanovacNadKeskami extends JVrstvaPlanovace {

	private static final long serialVersionUID = 1L;

	private static final int OKRAJ_SIPKY = 24;
	private static final int POLOMER_KROUZKU = 7;
	private static final int OKRAJ_VYREZU = 12;

	private transient VyrezModel vyrezModel;
	/** Kde se naposledy kreslila šipka ke středu (pro klik), nebo null. */
	private transient volatile Rectangle sipka;
	private transient volatile TvarPlanovace sipkaTvar;

	// pracovní pole kreslení, jen z EDT
	private final transient int[] ox = new int[TvarPlanovace.NEJVIC_ZNACEK + 8];
	private final transient int[] oy = new int[TvarPlanovace.NEJVIC_ZNACEK + 8];
	private final transient int[] ow = new int[TvarPlanovace.NEJVIC_ZNACEK + 8];
	private final transient int[] oh = new int[TvarPlanovace.NEJVIC_ZNACEK + 8];
	private transient int pocetObdelniku;
	private transient int posledniZnacek;
	private transient int[] viditelne = new int[256];
	private transient int[] px = new int[256];
	private transient int[] py = new int[256];
	private transient double[] vzdal = new double[256];

	/** Šipka ke středu mimo výřez z posledního kreslení, nebo null. */
	Rectangle sipka() {
		return sipka;
	}

	/** Kolik kroužků kandidátů se při posledním kreslení nakreslilo. */
	int posledniZnacek() {
		return posledniZnacek;
	}

	/** Obdélníky štítků z posledního kreslení. */
	java.util.List<Rectangle> obdelnikyStitku() {
		final java.util.List<Rectangle> l = new java.util.ArrayList<>();
		for (int i = 0; i < pocetObdelniku; i++) {
			l.add(new Rectangle(ox[i], oy[i], ow[i], oh[i]));
		}
		return l;
	}

	public void inject(final VyrezModel vyrezModel) {
		this.vyrezModel = vyrezModel;
	}

	@Override
	public void paintComponent(final Graphics aG) {
		sipka = null;
		posledniZnacek = 0;
		pocetObdelniku = 0;
		final TvarPlanovace t = tvar();
		if (t == null || !isSoordInitialized()) {
			return;
		}
		final Coord c = getSoord();
		final Graphics2D g = (Graphics2D) aG.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			final Font zaklad = UIManager.getFont("ToolTip.font") != null ? UIManager.getFont("ToolTip.font") : getFont();
			final Font pismo = zaklad.deriveFont(Font.PLAIN, zaklad.getSize2D() - 1f);
			final Font tucne = pismo.deriveFont(Font.BOLD);
			pocetObdelniku = 0;
			kresliStred(g, c, t, tucne);
			if (kresliSektory() && t.vybranyPozice != null && t.popisVybraneho != null && t.stred != null) {
				final Point s = c.transform(t.stred);
				final Point k = c.transform(t.vybranyPozice);
				final Dimension d = rozmerStitku(g, tucne, t.popisVybraneho);
				umisti(g, tucne, t.popisVybraneho, (s.x + k.x) / 2 + 8, (s.y + k.y) / 2 - d.height / 2, true);
			}
			if (kresliStitky()) {
				kresliKandidaty(g, c, t, pismo);
			}
		} finally {
			g.dispose();
		}
	}

	private void kresliStred(final Graphics2D g, final Coord c, final TvarPlanovace t, final Font tucne) {
		if (t.stred == null) {
			return;
		}
		final Point s = c.transform(t.stred);
		final int w = c.getWidth();
		final int h = c.getHeight();
		final Color akcent = AkcentPlanovace.barva();
		if (s.x >= 0 && s.y >= 0 && s.x < w && s.y < h) {
			final int r = 9;
			g.setColor(Color.WHITE);
			g.fill(new Ellipse2D.Double(s.x - r - 2, s.y - r - 2, 2 * r + 4, 2 * r + 4));
			g.setColor(akcent);
			g.fill(new Ellipse2D.Double(s.x - r, s.y - r, 2 * r, 2 * r));
			g.setColor(Color.WHITE);
			g.setFont(tucne);
			final FontMetrics fm = g.getFontMetrics();
			g.drawString("S", s.x - fm.stringWidth("S") / 2, s.y + (fm.getAscent() - fm.getDescent()) / 2);
			umisti(g, tucne, TextyKresleni.stred(t.popisStredu), s.x + r + 5, s.y - r - 4 - fm.getHeight(), true);
			return;
		}
		kresliSipku(g, c, t, tucne, s, akcent);
	}

	/** Šipka u okraje okna ve směru středu a štítek se vzdáleností; klik na ni vystředí mapu. */
	private void kresliSipku(final Graphics2D g, final Coord c, final TvarPlanovace t, final Font tucne, final Point s, final Color akcent) {
		final int w = c.getWidth();
		final int h = c.getHeight();
		double dx = s.x - w / 2.0;
		double dy = s.y - h / 2.0;
		final double d = Math.hypot(dx, dy);
		if (d < 1) {
			return;
		}
		dx /= d;
		dy /= d;
		final double tx = dx == 0 ? Double.MAX_VALUE : Math.abs((w / 2.0 - OKRAJ_SIPKY) / dx);
		final double ty = dy == 0 ? Double.MAX_VALUE : Math.abs((h / 2.0 - OKRAJ_SIPKY) / dy);
		final double k = Math.min(tx, ty);
		final double x = w / 2.0 + dx * k;
		final double y = h / 2.0 + dy * k;
		final double ang = Math.atan2(dy, dx);
		final double l = 18;
		final Path2D.Double p = new Path2D.Double();
		p.moveTo(x + l * Math.cos(ang), y + l * Math.sin(ang));
		p.lineTo(x + l * 0.7 * Math.cos(ang + 2.5), y + l * 0.7 * Math.sin(ang + 2.5));
		p.lineTo(x + l * 0.7 * Math.cos(ang - 2.5), y + l * 0.7 * Math.sin(ang - 2.5));
		p.closePath();
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(3f));
		g.draw(p);
		g.setColor(akcent);
		g.fill(p);
		final Wgs stredMapy = c.getMoustred().toWgs();
		final String text = TextyKresleni.stredMimoVyrez(t.popisStredu, Wgs.vzdalenost(stredMapy, t.stredWgs));
		final Dimension rozmer = rozmerStitku(g, tucne, text);
		final double lx = Math.max(6, Math.min(w - rozmer.width - 6, x - dx * (l + rozmer.width / 2.0 + 4) - rozmer.width / 2.0));
		final double ly = Math.max(6, Math.min(h - rozmer.height - 6, y - dy * (l + rozmer.height / 2.0 + 4) - rozmer.height / 2.0));
		final Rectangle r = nakresliStitek(g, tucne, text, (int) lx, (int) ly, akcent);
		sipka = r.union(p.getBounds());
		sipkaTvar = t;
	}

	private void kresliKandidaty(final Graphics2D g, final Coord c, final TvarPlanovace t, final Font pismo) {
		final int w = c.getWidth();
		final int h = c.getHeight();
		int n = 0;
		final Point p = new Point();
		for (int i = 0; i < t.pozice.length; i++) {
			c.transform(t.pozice[i], p);
			if (p.x < -OKRAJ_VYREZU || p.y < -OKRAJ_VYREZU || p.x > w + OKRAJ_VYREZU || p.y > h + OKRAJ_VYREZU) {
				continue;
			}
			if (n == viditelne.length) {
				viditelne = Arrays.copyOf(viditelne, n * 2);
				px = Arrays.copyOf(px, n * 2);
				py = Arrays.copyOf(py, n * 2);
				vzdal = Arrays.copyOf(vzdal, n * 2);
			}
			viditelne[n] = i;
			px[n] = p.x;
			py[n] = p.y;
			final double dx = p.x - w / 2.0;
			final double dy = p.y - h / 2.0;
			vzdal[n] = dx * dx + dy * dy;
			n++;
		}
		if (n == 0) {
			return;
		}
		// nejvýš NEJVIC_ZNACEK nejbližších ke středu okna, seřazených podle vzdálenosti
		final int kolik = Math.min(n, TvarPlanovace.NEJVIC_ZNACEK);
		final double[] serazene = Arrays.copyOf(vzdal, n);
		Arrays.sort(serazene);
		final double prah = serazene[kolik - 1];
		final int[] poradi = new int[kolik];
		int m = 0;
		for (int i = 0; i < n && m < kolik; i++) {
			if (vzdal[i] <= prah) {
				int j = m++;
				while (j > 0 && vzdal[poradi[j - 1]] > vzdal[i]) {
					poradi[j] = poradi[j - 1];
					j--;
				}
				poradi[j] = i;
			}
		}
		final Color akcent = AkcentPlanovace.barva();
		final int r = POLOMER_KROUZKU;
		posledniZnacek = m;
		for (int q = 0; q < m; q++) {
			final int i = poradi[q];
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(3.6f));
			g.drawOval(px[i] - r, py[i] - r, 2 * r, 2 * r);
			g.setColor(akcent);
			g.setStroke(new BasicStroke(2.2f));
			g.drawOval(px[i] - r, py[i] - r, 2 * r, 2 * r);
		}
		for (int q = 0; q < m; q++) {
			final int i = poradi[q];
			umistiOkoloKrouzku(g, pismo, t.texty[viditelne[i]], px[i], py[i]);
		}
	}

	private Dimension rozmerStitku(final Graphics2D g, final Font f, final String text) {
		final FontMetrics fm = g.getFontMetrics(f);
		return new Dimension(fm.stringWidth(text) + 8, fm.getHeight() + 2);
	}

	/** Umístí štítek na dané místo, když se nekryje s žádným dosud umístěným; vrací, zda se nakreslil. */
	private boolean umisti(final Graphics2D g, final Font f, final String text, final int x, final int y, final boolean vynutit) {
		final Dimension d = rozmerStitku(g, f, text);
		if (!vynutit && kryje(x, y, d.width, d.height)) {
			return false;
		}
		nakresliStitek(g, f, text, x, y, AkcentPlanovace.barva());
		zapis(x, y, d.width, d.height);
		return true;
	}

	/** Zkusí osm poloh kolem kroužku a vybere první bez překryvu; jinak štítek vynechá. */
	private void umistiOkoloKrouzku(final Graphics2D g, final Font f, final String text, final int cx, final int cy) {
		final Dimension d = rozmerStitku(g, f, text);
		final int od = POLOMER_KROUZKU + 3;
		final int[][] polohy = { { od, -d.height / 2 }, { -d.width - od, -d.height / 2 }, { -d.width / 2, -od - d.height }, { -d.width / 2, od }, { od, -d.height }, { od, 0 },
				{ -d.width - od, -d.height }, { -d.width - od, 0 } };
		for (final int[] p : polohy) {
			if (pocetObdelniku < ox.length && !kryje(cx + p[0], cy + p[1], d.width, d.height)) {
				nakresliStitek(g, f, text, cx + p[0], cy + p[1], AkcentPlanovace.barva());
				zapis(cx + p[0], cy + p[1], d.width, d.height);
				return;
			}
		}
	}

	private boolean kryje(final int x, final int y, final int w, final int h) {
		for (int i = 0; i < pocetObdelniku; i++) {
			if (x < ox[i] + ow[i] && x + w > ox[i] && y < oy[i] + oh[i] && y + h > oy[i]) {
				return true;
			}
		}
		return false;
	}

	private void zapis(final int x, final int y, final int w, final int h) {
		if (pocetObdelniku < ox.length) {
			ox[pocetObdelniku] = x;
			oy[pocetObdelniku] = y;
			ow[pocetObdelniku] = w;
			oh[pocetObdelniku] = h;
			pocetObdelniku++;
		}
	}

	private Rectangle nakresliStitek(final Graphics2D g, final Font f, final String text, final int x, final int y, final Color akcent) {
		g.setFont(f);
		final FontMetrics fm = g.getFontMetrics();
		final int w = fm.stringWidth(text) + 8;
		final int h = fm.getHeight() + 2;
		final RoundRectangle2D.Double tvar = new RoundRectangle2D.Double(x, y, w, h, 4, 4);
		g.setColor(AkcentPlanovace.pozadiStitku());
		g.fill(tvar);
		g.setColor(new Color(255, 255, 255, 190));
		g.setStroke(new BasicStroke(3f));
		g.draw(tvar);
		g.setColor(akcent);
		g.setStroke(new BasicStroke(1.4f));
		g.draw(tvar);
		g.setColor(AkcentPlanovace.textStitku());
		g.drawString(text, x + 4, y + 1 + fm.getAscent());
		return new Rectangle(x, y, w, h);
	}

	@Override
	public void mouseClicked(final MouseEvent e, final MouseGestureContext ctx) {
		final Rectangle s = sipka;
		final TvarPlanovace t = sipkaTvar;
		if (s != null && t != null && t.stred != null && vyrezModel != null && SwingUtilities.isLeftMouseButton(e) && s.contains(e.getPoint())) {
			vyrezModel.presunMapuNaMoustred(t.stred);
			return;
		}
		super.mouseClicked(e, ctx);
	}
}
