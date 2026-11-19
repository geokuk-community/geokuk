package cz.geokuk.plugins.challenge;

import java.util.Arrays;

/** Polohy hledatelných keší chybějících políček D/T matice, ze kterých se bez nového průchodu daty spočítá, kolik jich leží ve výřezu mapy. Neměnná. */
final class PolohyHledatelnych {

	private final double[][] lat;
	private final double[][] lon;

	private PolohyHledatelnych(final double[][] lat, final double[][] lon) {
		this.lat = lat;
		this.lon = lon;
	}

	/** Sběr poloh při průchodu daty; políčka se rozlišují pořadím obtížnost × 9 + terén. */
	static final class Sber {
		private final double[][] lat = new double[VypocetMatice.PULEK * VypocetMatice.PULEK][];
		private final double[][] lon = new double[VypocetMatice.PULEK * VypocetMatice.PULEK][];
		private final int[] pocet = new int[VypocetMatice.PULEK * VypocetMatice.PULEK];

		void pridej(final int policko, final double zemepisnaSirka, final double zemepisnaDelka) {
			int n = pocet[policko];
			if (lat[policko] == null) {
				lat[policko] = new double[16];
				lon[policko] = new double[16];
			} else if (n == lat[policko].length) {
				lat[policko] = Arrays.copyOf(lat[policko], n * 2);
				lon[policko] = Arrays.copyOf(lon[policko], n * 2);
			}
			lat[policko][n] = zemepisnaSirka;
			lon[policko][n] = zemepisnaDelka;
			pocet[policko] = ++n;
		}

		/** Ponechá jen polohy políček, která v matici chybí; ostatní se zahodí. */
		PolohyHledatelnych jenChybejici(final boolean[] chybi) {
			final double[][] a = new double[lat.length][];
			final double[][] o = new double[lat.length][];
			for (int i = 0; i < a.length; i++) {
				if (chybi[i] && pocet[i] > 0) {
					a[i] = Arrays.copyOf(lat[i], pocet[i]);
					o[i] = Arrays.copyOf(lon[i], pocet[i]);
				}
			}
			return new PolohyHledatelnych(a, o);
		}
	}

	/**
	 * Kolik hledatelných keší každého políčka leží ve výřezu (hranice včetně). Při {@code minLon > maxLon} výřez přechází přes poledník 180°.
	 *
	 * @return počty v pořadí obtížnost × 9 + terén
	 */
	int[] vOkne(final double minLat, final double maxLat, final double minLon, final double maxLon) {
		final int[] vysledek = new int[lat.length];
		final boolean pres180 = minLon > maxLon;
		for (int i = 0; i < lat.length; i++) {
			final double[] a = lat[i];
			if (a == null) {
				continue;
			}
			final double[] o = lon[i];
			int n = 0;
			for (int j = 0; j < a.length; j++) {
				if (a[j] >= minLat && a[j] <= maxLat && (pres180 ? o[j] >= minLon || o[j] <= maxLon : o[j] >= minLon && o[j] <= maxLon)) {
					n++;
				}
			}
			vysledek[i] = n;
		}
		return vysledek;
	}
}
