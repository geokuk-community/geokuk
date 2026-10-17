package cz.geokuk.plugins.mapy.pruvodce;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce.Polozka;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.StavBunky;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;

/**
 * Pořadí stahování z plánu průvodce. Stahovač zvládá jen jednu věc najednou, proto se další položka pustí, až předchozí skončí. Sama nic nestahuje:
 * volající po každé změně stavu zavolá {@link #dalsi} a případně vrácenou položku spustí.
 */
public final class FrontaPruvodce {

	public enum Akce {
		/** Něco se stahuje, počkat na další změnu stavu. */
		CEKEJ,
		/** Spustit stažení {@link Krok#getPolozka()}. */
		STAHUJ,
		/** Vše z plánu je na disku. */
		HOTOVO,
		/** Zastaveno: položka selhala, nebo uživatel stahování zrušil; další se nestahují. */
		ZASTAVENO
	}

	/** Co má volající udělat. */
	public static final class Krok {
		private final Akce akce;
		private final Polozka polozka;
		private final boolean chyba;

		Krok(final Akce akce, final Polozka polozka) {
			this(akce, polozka, false);
		}

		Krok(final Akce akce, final Polozka polozka, final boolean chyba) {
			this.akce = akce;
			this.polozka = polozka;
			this.chyba = chyba;
		}

		/** Zastaveno kvůli chybě stahování (ne kvůli zrušení). */
		public boolean isChyba() {
			return chyba;
		}

		public Akce getAkce() {
			return akce;
		}

		/** Položka ke spuštění (STAHUJ), u zastavení ta, která selhala, jinak null. */
		public Polozka getPolozka() {
			return polozka;
		}
	}

	private final PlanPruvodce plan;
	private final Set<String> spustene = new HashSet<>();
	/** Kolikrát po spuštění položka nebyla ani ve stahování, ani hotová; po spuštění může stav chvíli zaostávat. */
	private final Map<String, Integer> zpozdeni = new HashMap<>();
	private boolean zastaveno;

	public FrontaPruvodce(final PlanPruvodce plan) {
		this.plan = plan;
	}

	/** Položku se nepodařilo spustit (třeba se zrovna stahuje jiná): příště se zkusí znovu. */
	public void nespusteno(final Polozka polozka) {
		spustene.remove(polozka.getId());
	}

	/** Uživatel stahování zrušil: dál se nic nespouští. */
	public void zrus() {
		zastaveno = true;
	}

	/**
	 * Co dál: spustit další položku, čekat, nebo skončit. Volá se opakovaně; po STAHUJ ji volající zavolá znovu, dokud nepřijde CEKEJ.
	 *
	 * @param stavy
	 *            stav buňky matice podle id mapy nebo vrstvy (STAHUJE, STAZENO, KE_STAZENI, CHYBA…)
	 */
	public Krok dalsi(final Function<String, StavBunky> stavy) {
		return dalsi(stavy, true);
	}

	/** @param pocitejZpozdeni zda se má spuštěné položce, která ještě není vidět jako stahovaná, započítat další zpoždění (jednou za krok, ne za každé pokračování) */
	Krok dalsi(final Function<String, StavBunky> stavy, final boolean pocitejZpozdeni) {
		if (zastaveno) {
			return new Krok(Akce.ZASTAVENO, null);
		}
		boolean mapaBezi = false;
		int vrstevBezi = 0;
		for (final Polozka p : plan.getPolozky()) {
			final StavBunky s = stavy.apply(p.getId());
			if (hotovo(s) || !(s == StavBunky.STAHUJE || spustene.contains(p.getId()))) {
				continue;
			}
			if (p.isVrstva()) {
				vrstevBezi++;
			} else {
				mapaBezi = true;
			}
		}
		Polozka dalsi = null;
		boolean neco = false;
		for (final Polozka p : plan.getPolozky()) {
			final StavBunky s = stavy.apply(p.getId());
			if (hotovo(s)) {
				continue;
			}
			neco = true;
			if (s == StavBunky.STAHUJE) {
				continue;
			}
			if (!spustene.contains(p.getId())) {
				if (dalsi == null && (p.isVrstva() ? vrstevBezi < SpravaMapModel.SOUBEZNE_VRSTVY : !mapaBezi)) {
					dalsi = p;
				}
				continue;
			}
			if (s == StavBunky.CHYBA) {
				zastaveno = true;
				return new Krok(Akce.ZASTAVENO, p, true);
			}
			if (pocitejZpozdeni && zpozdeni.merge(p.getId(), 1, Integer::sum) >= 2) {
				zastaveno = true;
				return new Krok(Akce.ZASTAVENO, p);
			}
		}
		if (dalsi != null) {
			spustene.add(dalsi.getId());
			return new Krok(Akce.STAHUJ, dalsi);
		}
		return new Krok(neco ? Akce.CEKEJ : Akce.HOTOVO, null);
	}

	private boolean hotovo(final StavBunky s) {
		return s == StavBunky.STAZENO || s == StavBunky.NOVEJSI && !plan.isStahnoutVse();
	}
}
