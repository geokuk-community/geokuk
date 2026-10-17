package cz.geokuk.plugins.mapy.pruvodce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.StavBunky;

/** Plán průvodce: co se stáhne (jen to, co ještě na disku není) a na co se zobrazení přepne. Bez Swingu. */
public final class PlanPruvodce {

	/** Jedna věc ke stažení: mapa nebo vrstva. */
	public static final class Polozka {
		private final Cast cast;
		private final String klic;
		private final String id;
		private final String nazev;
		private final long velikost;

		Polozka(final Cast cast, final String klic, final String id, final String nazev, final long velikost) {
			this.cast = cast;
			this.klic = klic;
			this.id = id;
			this.nazev = nazev;
			this.velikost = velikost;
		}

		public Cast getCast() {
			return cast;
		}

		public String getKlic() {
			return klic;
		}

		/** Id mapy nebo vrstvy z katalogu pro stažení. */
		public String getId() {
			return id;
		}

		/** Například „Česko – mapa“, „Česko – trasy“. */
		public String getNazev() {
			return nazev;
		}

		/** Velikost podle katalogu, 0 když není známá. */
		public long getVelikost() {
			return velikost;
		}

		public boolean isVrstva() {
			return cast != Cast.MAPA;
		}
	}

	private final PruvodceVolba volba;
	private final List<Polozka> polozky;
	private final boolean stahnoutVse;

	private PlanPruvodce(final PruvodceVolba volba, final List<Polozka> polozky, final boolean stahnoutVse) {
		this.volba = volba;
		this.polozky = Collections.unmodifiableList(polozky);
		this.stahnoutVse = stahnoutVse;
	}

	/**
	 * Vrstva se u podkladu nabízí takto: trasy u každého (offline Turistická kreslí značené trasy z vrstvy tras, ne ze základní mapy; u online podkladu jsou
	 * překryvem), vrstevnice a stínování jen u offline mapy, která je kreslí. Důvod, proč ne, je v {@link #duvodNedostupnosti}.
	 */
	public static boolean jeVrstvaDostupna(final PodkladPruvodce podklad, final Cast cast) {
		return duvodNedostupnosti(podklad, cast) == null;
	}

	/** Proč se vrstva u podkladu nenabízí, null když se nabízí. */
	public static String duvodNedostupnosti(final PodkladPruvodce podklad, final Cast cast) {
		switch (cast) {
		case TRASY:
			return null;
		case VRSTEVNICE:
		case STINOVANI:
			return podklad.isOffline() ? null : "Jen s offline mapou.";
		case MAPA:
		default:
			return "Mapa není vrstva.";
		}
	}

	/** Sestaví plán pro volbu: co z matice (stavy map a vrstev) ještě chybí. Stažené a novější verze se nestahují. */
	public static PlanPruvodce sestav(final PruvodceVolba volba, final List<MatriceMap.Radek> matice) {
		final List<Polozka> polozky = new ArrayList<>();
		for (final String klic : volba.getKlice()) {
			final MatriceMap.Radek radek = matice.stream().filter(r -> r.getKlic().equals(klic)).findFirst().orElse(null);
			if (radek == null) {
				continue;
			}
			if (volba.getPodklad().isOffline()) {
				pridej(polozky, radek, Cast.MAPA, "mapa");
			}
			for (final Cast c : new Cast[] { Cast.TRASY, Cast.VRSTEVNICE, Cast.STINOVANI }) {
				if (volba.getVrstvy().contains(c) && jeVrstvaDostupna(volba.getPodklad(), c)) {
					pridej(polozky, radek, c, nazevCasti(c));
				}
			}
		}
		return new PlanPruvodce(volba, polozky, false);
	}

	/**
	 * Plán „Stáhnout vše“ pro zemi: co z vybraných částí chybí nebo je zastaralé, v pořadí mapa, trasy, vrstevnice, stínování. Zobrazení se nepřepíná.
	 *
	 * @param celekKlic
	 *            klíč celé země, nebo null, když země má jen regiony; každá část se bere jen z celku, když ji celek má, jinak z regionů
	 * @param klice
	 *            klíče řádků země (celek a regiony)
	 */
	public static PlanPruvodce sestavVse(final String celekKlic, final List<String> klice, final Set<Cast> casti, final List<MatriceMap.Radek> matice) {
		final List<Polozka> polozky = new ArrayList<>();
		pridejVse(polozky, celekKlic, klice, casti, matice);
		return new PlanPruvodce(new PruvodceVolba(klice, PodkladPruvodce.TURISTICKA_OFFLINE, casti), serad(polozky), true);
	}

	/**
	 * Plán „Dostáhnout vrstvy“ ke všem staženým základním mapám: jen vrstvy z GitHubu (žádná mapa z mapsforge). U stažené celé země i vrstvy, které existují jen po
	 * regionech, a to bez základních map regionů; u staženého regionu jeho vrstvy.
	 */
	public static PlanPruvodce sestavDostahnout(final Set<Cast> casti, final List<MatriceMap.Radek> matice) {
		final Set<Cast> vrstvy = java.util.EnumSet.copyOf(casti);
		vrstvy.remove(Cast.MAPA);
		final List<Polozka> polozky = new ArrayList<>();
		final List<String> vsechnyKlice = new ArrayList<>();
		for (final MatriceMap.Radek r : matice) {
			if (!r.getBunka(Cast.MAPA).jeStazeno()) {
				continue;
			}
			final String idCelku = r.getMapa().getIdCelku();
			if (idCelku != null && matice.stream().anyMatch(x -> x.getKlic().equals(idCelku) && x.getBunka(Cast.MAPA).jeStazeno())) {
				continue;
			}
			final List<String> klice = new ArrayList<>();
			klice.add(r.getKlic());
			final boolean celek = r.getMapa().getSkupina() == null;
			if (celek) {
				matice.stream().filter(x -> r.getKlic().equals(x.getMapa().getIdCelku())).forEach(x -> klice.add(x.getKlic()));
			}
			pridejVse(polozky, celek ? r.getKlic() : null, klice, vrstvy, matice);
			klice.stream().filter(k -> !vsechnyKlice.contains(k)).forEach(vsechnyKlice::add);
		}
		final java.util.Map<String, Polozka> bezDuplicit = new java.util.LinkedHashMap<>();
		polozky.forEach(p -> bezDuplicit.putIfAbsent(p.getId(), p));
		return new PlanPruvodce(new PruvodceVolba(vsechnyKlice, PodkladPruvodce.TURISTICKA_OFFLINE, vrstvy), serad(new ArrayList<>(bezDuplicit.values())), true);
	}

	/** Fronta jde od nejmenších souborů po největší; položky se stejnou nebo neznámou velikostí zůstávají v původním pořadí (neznámá je nakonec). */
	static List<Polozka> serad(final List<Polozka> polozky) {
		final List<Polozka> v = new ArrayList<>(polozky);
		v.sort(java.util.Comparator.comparingLong(p -> p.getVelikost() > 0 ? p.getVelikost() : Long.MAX_VALUE));
		return v;
	}

	private static void pridejVse(final List<Polozka> polozky, final String celekKlic, final List<String> klice, final Set<Cast> casti, final List<MatriceMap.Radek> matice) {
		for (final Cast cast : new Cast[] { Cast.MAPA, Cast.TRASY, Cast.VRSTEVNICE, Cast.STINOVANI }) {
			if (!casti.contains(cast)) {
				continue;
			}
			final boolean celekMaCast = celekKlic != null && matice.stream().anyMatch(r -> r.getKlic().equals(celekKlic) && r.getBunka(cast).getStav() != StavBunky.NENI);
			for (final String klic : klice) {
				matice.stream().filter(r -> r.getKlic().equals(klic)).findFirst().ifPresent(r -> {
					// Celek pokrývá jen své regiony; řádek skupiny bez vazby na celek (Kanáry u Španělska) se bere samostatně.
					if (celekMaCast && !klic.equals(celekKlic) && celekKlic.equals(r.getMapa().getIdCelku())) {
						return;
					}
					final MatriceMap.Bunka b = r.getBunka(cast);
					if (b.jeKeStazeni()) {
						polozky.add(new Polozka(cast, r.getKlic(), b.getId(), r.getMapa().getNazev() + " – " + nazevCasti(cast), Math.max(0, b.getVelikost())));
					}
				});
			}
		}
	}

	/** Plán „Stáhnout vše“: nic se nepřepíná a stahují se i novější verze. */
	public boolean isStahnoutVse() {
		return stahnoutVse;
	}

	private static void pridej(final List<Polozka> polozky, final MatriceMap.Radek radek, final Cast cast, final String popis) {
		final MatriceMap.Bunka b = radek.getBunka(cast);
		if (b.getStav() == StavBunky.KE_STAZENI || b.getStav() == StavBunky.CHYBA) {
			polozky.add(new Polozka(cast, radek.getKlic(), b.getId(), radek.getMapa().getNazev() + " – " + popis, Math.max(0, b.getVelikost())));
		}
	}

	private static String nazevCasti(final Cast cast) {
		switch (cast) {
		case TRASY:
			return "trasy";
		case VRSTEVNICE:
			return "vrstevnice";
		case STINOVANI:
			return "stínování";
		case MAPA:
		default:
			return "mapa";
		}
	}

	public PruvodceVolba getVolba() {
		return volba;
	}

	/** Co se stáhne, v pořadí: po mapách v pořadí zemí, u každé mapa a pak vrstvy. */
	public List<Polozka> getPolozky() {
		return polozky;
	}

	/** Součet velikostí podle katalogu. */
	public long getCelkovaVelikost() {
		return polozky.stream().mapToLong(Polozka::getVelikost).sum();
	}

	/** Není co stahovat, všechno potřebné už je na disku (nebo se nic nevybralo). */
	public boolean jeHotovo() {
		return polozky.isEmpty();
	}

	/** Zobrazení se přepne hned: online podklad, nebo offline podklad, jehož mapy už jsou stažené. */
	public boolean prepnoutHned() {
		return !stahnoutVse && polozky.stream().noneMatch(p -> p.getCast() == Cast.MAPA);
	}
}
