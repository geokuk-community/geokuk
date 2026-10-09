package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.util.*;

import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Wpt;

/** Skupiny zdrojů, které se při načítání ovlivňují, a cache jejich načteného výsledku. */
final class SkupinyZdroju {

	/** Zdroje, které se vzájemně ovlivňují, z dokončeného načtení; otisky souborů, waypointy a počty po členech. */
	static final class Skupina {
		final Object kontext;
		final Map<File, String> otisky = new LinkedHashMap<>();
		final Map<File, List<Wpt>> wpty = new HashMap<>();
		final Map<File, int[]> pocty = new HashMap<>();
		/** Část bagu skupiny (index, kešoidy, nejvyšší hodnoty) z jejího posledního převzetí a pořadí členů, ve kterém se postavila; bez ní se postaví při převzetí. */
		KesBag.Cast cast;
		List<File> poradiCasti;
		KliceZdroje klice = KliceZdroje.PRAZDNE;

		Skupina(final Object kontext) {
			this.kontext = kontext;
		}
	}

	/** Co je o zdroji známo z jeho posledního úplného přečtení; drží se i pro vypnutý zdroj, aby šlo po zapnutí předem poznat, s čím se překrývá. */
	static final class ZnamyZdroj {
		final String otisk;
		final KliceZdroje klice;

		ZnamyZdroj(final String otisk, final KliceZdroje klice) {
			this.otisk = otisk;
			this.klice = klice;
		}
	}

	private SkupinyZdroju() {}

	/** Rozdělí jednotky do komponent: jednotky se sdíleným klíčem patří k sobě. Vrací pro každou jednotku označení komponenty. */
	static int[] komponenty(final List<KliceZdroje> jednotky) {
		final int n = jednotky.size();
		final int[] otec = new int[n];
		for (int i = 0; i < n; i++) {
			otec[i] = i;
		}
		if (n > 0xFFFF) {
			Arrays.fill(otec, 0); // nad 65 535 jednotek se nic nepřevezme
			return otec;
		}
		int pocet = 0;
		for (final KliceZdroje k : jednotky) {
			pocet += k.klice.length;
		}
		final long[] pary = new long[pocet];
		int p = 0;
		for (int i = 0; i < n; i++) {
			for (final long klic : jednotky.get(i).klice) {
				pary[p++] = klic & ~0xFFFFL | i; // 48 bitů hashe stačí, kolize jen zbytečně spojí
			}
		}
		Arrays.sort(pary);
		for (int i = 1; i < pary.length; i++) {
			if (pary[i] >>> 16 == pary[i - 1] >>> 16) {
				spoj(otec, (int) (pary[i] & 0xFFFF), (int) (pary[i - 1] & 0xFFFF));
			}
		}
		final int[] vysledek = new int[n];
		for (int i = 0; i < n; i++) {
			vysledek[i] = najdi(otec, i);
		}
		return vysledek;
	}

	private static int najdi(final int[] otec, int i) {
		while (otec[i] != i) {
			otec[i] = otec[otec[i]];
			i = otec[i];
		}
		return i;
	}

	private static void spoj(final int[] otec, final int a, final int b) {
		final int ra = najdi(otec, a);
		final int rb = najdi(otec, b);
		if (ra != rb) {
			otec[rb] = ra;
		}
	}
}
