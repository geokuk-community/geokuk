package cz.geokuk.plugins.mapy.pruvodce;

import java.util.function.Consumer;

import cz.geokuk.plugins.mapy.pruvodce.FrontaPruvodce.Akce;
import cz.geokuk.plugins.mapy.pruvodce.FrontaPruvodce.Krok;
import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce.Polozka;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
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
		if (stav != Stav.BEZI) {
			return;
		}
		final Krok k = fronta.dalsi(this::stavPodleId);
		switch (k.getAkce()) {
		case STAHUJ:
			kontext.stahni(k.getPolozka());
			break;
		case HOTOVO:
			prepni();
			konec(Stav.HOTOVO);
			break;
		case ZASTAVENO:
			selhala = k.getPolozka();
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

	private void konec(final Stav novy) {
		stav = novy;
		poZmene.accept(novy);
	}

	private void prepni() {
		if (!prepnuto) {
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
