package cz.geokuk.plugins.mapy.pruvodce;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;

/** Země, ze kterých si uživatel v prvním kroku průvodce vybírá: Česko a Slovensko nahoře, další z katalogu podle abecedy. */
public final class NabidkaZemi {

	/** Země a klíče map, které se pro ni berou (celá země, nebo u země bez celku všechny její regiony). */
	public static final class Zeme {
		private final String nazev;
		private final List<String> klice;

		Zeme(final String nazev, final List<String> klice) {
			this.nazev = nazev;
			this.klice = Collections.unmodifiableList(klice);
		}

		public String getNazev() {
			return nazev;
		}

		public List<String> getKlice() {
			return klice;
		}
	}

	private static final List<String> NAHORE = java.util.Arrays.asList("europe/czech-republic", "europe/slovakia");

	/** Výchozí pohled prvního kroku: Česko a sousedé, z Německa jen Bavorsko a Sasko. */
	private static final List<String> OKOLI = java.util.Arrays.asList("europe/czech-republic", "europe/slovakia", "europe/austria", "europe/poland", "europe/germany/bayern", "europe/germany/sachsen");

	private NabidkaZemi() {}

	/** Regiony zemí, které mají i celek, jako samostatné položky „Německo – Bavorsko“; země bez celku se berou celé po regionech. */
	public static List<Zeme> regiony(final KatalogMap katalog) {
		final List<Zeme> v = new ArrayList<>();
		for (final KatalogovaMapa m : katalog.getMapy()) {
			if (m.getSkupina() != null && katalog.celek(m).isPresent()) {
				v.add(new Zeme(m.getSkupina() + " – " + m.getNazev(), new ArrayList<>(Collections.singletonList(m.getId()))));
			}
		}
		return v;
	}

	/** Výchozí pohled: Česko a okolí z nabídky všech zemí a regionů. */
	public static List<Zeme> okoli(final List<Zeme> vse) {
		final List<Zeme> v = new ArrayList<>();
		for (final String klic : OKOLI) {
			vse.stream().filter(z -> z.getKlice().size() == 1 && z.getKlice().get(0).equals(klic)).findFirst().ifPresent(v::add);
		}
		return v;
	}

	/** Název odpovídá hledanému textu; bez ohledu na velikost písmen a diakritiku, prázdný text vyhovuje všem. */
	public static boolean vyhovuje(final Zeme z, final String dotaz) {
		return bezDiakritiky(z.getNazev()).contains(bezDiakritiky(dotaz.trim()));
	}

	private static String bezDiakritiky(final String s) {
		return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	public static List<Zeme> sestav(final KatalogMap katalog) {
		final List<Zeme> zeme = new ArrayList<>();
		final List<String> nazvyBezCelku = new ArrayList<>();
		for (final KatalogovaMapa m : katalog.getMapy()) {
			if (m.getSkupina() == null) {
				zeme.add(new Zeme(m.getNazev(), new ArrayList<>(Collections.singletonList(m.getId()))));
			} else if (!katalog.celek(m).isPresent()) {
				nazvyBezCelku.add(m.getSkupina());
			}
		}
		for (final String skupina : new java.util.TreeSet<>(nazvyBezCelku)) {
			final List<String> regiony = new ArrayList<>();
			for (final KatalogovaMapa m : katalog.getMapy()) {
				if (skupina.equals(m.getSkupina()) && !katalog.celek(m).isPresent()) {
					regiony.add(m.getId());
				}
			}
			zeme.add(new Zeme(skupina, regiony));
		}
		final Collator cs = Collator.getInstance(new Locale("cs"));
		zeme.sort((a, b) -> {
			final int pa = NAHORE.indexOf(a.getKlice().get(0));
			final int pb = NAHORE.indexOf(b.getKlice().get(0));
			if (pa >= 0 || pb >= 0) {
				return pa < 0 ? 1 : pb < 0 ? -1 : Integer.compare(pa, pb);
			}
			return cs.compare(a.getNazev(), b.getNazev());
		});
		return zeme;
	}
}
