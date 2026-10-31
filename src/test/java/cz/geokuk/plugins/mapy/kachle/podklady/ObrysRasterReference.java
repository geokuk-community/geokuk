package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.Arrays;
import java.util.List;

import org.mapsforge.core.model.BoundingBox;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.PolygonPokryti;

/** Přímočarý referenční výpočet rastru obrysu, se kterým se porovnává {@link ObrysRaster}. */
final class ObrysRasterReference {

	private static final double BUNKA = 0.01;
	private static final int NEJVIC_BUNEK = 600;

	private final double zapad;
	private final double jih;
	private final double bunka;
	private final int sloupcu;
	private final int radku;
	/** Součtová tabulka označených buněk, (radku + 1) × (sloupcu + 1). */
	final int[][] soucty;

	ObrysRasterReference(final PolygonPokryti obrys, final double buffer) {
		final double rozsah = Math.max(obrys.getSever() - obrys.getJih(), obrys.getVychod() - obrys.getZapad());
		bunka = Math.max(BUNKA, rozsah / NEJVIC_BUNEK);
		final int okraj = (int) Math.ceil(buffer / bunka) + 2;
		zapad = obrys.getZapad() - okraj * bunka;
		jih = obrys.getJih() - okraj * bunka;
		sloupcu = (int) Math.ceil((obrys.getVychod() - zapad) / bunka) + okraj + 1;
		radku = (int) Math.ceil((obrys.getSever() - jih) / bunka) + okraj + 1;
		final boolean[][] znaceno = new boolean[radku][sloupcu];
		final boolean[][] zaklad = new boolean[radku][sloupcu];
		for (final List<Wgs> kruh : obrys.getObrysy()) {
			hrana(kruh, zaklad);
			vypln(kruh, zaklad);
		}
		for (final List<Wgs> kruh : obrys.getDiry()) {
			hrana(kruh, zaklad);
		}
		final int n = (int) Math.ceil(buffer / bunka) + 1;
		rozsir(zaklad, znaceno, n, radku, sloupcu);
		soucty = new int[radku + 1][sloupcu + 1];
		for (int r = 0; r < radku; r++) {
			for (int s = 0; s < sloupcu; s++) {
				soucty[r + 1][s + 1] = soucty[r][s + 1] + soucty[r + 1][s] - soucty[r][s] + (znaceno[r][s] ? 1 : 0);
			}
		}
	}

	private int sloupec(final double lon) {
		return (int) Math.floor((lon - zapad) / bunka);
	}

	private int radek(final double lat) {
		return (int) Math.floor((lat - jih) / bunka);
	}

	/** Označí buňky kolem bodů podél hran. */
	private void hrana(final List<Wgs> kruh, final boolean[][] zaklad) {
		final int n = kruh.size();
		for (int i = 0; i < n; i++) {
			final Wgs a = kruh.get(i);
			final Wgs b = kruh.get((i + 1) % n);
			final double d = Math.max(Math.abs(b.lat - a.lat), Math.abs(b.lon - a.lon));
			final int kroku = Math.max(1, (int) Math.ceil(d / (bunka / 2)));
			for (int k = 0; k <= kroku; k++) {
				final double t = (double) k / kroku;
				oznac(zaklad, radek(a.lat + (b.lat - a.lat) * t), sloupec(a.lon + (b.lon - a.lon) * t), 1);
			}
		}
	}

	private void oznac(final boolean[][] zaklad, final int r, final int s, final int polomer) {
		for (int dr = -polomer; dr <= polomer; dr++) {
			for (int ds = -polomer; ds <= polomer; ds++) {
				final int rr = r + dr;
				final int ss = s + ds;
				if (rr >= 0 && rr < radku && ss >= 0 && ss < sloupcu) {
					zaklad[rr][ss] = true;
				}
			}
		}
	}

	/** Označí buňky, jejichž střed leží uvnitř kruhu (řádkové prohledávání, sudý–lichý průsečík). */
	private void vypln(final List<Wgs> kruh, final boolean[][] zaklad) {
		final int n = kruh.size();
		final double[] prusecik = new double[n];
		for (int r = 0; r < radku; r++) {
			final double lat = jih + (r + 0.5) * bunka;
			int pocet = 0;
			for (int i = 0, j = n - 1; i < n; j = i++) {
				final Wgs a = kruh.get(i);
				final Wgs b = kruh.get(j);
				if (a.lat > lat != b.lat > lat) {
					prusecik[pocet++] = (b.lon - a.lon) * (lat - a.lat) / (b.lat - a.lat) + a.lon;
				}
			}
			Arrays.sort(prusecik, 0, pocet);
			for (int p = 0; p + 1 < pocet; p += 2) {
				final int od = Math.max(0, (int) Math.ceil((prusecik[p] - zapad) / bunka - 0.5));
				final int az = Math.min(sloupcu - 1, (int) Math.floor((prusecik[p + 1] - zapad) / bunka - 0.5));
				for (int s = od; s <= az; s++) {
					zaklad[r][s] = true;
				}
			}
		}
	}

	/** Rozšíří označené buňky o {@code n} buněk na všechny strany (po osách zvlášť). */
	static void rozsir(final boolean[][] zaklad, final boolean[][] cil, final int n, final int radku, final int sloupcu) {
		final boolean[][] pomocne = new boolean[radku][sloupcu];
		for (int r = 0; r < radku; r++) {
			for (int s = 0; s < sloupcu; s++) {
				if (zaklad[r][s]) {
					for (int k = Math.max(0, s - n); k <= Math.min(sloupcu - 1, s + n); k++) {
						pomocne[r][k] = true;
					}
				}
			}
		}
		for (int r = 0; r < radku; r++) {
			for (int s = 0; s < sloupcu; s++) {
				if (pomocne[r][s]) {
					for (int k = Math.max(0, r - n); k <= Math.min(radku - 1, r + n); k++) {
						cil[k][s] = true;
					}
				}
			}
		}
	}

	/** Obdélník zasahuje označené buňky, tj. obrys je v něm nebo do bufferu od něj. */
	boolean zasahuje(final BoundingBox oblast) {
		final int s0 = Math.max(0, sloupec(oblast.minLongitude));
		final int s1 = Math.min(sloupcu - 1, sloupec(oblast.maxLongitude));
		final int r0 = Math.max(0, radek(oblast.minLatitude));
		final int r1 = Math.min(radku - 1, radek(oblast.maxLatitude));
		if (s0 > s1 || r0 > r1) {
			return false;
		}
		return soucty[r1 + 1][s1 + 1] - soucty[r0][s1 + 1] - soucty[r1 + 1][s0] + soucty[r0][s0] > 0;
	}
}
