package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.mapsforge.core.model.BoundingBox;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.PolygonPokryti;

/**
 * Hrubý rastr obrysu mapy rozšířený o buffer: buňka je označená, když v jejím okolí do vzdálenosti bufferu leží obrys (včetně vnitřku). Rozhoduje se o souboru, jehož
 * data sahají kousek za obrys; dotaz na obdélník je O(1) a nikdy neřekne „mimo“ tam, kde obrys v bufferu je.
 */
final class ObrysRaster {

	private static final double BUNKA = 0.01;
	private static final int NEJVIC_BUNEK = 600;

	private final double zapad;
	private final double jih;
	private final double bunka;
	private final int sloupcu;
	private final int radku;
	/** Součtová tabulka označených buněk, (radku + 1) × (sloupcu + 1). */
	private final int[][] soucty;

	/** Rastry katalogových obrysů; raster je po výpočtu neměnný a stejný obrys se počítal při každém otevření map. */
	private static final Cache<PolygonPokryti, ConcurrentMap<Double, ObrysRaster>> PAMET = CacheBuilder.newBuilder().weakKeys().softValues().build();

	/** Raster obrysu s bufferem; pro tentýž objekt obrysu se počítá jednou. */
	static ObrysRaster pro(final PolygonPokryti obrys, final double buffer) {
		return PAMET.asMap().computeIfAbsent(obrys, o -> new ConcurrentHashMap<>()).computeIfAbsent(buffer, b -> new ObrysRaster(obrys, b));
	}

	ObrysRaster(final PolygonPokryti obrys, final double buffer) {
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

	/**
	 * Rozšíří označené buňky o {@code n} buněk na všechny strany (po osách zvlášť). Každá osa se projde dvakrát po řádcích a drží si jen vzdálenost k poslednímu
	 * označenému, takže práce nezávisí na {@code n}.
	 */
	static void rozsir(final boolean[][] zaklad, final boolean[][] cil, final int n, final int radku, final int sloupcu) {
		final boolean[][] pomocne = new boolean[radku][sloupcu];
		for (int r = 0; r < radku; r++) {
			final boolean[] z = zaklad[r];
			final boolean[] p = pomocne[r];
			int posledni = Integer.MIN_VALUE / 2;
			for (int s = 0; s < sloupcu; s++) {
				if (z[s]) {
					posledni = s;
				}
				p[s] = s - posledni <= n;
			}
			posledni = Integer.MAX_VALUE / 2;
			for (int s = sloupcu - 1; s >= 0; s--) {
				if (z[s]) {
					posledni = s;
				}
				if (posledni - s <= n) {
					p[s] = true;
				}
			}
		}
		final int[] posledniRadek = new int[sloupcu];
		Arrays.fill(posledniRadek, Integer.MIN_VALUE / 2);
		for (int r = 0; r < radku; r++) {
			final boolean[] p = pomocne[r];
			final boolean[] c = cil[r];
			for (int s = 0; s < sloupcu; s++) {
				if (p[s]) {
					posledniRadek[s] = r;
				}
				c[s] = r - posledniRadek[s] <= n;
			}
		}
		Arrays.fill(posledniRadek, Integer.MAX_VALUE / 2);
		for (int r = radku - 1; r >= 0; r--) {
			final boolean[] p = pomocne[r];
			final boolean[] c = cil[r];
			for (int s = 0; s < sloupcu; s++) {
				if (p[s]) {
					posledniRadek[s] = r;
				}
				if (posledniRadek[s] - r <= n) {
					c[s] = true;
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
