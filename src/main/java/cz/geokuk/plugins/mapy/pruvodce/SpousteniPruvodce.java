package cz.geokuk.plugins.mapy.pruvodce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.Timer;

import cz.geokuk.plugins.mapy.pruvodce.BehPruvodce.Selhana;
import cz.geokuk.plugins.mapy.pruvodce.BehPruvodce.Stav;

/**
 * Spouští stahování z průvodce na pozadí: časovač po sekundě popostrčí frontu. Naráz běží jedno stahování z průvodce. Selhané položky dávku nezastaví;
 * po jejím konci je k dispozici souhrn a jde je zkusit znovu.
 */
public final class SpousteniPruvodce {

	private static BehPruvodce aktualni;
	private static Timer casovac;
	private static Runnable poZmene = () -> {};

	private SpousteniPruvodce() {}

	/** Právě běží stahování z průvodce. */
	public static boolean bezi() {
		return aktualni != null && aktualni.getStav() == Stav.BEZI;
	}

	/** Zruší běžící stahování z průvodce, když nějaké běží. */
	public static void zrus() {
		if (bezi()) {
			aktualni.zrus();
		}
	}

	/** Co se zavolá, když se změní souhrn dávky (skončila, spustilo se opakování, souhrn se zavřel). */
	public static void setPoZmene(final Runnable poZmene) {
		SpousteniPruvodce.poZmene = poZmene;
	}

	/** Souhrn skončené dávky s chybami: první řádek „Staženo N, nepodařilo se M“, pak jeden řádek na selhanou položku; prázdný, když není co hlásit. */
	public static List<String> souhrn() {
		if (aktualni == null || aktualni.getStav() != Stav.HOTOVO || aktualni.getSelhane().isEmpty()) {
			return Collections.emptyList();
		}
		return radkySouhrnu(aktualni.getPocetPolozek(), aktualni.getSelhane());
	}

	static List<String> radkySouhrnu(final int celkem, final List<Selhana> selhane) {
		final List<String> radky = new ArrayList<>();
		radky.add("Staženo " + (celkem - selhane.size()) + ", nepodařilo se " + selhane.size() + ":");
		for (final Selhana s : selhane) {
			radky.add(s.getPolozka().getNazev() + (s.getDuvod() == null || s.getDuvod().isEmpty() ? "" : " – " + s.getDuvod()));
		}
		return radky;
	}

	/** Zkusí znovu jen selhané položky skončené dávky. */
	public static void zkusZnovu() {
		if (aktualni != null && aktualni.getStav() == Stav.HOTOVO && !aktualni.getSelhane().isEmpty()) {
			aktualni.zkusZnovu();
			if (aktualni.getStav() == Stav.BEZI) {
				spustCasovac(aktualni);
			}
			poZmene.run();
		}
	}

	/** Zavře souhrn dávky; selhané položky zůstávají označené chybou v popupu Mapy. */
	public static void zavriSouhrn() {
		if (aktualni != null && aktualni.getStav() == Stav.HOTOVO) {
			aktualni = null;
			poZmene.run();
		}
	}

	/** Začne plnit plán; musí se volat z EDT. */
	public static void spust(final PruvodceKontext kontext, final PlanPruvodce plan) {
		final BehPruvodce beh = new BehPruvodce(kontext, plan, stav -> {});
		aktualni = beh;
		beh.spust();
		if (beh.getStav() != Stav.BEZI) {
			poZmene.run();
			return;
		}
		spustCasovac(beh);
	}

	private static void spustCasovac(final BehPruvodce beh) {
		if (casovac != null) {
			casovac.stop();
		}
		casovac = new Timer(1000, null);
		casovac.addActionListener(e -> {
			beh.krok();
			if (beh.getStav() != Stav.BEZI) {
				casovac.stop();
				poZmene.run();
			}
		});
		casovac.start();
	}
}
