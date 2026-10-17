package cz.geokuk.plugins.mapy.pruvodce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import cz.geokuk.plugins.mapy.pruvodce.FrontaPruvodce.Akce;
import cz.geokuk.plugins.mapy.pruvodce.FrontaPruvodce.Krok;
import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce.Polozka;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.StavBunky;

/**
 * Průběh průvodce po „Stáhnout a přepnout“: pouští položky plánu po jedné a po dokončení přepne zobrazení. Zobrazení se přepne hned, když netřeba čekat
 * na mapu; jinak až se mapa stáhne. Volá se opakovaně (časovačem), sama nečeká.
 */
public final class BehPruvodce {

	public enum Stav {
		BEZI, HOTOVO, ZASTAVENO
	}

	private final PruvodceKontext kontext;
	private final PlanPruvodce plan;
	private final FrontaPruvodce fronta;
	private final Consumer<Stav> poZmene;
	private boolean prepnuto;
	private Stav stav = Stav.BEZI;
	private List<Selhana> selhane = Collections.emptyList();

	public BehPruvodce(final PruvodceKontext kontext, final PlanPruvodce plan, final Consumer<Stav> poZmene) {
		this.kontext = kontext;
		this.plan = plan;
		this.fronta = new FrontaPruvodce(plan);
		this.poZmene = poZmene;
	}

	/** Začátek: když netřeba čekat na mapu, přepne zobrazení hned. */
	public void spust() {
		if (plan.prepnoutHned()) {
			prepni();
		}
		krok();
	}

	/** Jeden krok fronty; volat po každé změně stavu stahování nebo v pravidelných odstupech. */
	public void krok() {
		for (int i = 0; i < SpravaMapModel.SOUBEZNE_VRSTVY + 1 && stav == Stav.BEZI; i++) {
			if (!krokFronty(i == 0)) {
				break;
			}
		}
	}

	/** Jeden krok fronty; vrací, jestli se spustila další položka (a má smysl zkusit ještě jednu). */
	private boolean krokFronty(final boolean prvni) {
		final java.util.Map<String, StavBunky> stavy = stavy();
		final Krok k = fronta.dalsi(id -> stavy.getOrDefault(id, StavBunky.NENI), prvni);
		boolean spusteno = false;
		switch (k.getAkce()) {
		case STAHUJ:
			if (kontext.stahni(k.getPolozka())) {
				spusteno = true;
			} else {
				// Stahovač je zrovna zaneprázdněný (třeba stahování z popupu Mapy): zkusí se při dalším kroku.
				fronta.nespusteno(k.getPolozka());
			}
			break;
		case HOTOVO:
			// Když selhala některá mapa, zobrazení se nepřepíná na prázdnou offline mapu.
			if (mapyJsou()) {
				prepni();
			}
			selhane = sestavSelhane();
			konec(Stav.HOTOVO);
			break;
		case ZASTAVENO:
			konec(Stav.ZASTAVENO);
			break;
		case CEKEJ:
		default:
			break;
		}
		// Mapa už je stažená a zbývají vrstvy: zobrazení se přepne, jakmile mapa je (hned se nečeká na vrstvy).
		if (!prepnuto && stav == Stav.BEZI && mapyJsou()) {
			prepni();
		}
		return spusteno;
	}

	/** Uživatel stahování zrušil. */
	public void zrus() {
		if (stav == Stav.BEZI) {
			fronta.zrus();
			kontext.zrusStahovani();
			konec(Stav.ZASTAVENO);
		}
	}

	/** Kolik položek plán obsahuje. */
	public int getPocetPolozek() {
		return plan.getPolozky().size();
	}

	public Stav getStav() {
		return stav;
	}

	/** Položka, která po spuštění skončila chybou, a text chyby (null, když ho program nehlásí). */
	public static final class Selhana {
		private final Polozka polozka;
		private final String duvod;

		Selhana(final Polozka polozka, final String duvod) {
			this.polozka = polozka;
			this.duvod = duvod;
		}

		public Polozka getPolozka() {
			return polozka;
		}

		public String getDuvod() {
			return duvod;
		}
	}

	/** Co se nepodařilo stáhnout, když dávka skončila ({@link Stav#HOTOVO}); jinak prázdné. */
	public List<Selhana> getSelhane() {
		return selhane;
	}

	/** Zkusí znovu jen to, co selhalo; průvodce běží dál ({@link Stav#BEZI}). Volající musí znovu spustit časovač. */
	public void zkusZnovu() {
		if (stav == Stav.HOTOVO && !selhane.isEmpty()) {
			fronta.znovuSelhane();
			selhane = Collections.emptyList();
			stav = Stav.BEZI;
			krok();
		}
	}

	private List<Selhana> sestavSelhane() {
		final List<Polozka> polozky = fronta.getSelhane();
		final List<Selhana> v = new ArrayList<>();
		if (polozky.isEmpty()) {
			return v;
		}
		final java.util.Map<String, String> chyby = new java.util.HashMap<>();
		for (final MatriceMap.Radek r : kontext.matice()) {
			for (final MatriceMap.Cast c : MatriceMap.Cast.values()) {
				final MatriceMap.Bunka b = r.getBunka(c);
				if (b.getId() != null && b.getChyba() != null) {
					chyby.putIfAbsent(b.getId(), b.getChyba());
				}
			}
		}
		for (final Polozka p : polozky) {
			v.add(new Selhana(p, chyby.get(p.getId())));
		}
		return v;
	}

	private void konec(final Stav novy) {
		stav = novy;
		poZmene.accept(novy);
	}

	private void prepni() {
		if (!prepnuto && !plan.isStahnoutVse()) {
			prepnuto = true;
			kontext.prepni(plan.getVolba());
		}
	}

	/** Všechny mapy z plánu jsou stažené (nebo se žádné nestahují). */
	private boolean mapyJsou() {
		final java.util.Map<String, StavBunky> stavy = stavy();
		return plan.getPolozky().stream().filter(p -> p.getCast() == MatriceMap.Cast.MAPA).allMatch(p -> stavy.get(p.getId()) == StavBunky.STAZENO);
	}

	/** Stavy buněk matice podle id položky; matice se sestaví jednou pro celý krok, ne pro každou položku. */
	private java.util.Map<String, StavBunky> stavy() {
		final java.util.Map<String, StavBunky> stavy = new java.util.HashMap<>();
		for (final MatriceMap.Radek r : kontext.matice()) {
			for (final MatriceMap.Cast c : MatriceMap.Cast.values()) {
				final MatriceMap.Bunka b = r.getBunka(c);
				if (b.getId() != null) {
					stavy.putIfAbsent(b.getId(), b.getStav());
				}
			}
		}
		return stavy;
	}
}
