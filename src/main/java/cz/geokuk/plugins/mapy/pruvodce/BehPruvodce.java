package cz.geokuk.plugins.mapy.pruvodce;

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
	private Polozka selhala;
	private String duvod;

	public BehPruvodce(final PruvodceKontext kontext, final PlanPruvodce plan, final Consumer<Stav> poZmene) {
		this.kontext = kontext;
		this.plan = plan;
		this.fronta = new FrontaPruvodce(plan);
		this.poZmene = poZmene;
	}

	/** Začátek: když netřeba čekat na mapu, přepne zobrazení hned. */
	public void spust() {
		if (plan.getPolozky().size() > 1) {
			kontext.hromadnaDavka(true);
		}
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
		final Krok k = fronta.dalsi(this::stavPodleId, prvni);
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
			prepni();
			konec(Stav.HOTOVO);
			break;
		case ZASTAVENO:
			if (k.isChyba()) {
				selhala = k.getPolozka();
				duvod = chybaPodleId(k.getPolozka().getId());
			}
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

	public Stav getStav() {
		return stav;
	}

	/** Položka, kvůli které se průvodce zastavil, null když se nezastavil kvůli chybě. */
	public Polozka getSelhala() {
		return selhala;
	}

	/** Text chyby stahování, null když ho program nehlásí. */
	public String getDuvod() {
		return duvod;
	}

	private String chybaPodleId(final String id) {
		for (final MatriceMap.Radek r : kontext.matice()) {
			for (final MatriceMap.Cast c : MatriceMap.Cast.values()) {
				if (id.equals(r.getBunka(c).getId())) {
					return r.getBunka(c).getChyba();
				}
			}
		}
		return null;
	}

	private void konec(final Stav novy) {
		kontext.hromadnaDavka(false);
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
		return plan.getPolozky().stream().filter(p -> p.getCast() == MatriceMap.Cast.MAPA).allMatch(p -> stavPodleId(p.getId()) == StavBunky.STAZENO);
	}

	private StavBunky stavPodleId(final String id) {
		for (final MatriceMap.Radek r : kontext.matice()) {
			for (final MatriceMap.Cast c : MatriceMap.Cast.values()) {
				final MatriceMap.Bunka b = r.getBunka(c);
				if (id.equals(b.getId())) {
					return b.getStav();
				}
			}
		}
		return StavBunky.NENI;
	}
}
