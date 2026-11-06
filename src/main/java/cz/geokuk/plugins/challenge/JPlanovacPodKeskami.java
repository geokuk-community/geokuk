package cz.geokuk.plugins.challenge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.List;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Wgs;

/** Vrstva pod kešemi: prstenec sektorů kolem středu (splněno tenká zelená, chybí silná červená, nejisté šedé čárkované), klín vybraného sektoru a čára k vybranému kandidátovi. */
public class JPlanovacPodKeskami extends JVrstvaPlanovace {

	private static final long serialVersionUID = 1L;

	static final Color SPLNENY = new Color(0x2E8B3A);
	static final Color CHYBI = new Color(0xD32F2F);
	static final Color NEJISTY = new Color(0x707070);
	private static final Color LEM = new Color(255, 255, 255, 200);

	@Override
	public void paintComponent(final Graphics aG) {
		final TvarPlanovace t = tvar();
		if (t == null || t.stred == null || !kresliSektory() || !isSoordInitialized()) {
			return;
		}
		final Coord c = getSoord();
		final double polomer = TvarPlanovace.polomerPrstence(c);
		final Graphics2D g = (Graphics2D) aG.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			final Point p = new Point();
			final List<GeometrieSektoru.Bod> klin = t.klin(polomer);
			if (!klin.isEmpty()) {
				final Color a = AkcentPlanovace.barva();
				g.setColor(new Color(a.getRed(), a.getGreen(), a.getBlue(), 55));
				g.fill(cesta(c, klin, p, true));
			}
			if (t.vybranyPozice != null) {
				final Point s = c.transform(t.stred);
				final Point k = c.transform(t.vybranyPozice);
				g.setColor(LEM);
				g.setStroke(new BasicStroke(3.5f));
				g.drawLine(s.x, s.y, k.x, k.y);
				g.setColor(AkcentPlanovace.barva());
				g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[] { 6f, 4f }, 0f));
				g.drawLine(s.x, s.y, k.x, k.y);
			}
			for (final GeometrieSektoru.Usek u : t.prstenec(polomer)) {
				final Path2D cesta = cesta(c, u.body(), p, false);
				final boolean chybi = u.stav() == GeometrieSektoru.Stav.CHYBI;
				final float sirka = chybi ? 4.5f : 2.2f;
				g.setColor(LEM);
				g.setStroke(new BasicStroke(sirka + 2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
				g.draw(cesta);
				g.setColor(chybi ? CHYBI : u.stav() == GeometrieSektoru.Stav.NEJISTY ? NEJISTY : SPLNENY);
				g.setStroke(u.stav() == GeometrieSektoru.Stav.NEJISTY ? new BasicStroke(sirka, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[] { 5f, 3f }, 0f)
						: new BasicStroke(sirka, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
				g.draw(cesta);
			}
		} finally {
			g.dispose();
		}
	}

	private static Path2D cesta(final Coord c, final List<GeometrieSektoru.Bod> body, final Point p, final boolean uzavrit) {
		final Path2D.Double cesta = new Path2D.Double();
		boolean prvni = true;
		for (final GeometrieSektoru.Bod b : body) {
			final Mou m = new Wgs(b.lat, b.lon).toMou();
			c.transform(m, p);
			if (prvni) {
				cesta.moveTo(p.x, p.y);
				prvni = false;
			} else {
				cesta.lineTo(p.x, p.y);
			}
		}
		if (uzavrit) {
			cesta.closePath();
		}
		return cesta;
	}
}
