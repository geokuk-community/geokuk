package cz.geokuk.plugins.mapy.sprava.gui;

import java.util.HashMap;
import java.util.Map;

/** Odhad, za jak dlouho se mapa dostáhne, z rychlosti od prvního pozorování. */
final class OdhadStahovani {

	/** Dřív se odhad neukazuje, rychlost by skákala. */
	static final long NEJKRATSI_POZOROVANI_MS = 3000;
	static final long NEJMENSI_PRIRUSTEK = 64 * 1024;

	private static final class Vzorek {
		final long ms;
		final long bajtu;

		Vzorek(final long ms, final long bajtu) {
			this.ms = ms;
			this.bajtu = bajtu;
		}
	}

	private final Map<String, Vzorek> prvni = new HashMap<>();

	/** Zapíše stav stahování mapy; vrátí zbývající čas v sekundách, nebo -1, když se zatím odhadnout nedá. */
	long zbyva(final String id, final long stazeno, final long celkem, final long ms) {
		Vzorek v = prvni.computeIfAbsent(id, k -> new Vzorek(ms, stazeno));
		if (stazeno < v.bajtu) {
			// Stahování začalo znovu od začátku.
			v = new Vzorek(ms, stazeno);
			prvni.put(id, v);
		}
		final long uplynulo = ms - v.ms;
		final long prirustek = stazeno - v.bajtu;
		if (celkem <= 0 || uplynulo < NEJKRATSI_POZOROVANI_MS || prirustek < NEJMENSI_PRIRUSTEK) {
			return -1;
		}
		final double bajtuZaSekundu = prirustek * 1000.0 / uplynulo;
		return Math.max(0, Math.round((celkem - stazeno) / bajtuZaSekundu));
	}

	/** Mapy, které se nestahují, se přestanou sledovat; další stahování začne odhad znovu. */
	void zapomenKrome(final java.util.Set<String> stahovane) {
		prvni.keySet().retainAll(stahovane);
	}

	/** „asi 40 s“, „asi 3 min“, „asi 1 h 5 min“. */
	static String text(final long sekund) {
		if (sekund < 60) {
			return "asi " + Math.max(5, (sekund + 4) / 5 * 5) + " s";
		}
		final long minut = (sekund + 30) / 60;
		if (minut < 60) {
			return "asi " + minut + " min";
		}
		return "asi " + minut / 60 + " h" + (minut % 60 == 0 ? "" : " " + minut % 60 + " min");
	}
}
