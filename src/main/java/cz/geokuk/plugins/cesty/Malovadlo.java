package cz.geokuk.plugins.cesty;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.List;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Mouable;
import cz.geokuk.plugins.cesty.data.*;

public class Malovadlo {
	class MalovadloCesty {

		private final Cesta cesta;
		private final boolean jeCurta;
		private final boolean jeBlizkyBousekVTetoCeste;

		private final Color barvaCestyPredKurzorem;
		private final Color barvaCestyZaKurzorem;

		public MalovadloCesty(final Cesta cesta) {
			this.cesta = cesta;
			jeCurta = cesta == curta;
			jeBlizkyBousekVTetoCeste = blizkyBousek != null && blizkyBousek.getCesta() == cesta;
			if (jeCurta) {
				if (jeBlizkyBousekVTetoCeste) {
					barvaCestyPredKurzorem = FBarvy.CURTA_PRED_KURZOREM;
					barvaCestyZaKurzorem = FBarvy.CURTA_ZA_KURZOREM;
				} else {
					barvaCestyPredKurzorem = FBarvy.CURTA_NORMALNE;
					barvaCestyZaKurzorem = FBarvy.CURTA_NORMALNE;
				}
			} else {
				if (jeBlizkyBousekVTetoCeste) {
					barvaCestyPredKurzorem = FBarvy.TLUMENA_PRED_KURZOREM;
					barvaCestyZaKurzorem = FBarvy.TLUMENA_ZA_KURZOREM;
				} else {
					barvaCestyPredKurzorem = FBarvy.TLUMENA_NORMALNE;
					barvaCestyZaKurzorem = FBarvy.TLUMENA_NORMALNE;
				}
			}

		}

		private void natocVeSmeru(final Graphics2D gg, final Bod bod) {
			final Usek uvpred = bod.getUvpred();
			if (uvpred != null) {
				gg.rotate(uvpred.getUhel() - Math.PI / 2);
			} else {
				final Usek uvzad = bod.getUvzad();
				if (uvzad != null) {
					gg.rotate(uvzad.getUhel() - Math.PI / 2);
				}
			}
		}

		private void paint() {

			paintUseky();

			paintBody();

		}

		private void paintBody() {
			paintKoncoveBody();
			// vykreslování bodů přes již vykreslené úseky
			final Bod cilovyBodyKruhoveCesty = cesta.isKruh() ? cesta.getCil() : null;
			Point posledniZnacka = null;
			for (final Bod bod : cesta.getBody()) {
				// Mimo vybranou cestu se kreslí jen zvýraznění blízkého bodu, tisíce bodů dlouhé cesty se nemusí přepočítávat.
				if (!jeCurta && bod != blizkyBousek) {
					continue;
				}
				final Point p = soord.transform(bod.getMou());
				if (!vKresleneOblasti(p, p)) {
					continue;
				}
				// Značka těsně u předchozí se nekreslí, v přehledu dlouhého záznamu by jich byly statisíce přes sebe.
				final boolean zvyrazneny = bod == blizkyBousek || bod == poziceMouable;
				if (!zvyrazneny && posledniZnacka != null && Math.abs(p.x - posledniZnacka.x) < ROZESTUP_ZNACEK && Math.abs(p.y - posledniZnacka.y) < ROZESTUP_ZNACEK) {
					continue;
				}
				if (!zvyrazneny) {
					posledniZnacka = p;
				}
				if (bod == poziceMouable) {
					g.setColor(Color.RED);
				} else {
					g.setColor(barvaCestyPredKurzorem);
				}
				if (jeBlizkyBousekVTetoCeste) {
					if (blizkyBousek == bod || blizkyBousek == bod.getUvzad()) {
						g.setColor(barvaCestyZaKurzorem);
					}
				} else {}
				if (jeCurta && bod != cilovyBodyKruhoveCesty && !bod.isWpt()) {
					final Graphics2D gg = (Graphics2D) g.create();
					gg.translate(p.x, p.y);
					natocVeSmeru(gg, bod);
					// gg.rotate(1);
					gg.fillRect(-4, -4, 8, 8);
					gg.setColor(FBarvy.ZVYRAZNOVAC_BLIZKEHO_BOUSKU);
					gg.setStroke(new BasicStroke(2));
					gg.drawPolygon(bodovaSipecka);
					// gg.drawLineRect(- 3, - 3, 6, 6);
				}
				if (bod == blizkyBousek) {
					final Graphics2D gg = (Graphics2D) g.create();
					gg.setColor(FBarvy.ZVYRAZNOVAC_BLIZKEHO_BOUSKU);
					gg.translate(p.x, p.y);
					natocVeSmeru(gg, bod);
					gg.fillPolygon(bodovaSipecka);
				} else {}

			}
		}

		private void paintKoncoveBody() {
			if (cesta.isJednobodova()) {
				final Graphics2D gg = (Graphics2D) g.create();
				final Point p = soord.transform(cesta.getStart().getMou());
				gg.translate(p.x, p.y);
				gg.fill(bodStartocilu);
			} else if (cesta.isKruh()) {
				final Graphics2D gg = (Graphics2D) g.create();
				final Point p = soord.transform(cesta.getStart().getMou());
				gg.translate(p.x, p.y);
				gg.fill(bodStartocilu);
			} else { // normální cesta
				final Bod start = cesta.getStart();
				paintKoncovyBod(start, barvaCestyPredKurzorem);
				final Bod cil = cesta.getCil();
				paintKoncovyBod(cil, barvaCestyZaKurzorem);
			}
		}

		private void paintKoncovyBod(final Bod bod, final Color color) {
			final Graphics2D gg = (Graphics2D) g.create();
			final Point p = soord.transform(bod.getMou());
			gg.translate(p.x, p.y);
			natocVeSmeru(gg, bod);
			gg.scale(2, 2);
			gg.setColor(color);
			gg.fillPolygon(bodovaSipecka);
		}

		/** Tmavý lem pod úseky vybrané cesty: plný pod rovnými úseky, tečkovaný pod vzdušnými. */
		private void paintLem() {
			final Graphics2D gg = createGraphics();
			gg.setColor(FBarvy.CURTA_LEM);
			Point konec = null;
			for (final Usek usek : cesta.getUseky()) {
				final Point p1 = konec != null ? konec : soord.transform(usek.getBvzad().getMou());
				final Point p2 = soord.transform(usek.getBvpred().getMou());
				konec = p2;
				final List<Mou> trasa = usek.getTrasa();
				if (trasa != null) {
					gg.setStroke(strokeUsekuLem);
					kresliTrasu(gg, trasa);
					continue;
				}
				if (p1.equals(p2) || !vKresleneOblasti(p1, p2)) {
					continue;
				}
				gg.setStroke(usek.isVzdusny() ? strokeVzdusnyLem : strokeUsekuLem);
				gg.drawLine(p1.x, p1.y, p2.x, p2.y);
			}
			gg.dispose();
		}

		private void paintUseky() {
			if (jeCurta) {
				paintLem();
			}

			// Vykreslování úseků této cesty
			g.setColor(barvaCestyPredKurzorem);
			// Konec posledního nakresleného úseku. Úseky mimo kreslenou oblast a úseky, které nevyjdou z pixelu, se nekreslí;
			// u záznamu se statisíci bodů jich je většina.
			Point konec = null;
			for (final Usek usek : cesta.getUseky()) {
				final Point p1 = konec != null ? konec : soord.transform(usek.getBvzad().getMou());
				final Point p2 = soord.transform(usek.getBvpred().getMou());
				if (blizkyBousek == usek.getBousekVzad()) {
					g.setColor(barvaCestyZaKurzorem);
				}
				final List<Mou> trasa = usek.getTrasa();
				if (trasa != null) {
					konec = p2;
					g.setStroke(strokePoCeste);
					if (blizkyBousek == usek && mouDeliciNaBlizkemBousku != null) {
						// Před kurzorem jednou barvou, za ním druhou, jako u rovného úseku.
						final Mou delici = usek.getNejblizsiBodKPrimce(mouDeliciNaBlizkemBousku);
						final int i = indexUsecky(trasa, delici);
						final List<Mou> pred = new java.util.ArrayList<>(trasa.subList(0, i));
						pred.add(delici);
						final List<Mou> za = new java.util.ArrayList<>();
						za.add(delici);
						za.addAll(trasa.subList(i, trasa.size()));
						kresliTrasu(g, pred);
						g.setColor(barvaCestyZaKurzorem);
						kresliTrasu(g, za);
						final Graphics2D gg = createGraphics();
						gg.setColor(FBarvy.USEK_ZVYRAZNENY);
						gg.setStroke(new BasicStroke(1));
						kresliTrasu(gg, trasa);
						gg.dispose();
					} else {
						kresliTrasu(g, trasa);
					}
					continue;
				}
				if (blizkyBousek != usek) {
					if (p1.equals(p2)) {
						konec = p1;
						continue;
					}
					konec = p2;
					if (!vKresleneOblasti(p1, p2)) {
						continue;
					}
				} else {
					konec = p2;
				}
				g.setStroke(strokeUseku);
				if (usek.isVzdusny()) {
					g.setStroke(strokeVzdusny);
				}
				if (blizkyBousek == usek) {
					// bod musi být na úsečce, ale nějaká zaokrouhlování mohouz působit,
					// že není
					final Mou nejblizsiBod = usek.getNejblizsiBodKPrimce(mouDeliciNaBlizkemBousku);
					final Point p = soord.transform(nejblizsiBod);
					g.drawLine(p1.x, p1.y, p.x, p.y);
					g.setColor(barvaCestyZaKurzorem);
					g.drawLine(p.x, p.y, p2.x, p2.y);
					// a ještě vykreslit zvýraznění
					final Graphics2D gg = createGraphics();
					gg.setColor(FBarvy.USEK_ZVYRAZNENY);
					gg.setStroke(new BasicStroke(1));
					gg.drawLine(p1.x, p1.y, p2.x, p2.y);
				} else {
					// Hlavní vykreslování úseku
					g.drawLine(p1.x, p1.y, p2.x, p2.y);
				}
			}
		}

		/** Index konce úsečky trasy, na které leží bod (1 až počet bodů − 1). */
		private int indexUsecky(final List<Mou> trasa, final Mou bod) {
			int nejlepsi = 1;
			double nejmene = Double.MAX_VALUE;
			for (int i = 1; i < trasa.size(); i++) {
				final Mou a = trasa.get(i - 1);
				final Mou b = trasa.get(i);
				final double d = java.awt.geom.Line2D.ptSegDistSq(a.xx, a.yy, b.xx, b.yy, bod.xx, bod.yy);
				if (d < nejmene) {
					nejmene = d;
					nejlepsi = i;
				}
			}
			return nejlepsi;
		}

		/** Lomená čára trasy po cestách; mimo kreslenou oblast se nekreslí, body v jednom pixelu se slijí. */
		private void kresliTrasu(final Graphics2D gg, final List<Mou> trasa) {
			final int[] xs = new int[trasa.size()];
			final int[] ys = new int[trasa.size()];
			int n = 0;
			int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
			for (final Mou mou : trasa) {
				final Point p = soord.transform(mou);
				if (n > 0 && p.x == xs[n - 1] && p.y == ys[n - 1]) {
					continue;
				}
				xs[n] = p.x;
				ys[n] = p.y;
				n++;
				minX = Math.min(minX, p.x);
				minY = Math.min(minY, p.y);
				maxX = Math.max(maxX, p.x);
				maxY = Math.max(maxY, p.y);
			}
			if (n < 2 || !vKresleneOblasti(new Point(minX, minY), new Point(maxX, maxY))) {
				return;
			}
			gg.drawPolyline(xs, ys, n);
		}

	} // MalovadloCesty

	private static final int SIRKA_CARY_VYBRANE = 6;
	/** Největší přesah kreslení přes bod: koncová šipka, kolečko startu. */
	private static final int OKRAJ = 12;
	/** Značka bodu vybrané cesty má 8 px. */
	private static final int ROZESTUP_ZNACEK = 4;

	private static final Polygon bodovaSipecka = new Polygon(new int[] { -3, 2, 5, 2, -3 }, new int[] { 3, 3, 0, -3, -3 }, 5);

	private static final Ellipse2D bodStartocilu = new Ellipse2D.Float(-10, -10, 20, 20);

	private final Graphics2D g;

	/** Úsek po cestě plnou čárou, vzdušný úsek tečkovaně, rovný úsek čárkovaně, všechny přes tmavý lem. */
	private final Stroke strokePoCeste = new BasicStroke(SIRKA_CARY_VYBRANE - 1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private final Stroke strokeVzdusny = new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[] { 0.1f, 8 }, 0);
	private final Stroke strokeVzdusnyLem = new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[] { 0.1f, 8 }, 0);
	private final Stroke strokeUseku = new BasicStroke(SIRKA_CARY_VYBRANE - 1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 12, 6 }, 0);
	private final Stroke strokeUsekuLem = new BasicStroke(SIRKA_CARY_VYBRANE + 3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	/** Překreslovaná oblast s okrajem na šířku čáry a značky bodu, null = celá komponenta. */
	private final Rectangle kreslenaOblast;

	private final Doc doc;
	private final Bousek0 blizkyBousek;

	private final Coord soord;

	/** Obsahuje bod, který je přidáván, pak se kreslí k němu přidávací čára */
	private final Mou pridavanyBod1;

	private final Mou pridavanyBod2;

	private final Mou mouDeliciNaBlizkemBousku;

	private final Cesta curta;

	private final Mouable poziceMouable;

	public Malovadlo(final Graphics2D g2, final MalovadloParams params) {
		g = g2;
		doc = params.doc;
		curta = params.curta;
		blizkyBousek = params.blizkyBousek;
		pridavanyBod1 = params.mouPridavanyBod1;
		pridavanyBod2 = params.mouPridavanyBod2;
		soord = params.soord;
		mouDeliciNaBlizkemBousku = params.mouDeliciNaBlizkemBousku;
		poziceMouable = params.poziceMouable;
		final Rectangle clip = g2.getClipBounds();
		kreslenaOblast = clip == null ? null : new Rectangle(clip.x - OKRAJ, clip.y - OKRAJ, clip.width + 2 * OKRAJ, clip.height + 2 * OKRAJ);
	}

	/** Úsečka z p1 do p2 může zasáhnout do kreslené oblasti. */
	private boolean vKresleneOblasti(final Point p1, final Point p2) {
		final Rectangle r = kreslenaOblast;
		return r == null || !(p1.x < r.x && p2.x < r.x || p1.y < r.y && p2.y < r.y || p1.x > r.x + r.width && p2.x > r.x + r.width || p1.y > r.y + r.height && p2.y > r.y + r.height);
	}

	public void paint() {
		// long kvadratMaximalniVzdalenosti = getKvadratMaximalniVzdalenosti();

		for (final Cesta cesta : doc.getCesty()) {
			if (cesta == null || cesta.isEmpty()) {
				continue;
			}
			final MalovadloCesty malovadloCesty = new MalovadloCesty(cesta);
			malovadloCesty.paint();
		}
		paintPridavaciUsecka();

	}

	private Graphics2D createGraphics() {
		return (Graphics2D) g.create();

	}

	private void paintPridavaciUsecka() {
		if (pridavanyBod1 == null || pridavanyBod2 == null) {
			return;
		}
		final Point p1 = soord.transform(pridavanyBod1);
		// Point p2 = getSoord().transform(semSePridava.getMou());
		// Point p2 = getSoord().transform(moucur);
		final Point p2 = soord.transform(pridavanyBod2);
		final Graphics2D gg = createGraphics();
		// gg.setColor(FBarvy.USEK_PRIDAVANY);
		gg.translate(p1.x, p1.y);
		gg.rotate(Math.atan2(p2.y - p1.y, p2.x - p1.x));
		final double delka = Math.hypot(p2.x - p1.x, p2.y - p1.y);
		gg.setStroke(new BasicStroke(2));
		gg.drawLine(0, -2, (int) delka, -2);
		gg.drawLine(0, 2, (int) delka, 2);
	}
}
