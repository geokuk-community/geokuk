package cz.geokuk.plugins.kesoid.importek;

import java.util.Arrays;

/**
 * Klíče jmen waypointů jednoho zdroje. Dva zdroje se při načítání ovlivňují (duplicitní jméno, přídavný waypoint jednoho zdroje u keše z druhého), když sdílejí klíč. Klíč je
 * hash jména bez dvoupísmenného prefixu (FI1234 patří ke GC1234); kolize hashe jen přidá zbytečné společné čtení. Zdroj se jmény mimo GC (CGP, waymarky, přídavné
 * waypointy, fotky) je zvláštní; CGP a waymarky se párují podle textu, takže se dva zvláštní zdroje ovlivňují vždy.
 */
final class KliceZdroje {

	static final KliceZdroje PRAZDNE = new KliceZdroje(new long[0], false, false);

	final long[] klice;
	final boolean zvlastni;
	/** Jména se generovala číslováním za běh, v jiném běhu by se pletla. */
	final boolean bezejmenne;

	private KliceZdroje(final long[] klice, final boolean zvlastni, final boolean bezejmenne) {
		this.klice = klice;
		this.zvlastni = zvlastni;
		this.bezejmenne = bezejmenne;
	}

	static long klic(final String jmeno) {
		long h = 0xcbf29ce484222325L;
		for (int i = Math.min(2, jmeno.length()); i < jmeno.length(); i++) {
			h = (h ^ jmeno.charAt(i)) * 0x100000001b3L;
		}
		return h;
	}

	static KliceZdroje slouc(final Iterable<KliceZdroje> zdroje) {
		int pocet = 0;
		boolean zvlastni = false;
		boolean bezejmenne = false;
		for (final KliceZdroje k : zdroje) {
			pocet += k.klice.length;
			zvlastni |= k.zvlastni;
			bezejmenne |= k.bezejmenne;
		}
		final long[] vse = new long[pocet];
		int i = 0;
		for (final KliceZdroje k : zdroje) {
			System.arraycopy(k.klice, 0, vse, i, k.klice.length);
			i += k.klice.length;
		}
		return new KliceZdroje(vse, zvlastni, bezejmenne);
	}

	/** Sbírá klíče při čtení zdroje. */
	static final class Sberac {
		private long[] klice = new long[64];
		private int pocet;
		private boolean zvlastni;
		private boolean bezejmenne;

		void pridej(final String jmeno, final boolean generovane) {
			if (pocet == klice.length) {
				klice = Arrays.copyOf(klice, pocet * 2);
			}
			klice[pocet++] = klic(jmeno);
			zvlastni |= !jmeno.startsWith("GC");
			bezejmenne |= generovane;
		}

		KliceZdroje hotovo() {
			final long[] serazene = Arrays.copyOf(klice, pocet);
			Arrays.sort(serazene);
			int n = 0;
			for (int i = 0; i < serazene.length; i++) {
				if (i == 0 || serazene[i] != serazene[i - 1]) {
					serazene[n++] = serazene[i];
				}
			}
			return new KliceZdroje(Arrays.copyOf(serazene, n), zvlastni, bezejmenne);
		}
	}
}
