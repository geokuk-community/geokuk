package cz.geokuk.plugins.mapy.pruvodce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

	private PlanPruvodce(final PruvodceVolba volba, final List<Polozka> polozky) {
		this.volba = volba;
		this.polozky = Collections.unmodifiableList(polozky);
	}

	/**
	 * Vrstva se u podkladu nabízí takto: trasy jen u online podkladu (offline mapa je má v sobě), vrstevnice a stínování jen u offline mapy, která je kreslí.
	 * Důvod, proč ne, je v {@link #duvodNedostupnosti}.
	 */
	public static boolean jeVrstvaDostupna(final PodkladPruvodce podklad, final Cast cast) {
		return duvodNedostupnosti(podklad, cast) == null;
	}

	/** Proč se vrstva u podkladu nenabízí, null když se nabízí. */
	public static String duvodNedostupnosti(final PodkladPruvodce podklad, final Cast cast) {
		switch (cast) {
		case TRASY:
			return podklad.isOffline() ? "Turistická offline mapa už trasy obsahuje." : null;
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
		return new PlanPruvodce(volba, polozky);
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
		return polozky.stream().noneMatch(p -> p.getCast() == Cast.MAPA);
	}
}
