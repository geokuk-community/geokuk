package cz.geokuk.plugins.kesoid.importek;

import java.util.Arrays;
import java.util.Map;

/**
 * Klíče vazeb waypointů jednoho zdroje a otisk jeho obsahu. Dva zdroje se při načítání ovlivňují (duplicitní jméno, přídavný waypoint jednoho zdroje u keše z druhého,
 * waymark u geodetického bodu z jiného souboru), když sdílejí klíč. Klíč je hash textu s jmenným prostorem; kolize hashe jen přidá zbytečné společné čtení.
 */
final class KliceZdroje {

	static final KliceZdroje PRAZDNE = new KliceZdroje(new long[0], false, 0);

	/** Seřazené, bez opakování. */
	final long[] klice;
	/** Jména se generovala číslováním za běh, v jiném běhu by se pletla. */
	final boolean bezejmenne;
	/** Otisk všeho, co ze zdroje přišlo, v pořadí čtení. */
	final long otiskObsahu;

	private KliceZdroje(final long[] klice, final boolean bezejmenne, final long otiskObsahu) {
		this.klice = klice;
		this.bezejmenne = bezejmenne;
		this.otiskObsahu = otiskObsahu;
	}

	/** Klíč jména waypointu: jméno bez dvoupísmenného prefixu, FI1234 patří ke GC1234. */
	static long klicJmena(final String jmeno) {
		return hash(hash(ZACATEK, "n:"), jmeno, Math.min(2, jmeno.length()));
	}

	static long klic(final String text) {
		return hash(ZACATEK, text);
	}

	static KliceZdroje slouc(final Iterable<KliceZdroje> zdroje) {
		final Sberac s = new Sberac();
		for (final KliceZdroje k : zdroje) {
			for (final long klic : k.klice) {
				s.pridej(klic);
			}
			s.bezejmenne |= k.bezejmenne;
		}
		return s.hotovo();
	}

	/** Mají společný klíč. */
	boolean protina(final KliceZdroje jine) {
		int i = 0;
		int j = 0;
		while (i < klice.length && j < jine.klice.length) {
			if (klice[i] == jine.klice[j]) {
				return true;
			}
			if (klice[i] < jine.klice[j]) {
				i++;
			} else {
				j++;
			}
		}
		return false;
	}

	private static final long ZACATEK = 0xcbf29ce484222325L;
	private static final long NASOBEK = 0x100000001b3L;

	private static long hash(final long h, final String s) {
		return hash(h, s, 0);
	}

	private static long hash(long h, final String s, final int od) {
		if (s == null) {
			return (h ^ 0xff) * NASOBEK;
		}
		for (int i = od; i < s.length(); i++) {
			h = (h ^ s.charAt(i)) * NASOBEK;
		}
		return (h ^ 0xfe) * NASOBEK;
	}

	private static long hash(final long h, final long v) {
		return (h ^ v) * NASOBEK + (v >>> 32);
	}

	/** Sbírá klíče a otisk obsahu při čtení zdroje. */
	static final class Sberac {
		private long[] klice = new long[64];
		private int pocet;
		private boolean bezejmenne;
		private long obsah = ZACATEK;

		void pridej(final long klic) {
			if (pocet == klice.length) {
				klice = Arrays.copyOf(klice, pocet * 2);
			}
			klice[pocet++] = klic;
		}

		void bezejmenny() {
			bezejmenne = true;
		}

		/** Do otisku jde vše, co z waypointu může ovlivnit výsledek; hint načítaný až při zobrazení ne. */
		void obsah(final GpxWpt w) {
			long h = obsah;
			h = hash(h, w.name);
			h = hash(h, w.wgs == null ? 0 : Double.doubleToLongBits(w.wgs.lat));
			h = hash(h, w.wgs == null ? 0 : Double.doubleToLongBits(w.wgs.lon));
			h = hash(h, Double.doubleToLongBits(w.ele));
			h = hash(h, w.desc);
			h = hash(h, w.cmt);
			h = hash(h, w.sym);
			h = hash(h, w.time);
			h = hash(h, w.type);
			h = hash(h, w.explicitneUrcenoVlastnictvi ? 1 : 0);
			h = hash(h, w.link.href);
			h = hash(h, w.link.text);
			h = hash(h, w.link.type);
			final Gpxg g = w.gpxg;
			h = hash(h, g.elevation);
			h = hash(h, g.found);
			h = hash(h, g.flag);
			h = hash(h, g.hodnoceni);
			h = hash(h, g.hodnoceniPocet);
			h = hash(h, g.bestOf);
			h = hash(h, g.favorites);
			h = hash(h, g.znamka);
			h = hash(h, g.czkraj);
			h = hash(h, g.czokres);
			long tagy = 0; // součet nezávisí na pořadí v mapě
			for (final Map.Entry<String, String> e : g.userTags.entrySet()) {
				tagy += hash(hash(ZACATEK, e.getKey()), e.getValue());
			}
			h = hash(h, tagy);
			final Groundspeak s = w.groundspeak;
			if (s != null) {
				h = hash(h, s.name);
				h = hash(h, s.type);
				h = hash(h, s.ownerid);
				h = hash(h, s.owner);
				h = hash(h, s.placedBy);
				h = hash(h, (s.archived ? 1 : 0) + (s.availaible ? 2 : 0));
				h = hash(h, s.encodedHints);
				h = hash(h, s.state);
				h = hash(h, s.country);
				h = hash(h, s.terrain);
				h = hash(h, s.difficulty);
				h = hash(h, s.container);
				h = hash(h, s.shortDescription);
			}
			obsah = h;
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
			return new KliceZdroje(Arrays.copyOf(serazene, n), bezejmenne, obsah);
		}
	}
}
