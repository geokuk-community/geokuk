package cz.geokuk.plugins.challenge;

import java.util.Arrays;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.kind.kes.EKesDiffTerRating;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;
import cz.geokuk.util.index2d.Indexator;

/**
 * Keše ve výřezu, které patří do zvýrazněného sektoru 360° nebo políčka D/T: jejich poloha, text štítku a okraj ikony. Staví se jednou pro dané okno, výběr a zvětšení ikon;
 * nejvýš {@link TvarPlanovace#NEJVIC_ZNACEK} nejbližších ke středu okna.
 */
final class ZvyrazneniKese {

	private static final int OKRAJ_VYREZU = 12;
	private static final String[] TEXTY_SEKTORU = new String[360];

	static {
		for (int i = 0; i < TEXTY_SEKTORU.length; i++) {
			TEXTY_SEKTORU[i] = i + "°";
		}
	}

	final TvarPlanovace tvar;
	final Coord coord;
	final Indexator<Wpt> indexator;
	final double meritkoIkon;
	final VyberZvyrazneni vyber;
	int pocet;
	int[] x = new int[0];
	int[] y = new int[0];
	int[] ikonaVpravo = new int[0];
	int[] ikonaStredY = new int[0];
	String[] text = new String[0];

	private ZvyrazneniKese(final TvarPlanovace tvar, final Coord coord, final Indexator<Wpt> indexator, final double meritkoIkon, final VyberZvyrazneni vyber) {
		this.tvar = tvar;
		this.coord = coord;
		this.indexator = indexator;
		this.meritkoIkon = meritkoIkon;
		this.vyber = vyber;
	}

	boolean jeStejne(final TvarPlanovace t, final Coord c, final Indexator<Wpt> i, final double meritko, final VyberZvyrazneni v) {
		return tvar == t && coord.equals(c) && indexator == i && meritkoIkon == meritko && vyber.equals(v);
	}

	/** Projde keše ve výřezu a vybere ty, které patří do sektoru nebo políčka výběru. Jednorázově pro dané okno a výběr, ne při každém překreslení. */
	static ZvyrazneniKese postav(final TvarPlanovace t, final Coord c, final Indexator<Wpt> indexator, final double meritko, final VyberZvyrazneni vyber) {
		final ZvyrazneniKese z = new ZvyrazneniKese(t, c, indexator, meritko, vyber);
		if (indexator == null || vyber.jeNic()) {
			return z;
		}
		final PrehledChallenge p = t.prehled;
		final int w = c.getWidth();
		final int h = c.getHeight();
		final java.awt.Point bod = new java.awt.Point();
		int n = 0;
		Wpt[] wpts = new Wpt[256];
		int[] xs = new int[256];
		int[] ys = new int[256];
		double[] vz = new double[256];
		for (final Wpt wpt : (Iterable<Wpt>) () -> indexator.bound(c.getBoundingRect()).stream().iterator()) {
			final Kesoid kesoid = wpt.getKesoid();
			if (kesoid == null || wpt != kesoid.getMainWpt() || !KesVstup.jeVhodny(kesoid) || !patri(p, vyber, kesoid, wpt)) {
				continue;
			}
			PixelyMapy.transform(c, wpt.getMou(), bod);
			if (bod.x < -OKRAJ_VYREZU || bod.y < -OKRAJ_VYREZU || bod.x > w + OKRAJ_VYREZU || bod.y > h + OKRAJ_VYREZU) {
				continue;
			}
			if (n == wpts.length) {
				wpts = Arrays.copyOf(wpts, n * 2);
				xs = Arrays.copyOf(xs, n * 2);
				ys = Arrays.copyOf(ys, n * 2);
				vz = Arrays.copyOf(vz, n * 2);
			}
			wpts[n] = wpt;
			xs[n] = bod.x;
			ys[n] = bod.y;
			final double dx = bod.x - w / 2.0;
			final double dy = bod.y - h / 2.0;
			vz[n] = dx * dx + dy * dy;
			n++;
		}
		final int kolik = Math.min(n, TvarPlanovace.NEJVIC_ZNACEK);
		final int[] poradi = NejblizsiPrvky.vyber(vz, n, kolik);
		z.pocet = kolik;
		z.x = new int[kolik];
		z.y = new int[kolik];
		z.ikonaVpravo = new int[kolik];
		z.ikonaStredY = new int[kolik];
		z.text = new String[kolik];
		final int[] okraje = new int[2];
		for (int q = 0; q < kolik; q++) {
			final int i = poradi[q];
			z.x[q] = xs[i];
			z.y[q] = ys[i];
			IkonaKese.okraje(wpts[i], meritko, okraje);
			z.ikonaVpravo[q] = okraje[0];
			z.ikonaStredY[q] = okraje[1];
			z.text[q] = vyber.sektor >= 0 ? TEXTY_SEKTORU[vyber.sektor] : vyber.stitek;
		}
		return z;
	}

	/** Patří keš do zvýrazněného sektoru (a oblasti challenge) nebo políčka? */
	private static boolean patri(final PrehledChallenge p, final VyberZvyrazneni vyber, final Kesoid kesoid, final Wpt wpt) {
		if (vyber.sektor >= 0) {
			final double lat = wpt.getWgs().lat;
			final double lon = wpt.getWgs().lon;
			return p.sektorBodu(lat, lon) == vyber.sektor && p.jeVOblasti(lat, lon);
		}
		final Kes kes = (Kes) kesoid;
		final EKesDiffTerRating d = kes.getDifficulty();
		final EKesDiffTerRating t = kes.getTerrain();
		return d != null && t != null && vyber.stitek.equals(p.stitekPolicka(d.ordinal(), t.ordinal()));
	}
}
