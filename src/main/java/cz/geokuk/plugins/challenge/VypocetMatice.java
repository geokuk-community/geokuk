package cz.geokuk.plugins.challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Výpočet challenge D/T matice: 81 kombinací obtížnosti a terénu po půlkách od 1 do 5. */
public final class VypocetMatice {

	static final int PULEK = 9;

	private VypocetMatice() {}

	/** Prvky {@code kese} nesmí být null. */
	public static VysledekPolicek spocti(final PravidlaTypu pravidla, final Iterable<? extends VstupDT> kese) {
		Objects.requireNonNull(pravidla, "pravidla");
		Objects.requireNonNull(kese, "kese");
		final boolean[][] nalezeno = new boolean[PULEK][PULEK];
		final List<List<VstupDT>> hledatelne = new ArrayList<>();
		for (int i = 0; i < PULEK * PULEK; i++) {
			hledatelne.add(new ArrayList<VstupDT>());
		}
		for (final VstupDT k : kese) {
			Objects.requireNonNull(k, "keš");
			if (!jePlatne(k.obtiznost()) || !jePlatne(k.teren()) || !pravidla.lzePocitat(k)) {
				continue;
			}
			if (PravidlaTypu.platnyNalez(k)) {
				nalezeno[k.obtiznost()][k.teren()] = true;
			} else if (PravidlaTypu.hledatelna(k)) {
				final List<VstupDT> v = hledatelne.get(k.obtiznost() * PULEK + k.teren());
				if (v.size() < pravidla.maxKandidatu()) {
					v.add(k);
				}
			}
		}
		final List<String> chybejici = new ArrayList<>();
		final List<KandidatPolicka> kandidati = new ArrayList<>();
		for (int d = 0; d < PULEK; d++) {
			for (int t = 0; t < PULEK; t++) {
				if (nalezeno[d][t]) {
					continue;
				}
				final String stitek = stitek(d, t);
				chybejici.add(stitek);
				for (final VstupDT k : hledatelne.get(d * PULEK + t)) {
					kandidati.add(new KandidatPolicka(k.id(), k.lat(), k.lon(), stitek, stitek, null));
				}
			}
		}
		return new VysledekPolicek(PULEK * PULEK, chybejici, kandidati);
	}

	/** Štítek políčka z pořadí po půlkách, například „D4/T1“ nebo „D1,5/T2,5“. */
	public static String stitek(final int obtiznost, final int teren) {
		return "D" + hodnota(obtiznost) + "/T" + hodnota(teren);
	}

	private static String hodnota(final int pulky) {
		return pulky % 2 == 0 ? String.valueOf(1 + pulky / 2) : (1 + pulky / 2) + ",5";
	}

	private static boolean jePlatne(final int pulky) {
		return pulky >= 0 && pulky < PULEK;
	}
}
