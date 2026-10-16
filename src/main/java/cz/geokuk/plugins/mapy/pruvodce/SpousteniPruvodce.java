package cz.geokuk.plugins.mapy.pruvodce;

import java.awt.Component;

import javax.swing.JOptionPane;
import javax.swing.Timer;

import cz.geokuk.plugins.mapy.pruvodce.BehPruvodce.Stav;

/** Spouští stahování z průvodce na pozadí: časovač po sekundě popostrčí frontu, na konci nebo při chybě to ohlásí. Naráz běží jedno stahování z průvodce. */
public final class SpousteniPruvodce {

	private static BehPruvodce aktualni;

	private SpousteniPruvodce() {}

	static String textChyby(final String polozka, final String duvod) {
		return "Stahování se zastavilo u „" + polozka + "“" + (duvod == null || duvod.isEmpty() ? "." : ": " + duvod) + "\nZkuste to znovu přes Mapy > Průvodce nastavením map.";
	}

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

	/** Začne plnit plán; musí se volat z EDT. */
	public static void spust(final PruvodceKontext kontext, final PlanPruvodce plan, final Component rodic) {
		final BehPruvodce beh = new BehPruvodce(kontext, plan, stav -> {});
		aktualni = beh;
		beh.spust();
		if (beh.getStav() != Stav.BEZI) {
			return;
		}
		final Timer casovac = new Timer(1000, null);
		casovac.addActionListener(e -> {
			beh.krok();
			if (beh.getStav() != Stav.BEZI) {
				casovac.stop();
				if (beh.getSelhala() != null) {
					JOptionPane.showMessageDialog(rodic, textChyby(beh.getSelhala().getNazev(), beh.getDuvod()), "Nastavení map", JOptionPane.WARNING_MESSAGE);
				}
			}
		});
		casovac.start();
	}
}
