package cz.geokuk.plugins.cesty.routovani;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Graf cest v obdélníku. Uzly jsou body cest podle souřadnic v mikrostupních; body, které se liší nejvýš o 1 µ°, jsou tentýž uzel (tentýž bod v sousedních
 * dlaždicích mapy se tak liší kvůli kódování vůči rohu dlaždice).
 */
final class GrafCest {

	private static final double METRU_NA_MIKROSTUPEN = 6371008.8 * Math.PI / 180 / 1e6;
	private static final int TOLERANCE = 1;
	private static final long PRAZDNO = Long.MIN_VALUE;

	/** Kosinus zeměpisné šířky středu grafu; vzdálenosti se počítají v rovině, aby odhad A* byl konzistentní. */
	private final double cosLat;
	/** Obdélník, pro který se graf staví, v mikrostupních. */
	private final int jih, zapad, sever, vychod;

	private int uzlu;
	private int[] lat = new int[1024];
	private int[] lon = new int[1024];

	/** Hrany jako dvojice uzlů (obousměrné) a jejich koeficient. */
	private int hran;
	private int[] hranaZ = new int[1024];
	private int[] hranaDo = new int[1024];
	private float[] hranaKoef = new float[1024];

	/** Index souřadnice → uzel, otevřené adresování. */
	private long[] klice = new long[2048];
	private int[] hodnoty = new int[2048];

	/** Sousedé po {@link #dokonci()}: uzly v {@code sousede[zacatek[u]..zacatek[u+1])}. */
	private int[] zacatek;
	private int[] sousede;
	private float[] sousedKoef;
	private int[] komponenta;
	/** Zda komponenta má uzel mimo obdélník, tedy může pokračovat dál. */
	private boolean[] otevrena;

	GrafCest(final int jih, final int zapad, final int sever, final int vychod) {
		this.jih = jih;
		this.zapad = zapad;
		this.sever = sever;
		this.vychod = vychod;
		cosLat = Math.cos(Math.toRadians((jih + sever) / 2e6));
		Arrays.fill(klice, PRAZDNO);
	}

	int pocetUzlu() {
		return uzlu;
	}

	int pocetHran() {
		return hran;
	}

	int lat(final int u) {
		return lat[u];
	}

	int lon(final int u) {
		return lon[u];
	}

	void pridejCaru(final int[] cara, final float koef) {
		int pred = uzel(cara[0], cara[1]);
		for (int i = 2; i + 1 < cara.length; i += 2) {
			final int u = uzel(cara[i], cara[i + 1]);
			if (u != pred) {
				pridejHranu(pred, u, koef);
			}
			pred = u;
		}
	}

	/** Vzdálenost v metrech mezi body v mikrostupních. */
	double metry(final int lat1, final int lon1, final int lat2, final int lon2) {
		final double dy = lat2 - lat1;
		final double dx = (lon2 - lon1) * cosLat;
		return Math.sqrt(dx * dx + dy * dy) * METRU_NA_MIKROSTUPEN;
	}

	private double metry(final int u, final int v) {
		return metry(lat[u], lon[u], lat[v], lon[v]);
	}

	private static long klic(final int la, final int lo) {
		return (long) la << 32 | lo & 0xffffffffL;
	}

	private int najdi(final long k) {
		final int maska = klice.length - 1;
		for (int i = mix(k) & maska;; i = i + 1 & maska) {
			if (klice[i] == k) {
				return hodnoty[i];
			}
			if (klice[i] == PRAZDNO) {
				return -1;
			}
		}
	}

	private void vloz(final long k, final int hodnota) {
		final int maska = klice.length - 1;
		int i = mix(k) & maska;
		while (klice[i] != PRAZDNO) {
			i = i + 1 & maska;
		}
		klice[i] = k;
		hodnoty[i] = hodnota;
	}

	private static int mix(final long k) {
		final long h = k * 0x9E3779B97F4A7C15L;
		return (int) (h ^ h >>> 32);
	}

	private int uzel(final int la, final int lo) {
		int u = najdi(klic(la, lo));
		if (u >= 0) {
			return u;
		}
		for (int d1 = -TOLERANCE; d1 <= TOLERANCE; d1++) {
			for (int d2 = -TOLERANCE; d2 <= TOLERANCE; d2++) {
				if (d1 != 0 || d2 != 0) {
					u = najdi(klic(la + d1, lo + d2));
					if (u >= 0) {
						return u;
					}
				}
			}
		}
		return novyUzel(la, lo);
	}

	private int novyUzel(final int la, final int lo) {
		if (uzlu == lat.length) {
			lat = Arrays.copyOf(lat, uzlu * 2);
			lon = Arrays.copyOf(lon, uzlu * 2);
		}
		if ((uzlu + 1) * 2 > klice.length) {
			zvetsiIndex();
		}
		lat[uzlu] = la;
		lon[uzlu] = lo;
		vloz(klic(la, lo), uzlu);
		return uzlu++;
	}

	private void zvetsiIndex() {
		final long[] stareKlice = klice;
		final int[] stareHodnoty = hodnoty;
		klice = new long[stareKlice.length * 2];
		hodnoty = new int[stareKlice.length * 2];
		Arrays.fill(klice, PRAZDNO);
		for (int i = 0; i < stareKlice.length; i++) {
			if (stareKlice[i] != PRAZDNO) {
				vloz(stareKlice[i], stareHodnoty[i]);
			}
		}
	}

	private void pridejHranu(final int u, final int v, final float koef) {
		if (hran == hranaZ.length) {
			hranaZ = Arrays.copyOf(hranaZ, hran * 2);
			hranaDo = Arrays.copyOf(hranaDo, hran * 2);
			hranaKoef = Arrays.copyOf(hranaKoef, hran * 2);
		}
		hranaZ[hran] = u;
		hranaDo[hran] = v;
		hranaKoef[hran] = koef;
		hran++;
	}

	/** Po přidání všech čar sestaví seznamy sousedů a komponenty souvislosti. */
	void dokonci() {
		zacatek = new int[uzlu + 1];
		for (int i = 0; i < hran; i++) {
			zacatek[hranaZ[i] + 1]++;
			zacatek[hranaDo[i] + 1]++;
		}
		for (int u = 0; u < uzlu; u++) {
			zacatek[u + 1] += zacatek[u];
		}
		sousede = new int[hran * 2];
		sousedKoef = new float[hran * 2];
		final int[] pozice = Arrays.copyOf(zacatek, uzlu);
		for (int i = 0; i < hran; i++) {
			final int u = hranaZ[i];
			final int v = hranaDo[i];
			sousede[pozice[u]] = v;
			sousedKoef[pozice[u]++] = hranaKoef[i];
			sousede[pozice[v]] = u;
			sousedKoef[pozice[v]++] = hranaKoef[i];
		}
		komponenta = new int[uzlu];
		Arrays.fill(komponenta, -1);
		final int[] zasobnik = new int[Math.max(1, uzlu)];
		int komponent = 0;
		for (int s = 0; s < uzlu; s++) {
			if (komponenta[s] >= 0) {
				continue;
			}
			int vrchol = 0;
			zasobnik[vrchol++] = s;
			komponenta[s] = komponent;
			while (vrchol > 0) {
				final int u = zasobnik[--vrchol];
				for (int j = zacatek[u]; j < zacatek[u + 1]; j++) {
					final int v = sousede[j];
					if (komponenta[v] < 0) {
						komponenta[v] = komponent;
						zasobnik[vrchol++] = v;
					}
				}
			}
			komponent++;
		}
		otevrena = new boolean[komponent];
		for (int u = 0; u < uzlu; u++) {
			if (lat[u] < jih || lat[u] > sever || lon[u] < zapad || lon[u] > vychod) {
				otevrena[komponenta[u]] = true;
			}
		}
	}

	/** Místo na hraně, kam se bod přichytí. */
	static final class Prichyceni {
		/** Index hrany (z {@link #pridejHranu}). */
		final int hrana;
		/** Poloha na hraně 0–1 od jejího prvního uzlu. */
		final double t;
		final int lat;
		final int lon;
		/** Vzdálenost bodu od hrany v metrech. */
		final double vzdalenost;

		Prichyceni(final int hrana, final double t, final int lat, final int lon, final double vzdalenost) {
			this.hrana = hrana;
			this.t = t;
			this.lat = lat;
			this.lon = lon;
			this.vzdalenost = vzdalenost;
		}
	}

	int pocetKomponent() {
		return otevrena.length;
	}

	/** Komponenta zasahuje za okraj obdélníku; uzavřená leží celá uvnitř a nikam dál nevede. */
	boolean isOtevrena(final int k) {
		return otevrena[k];
	}

	/**
	 * Pro každou komponentu nejbližší místo k bodu. Prvek pole je null, když komponenta nemá hranu.
	 */
	Prichyceni[] nejblizsiVKomponentach(final int la, final int lo) {
		final Prichyceni[] vysledek = new Prichyceni[pocetKomponent()];
		for (int i = 0; i < hran; i++) {
			final int u = hranaZ[i];
			final int v = hranaDo[i];
			final double ax = lon[u] * cosLat;
			final double ay = lat[u];
			final double bx = lon[v] * cosLat;
			final double by = lat[v];
			final double px = lo * cosLat;
			final double py = la;
			final double dx = bx - ax;
			final double dy = by - ay;
			final double d2 = dx * dx + dy * dy;
			double t = d2 == 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / d2;
			t = Math.max(0, Math.min(1, t));
			final double qx = ax + t * dx;
			final double qy = ay + t * dy;
			final double vzd = Math.sqrt((px - qx) * (px - qx) + (py - qy) * (py - qy)) * METRU_NA_MIKROSTUPEN;
			final int k = komponenta[u];
			if (vysledek[k] == null || vzd < vysledek[k].vzdalenost) {
				final int qla = (int) Math.round(lat[u] + t * (lat[v] - lat[u]));
				final int qlo = (int) Math.round(lon[u] + t * (lon[v] - lon[u]));
				vysledek[k] = new Prichyceni(i, t, qla, qlo, vzd);
			}
		}
		return vysledek;
	}

	/**
	 * Nejlevnější cesta mezi dvěma místy na hranách téže komponenty (A*).
	 *
	 * @return souřadnice cesty po dvojicích (lat, lon) od startu do cíle včetně obou míst
	 * @throws CancellationException
	 *             když se výpočet zruší
	 */
	int[] najdiCestu(final Prichyceni start, final Prichyceni cil, final BooleanSupplier zruseno) {
		if (start.hrana == cil.hrana) {
			return new int[] { start.lat, start.lon, cil.lat, cil.lon };
		}
		// Start a cíl jsou dva pomocné uzly za koncem pole uzlů.
		final int s = uzlu;
		final int c = uzlu + 1;
		final int n = uzlu + 2;
		final int[] la = Arrays.copyOf(lat, n);
		final int[] lo = Arrays.copyOf(lon, n);
		la[s] = start.lat;
		lo[s] = start.lon;
		la[c] = cil.lat;
		lo[c] = cil.lon;
		final int c1 = hranaZ[cil.hrana];
		final int c2 = hranaDo[cil.hrana];
		final float cilKoef = hranaKoef[cil.hrana];

		final double[] g = new double[n];
		Arrays.fill(g, Double.POSITIVE_INFINITY);
		final int[] pred = new int[n];
		Arrays.fill(pred, -1);
		final boolean[] hotovo = new boolean[n];
		final Halda halda = new Halda();

		g[s] = 0;
		final int s1 = hranaZ[start.hrana];
		final int s2 = hranaDo[start.hrana];
		final float startKoef = hranaKoef[start.hrana];
		uvolni(s, s1, startKoef * metry(la[s], lo[s], la[s1], lo[s1]), g, pred, halda, la, lo, c);
		uvolni(s, s2, startKoef * metry(la[s], lo[s], la[s2], lo[s2]), g, pred, halda, la, lo, c);
		hotovo[s] = true;
		int kroku = 0;
		while (!halda.prazdna()) {
			final int u = halda.vyjmi();
			if (hotovo[u]) {
				continue;
			}
			hotovo[u] = true;
			if (u == c) {
				break;
			}
			if ((++kroku & 0xfff) == 0 && zruseno.getAsBoolean()) {
				throw new CancellationException();
			}
			if (u == c1 || u == c2) {
				uvolni(u, c, cilKoef * metry(la[u], lo[u], la[c], lo[c]), g, pred, halda, la, lo, c);
			}
			for (int j = zacatek[u]; j < zacatek[u + 1]; j++) {
				final int v = sousede[j];
				if (!hotovo[v]) {
					uvolni(u, v, sousedKoef[j] * metry(u, v), g, pred, halda, la, lo, c);
				}
			}
		}
		if (pred[c] < 0) {
			return null;
		}
		int delka = 0;
		for (int u = c; u >= 0; u = pred[u]) {
			delka++;
		}
		final int[] vysledek = new int[delka * 2];
		int i = delka;
		for (int u = c; u >= 0; u = pred[u]) {
			i--;
			vysledek[2 * i] = la[u];
			vysledek[2 * i + 1] = lo[u];
		}
		return vysledek;
	}

	private void uvolni(final int u, final int v, final double cena, final double[] g, final int[] pred, final Halda halda, final int[] la, final int[] lo, final int c) {
		final double ng = g[u] + cena;
		if (ng < g[v]) {
			g[v] = ng;
			pred[v] = u;
			halda.vloz(v, ng + PeskyProfil.MIN_KOEFICIENT * metry(la[v], lo[v], la[c], lo[c]));
		}
	}

	/** Binární halda uzlů podle priority; starší záznamy téhož uzlu se přeskočí při vyjmutí. */
	private static final class Halda {
		private int velikost;
		private int[] uzly = new int[256];
		private double[] priority = new double[256];

		boolean prazdna() {
			return velikost == 0;
		}

		void vloz(final int uzel, final double priorita) {
			if (velikost == uzly.length) {
				uzly = Arrays.copyOf(uzly, velikost * 2);
				priority = Arrays.copyOf(priority, velikost * 2);
			}
			int i = velikost++;
			while (i > 0) {
				final int rodic = (i - 1) / 2;
				if (priority[rodic] <= priorita) {
					break;
				}
				uzly[i] = uzly[rodic];
				priority[i] = priority[rodic];
				i = rodic;
			}
			uzly[i] = uzel;
			priority[i] = priorita;
		}

		int vyjmi() {
			final int vysledek = uzly[0];
			final int posledni = uzly[--velikost];
			final double pp = priority[velikost];
			int i = 0;
			while (true) {
				int dite = 2 * i + 1;
				if (dite >= velikost) {
					break;
				}
				if (dite + 1 < velikost && priority[dite + 1] < priority[dite]) {
					dite++;
				}
				if (priority[dite] >= pp) {
					break;
				}
				uzly[i] = uzly[dite];
				priority[i] = priority[dite];
				i = dite;
			}
			uzly[i] = posledni;
			priority[i] = pp;
			return vysledek;
		}
	}
}
