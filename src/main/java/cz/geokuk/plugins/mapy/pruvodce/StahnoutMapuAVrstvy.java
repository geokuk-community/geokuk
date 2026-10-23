package cz.geokuk.plugins.mapy.pruvodce;

import java.awt.Component;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.JOptionPane;

import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;

/** Tlačítko „Stáhnout mapu a vrstvy“ u řádku: plán pro jeden řádek (u celé země i s regiony) spuštěný hned, potvrzení jen nad mezí průvodce. */
public final class StahnoutMapuAVrstvy {

	private StahnoutMapuAVrstvy() {}

	/** Plán řádku: mapa a všechny vrstvy včetně stínování, v režimu podle stavu řádku. */
	public static PlanPruvodce plan(final PruvodceKontext kontext, final String celekKlic, final List<String> klice, final PlanPruvodce.Rezim rezim) {
		return PlanPruvodce.sestavVse(celekKlic, klice, EnumSet.allOf(Cast.class), kontext.matice(), rezim);
	}

	/** Spustí plán hned; nad 5 map nebo 10 GB se předtím zeptá. Vrací, jestli se stahování spustilo. */
	public static boolean spust(final PruvodceKontext kontext, final PlanPruvodce plan, final Predicate<PlanPruvodce> potvrzeni) {
		if (plan.jeHotovo()) {
			return false;
		}
		if (JPruvodceMap.potrebujePotvrzeni(plan) && !potvrzeni.test(plan)) {
			return false;
		}
		SpousteniPruvodce.spust(kontext, plan);
		return true;
	}

	/** Dialog Opravdu stáhnout N map, X GB? */
	public static Predicate<PlanPruvodce> dialog(final Component rodic) {
		return plan -> {
			final long map = plan.getPolozky().stream().filter(p -> p.getCast() == Cast.MAPA).count();
			return JOptionPane.showConfirmDialog(rodic, "Opravdu stáhnout " + map + " " + (map == 1 ? "mapu" : map < 5 ? "mapy" : "map") + ", "
					+ JPruvodceMap.velikost(plan.getCelkovaVelikost()) + "?", "Offline mapy", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
		};
	}
}
