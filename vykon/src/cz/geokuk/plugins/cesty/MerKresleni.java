package cz.geokuk.plugins.cesty;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Random;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.*;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.kind.kes.EKesWptType;
import cz.geokuk.plugins.kesoidkruhy.*;
import cz.geokuk.util.index2d.*;

/** Měření kreslení: obří cesta, zvýrazňovací kruhy, zjišťování typu waypointu. */
public class MerKresleni {

	static final Dimension OKNO = new Dimension(1400, 900);

	public static void main(final String[] a) throws Exception {
		System.setProperty("java.awt.headless", "true");
		decode();
		cesta();
		kruhy();
	}

	/** Medián v ms z opakování. */
	static double mer(final Runnable r, final int opakovani) {
		final double[] t = new double[opakovani];
		r.run();
		for (int i = 0; i < opakovani; i++) {
			final long t0 = System.nanoTime();
			r.run();
			t[i] = (System.nanoTime() - t0) / 1e6;
		}
		java.util.Arrays.sort(t);
		return t[opakovani / 2];
	}

	static void decode() {
		final int n = 1_000_000;
		final double ms = mer(() -> {
			int x = 0;
			for (int i = 0; i < n; i++) {
				if (EKesWptType.decode(i % 2 == 0 ? "Geocache" : "Final Location") != null) {
					x++;
				}
			}
			if (x == 42) {
				System.out.print("");
			}
		}, 5);
		System.out.printf("decode: %.3f µs na volání (polovina neznámých typů)%n", ms * 1000 / n);
	}

	static void cesta() {
		final Doc doc = new Doc();
		final Updator updator = new Updator();
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		final Random r = new Random(1);
		double lat = 50.0, lon = 14.4, smer = 0;
		final int bodu = 500_000;
		for (int i = 0; i < bodu; i++) {
			smer += r.nextGaussian() * 0.3;
			lat += Math.cos(smer) * 0.00004;
			lon += Math.sin(smer) * 0.00006;
			updator.pridejNaKonec(cesta, new Wgs(lat, lon).toMou());
		}
		final Mou stred = new Wgs(50.0, 14.4).toMou();
		for (final int meritko : new int[] { 8, 12, 15 }) {
			final Coord soord = new Coord(meritko, stred, OKNO, 0);
			for (final boolean vybrana : new boolean[] { false, true }) {
				final MalovadloParams p = new MalovadloParams();
				p.doc = doc;
				p.soord = soord;
				p.curta = vybrana ? cesta : null;
				final double cele = mer(() -> kresli(p, null), 5);
				final double dlazdice = mer(() -> kresli(p, new Rectangle(512, 256, 256, 256)), 5);
				System.out.printf("cesta %d bodů, měřítko %d, %s: celé okno %.0f ms, jedna dlaždice %.0f ms%n", bodu, meritko, vybrana ? "vybraná" : "nevybraná", cele, dlazdice);
			}
		}
	}

	static void kresli(final MalovadloParams p, final Rectangle clip) {
		final BufferedImage img = new BufferedImage(OKNO.width, OKNO.height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		if (clip != null) {
			g.setClip(clip);
		}
		new Malovadlo(g, p).paint();
		g.dispose();
	}

	static void kruhy() throws Exception {
		final Mou stred = new Wgs(50.0, 14.4).toMou();
		final Coord soord = new Coord(12, stred, OKNO, 0);
		final BoundingRect br = soord.getBoundingRect();
		Indexator<Wpt> idx = new Indexator<>(BoundingRect.ALL);
		final Random r = new Random(2);
		final int wpt = 25_000;
		for (int i = 0; i < wpt; i++) {
			final Wpt w = new Wpt();
			final int xx = (int) (br.xx1 + (long) (r.nextDouble() * ((long) br.xx2 - br.xx1)));
			final int yy = (int) (br.yy1 + (long) (r.nextDouble() * ((long) br.yy2 - br.yy1)));
			w.setWgs(new Mou(xx, yy).toWgs());
			idx = idx.add(xx, yy, w);
		}
		final JZvyraznovaciKruhySlide slide = new JZvyraznovaciKruhySlide();
		nastav(slide, "iIndexator", idx);
		final KruhySettings k = new KruhySettings();
		nastav(slide, "kruhy", k);
		slide.setSoord(soord);
		slide.setSize(OKNO);
		final double cele = mer(() -> kresliSlide(slide, null), 7);
		final double dlazdice = mer(() -> kresliSlide(slide, new Rectangle(512, 256, 256, 256)), 7);
		System.out.printf("kruhy %d wpt ve výřezu: celé okno %.0f ms, jedna dlaždice %.0f ms%n", wpt, cele, dlazdice);
	}

	static void kresliSlide(final Component c, final Rectangle clip) {
		final BufferedImage img = new BufferedImage(OKNO.width, OKNO.height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		if (clip != null) {
			g.setClip(clip);
		}
		try {
			((cz.geokuk.core.coord.JSingleSlide0) c).render(g);
		} catch (final InterruptedException e) {
			throw new IllegalStateException(e);
		}
		g.dispose();
	}

	static void nastav(final Object o, final String pole, final Object hodnota) throws Exception {
		final Field f = o.getClass().getDeclaredField(pole);
		f.setAccessible(true);
		f.set(o, hodnota);
	}
}
