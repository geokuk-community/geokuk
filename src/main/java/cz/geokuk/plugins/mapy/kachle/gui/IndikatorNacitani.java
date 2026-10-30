package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Rectangle;

/**
 * Kdy a kde se ukáže nenápadný proužek načítání: až když se na dlaždice čeká déle než {@link #PRODLEVA_MS}, a zmizí hned po načtení poslední.
 */
final class IndikatorNacitani {

	static final long PRODLEVA_MS = 300;
	static final long PERIODA_MS = 1400;
	static final int VYSKA = 3;
	/** Podíl šířky mapy, který zabírá pohyblivý proužek. */
	private static final double PODIL_PROUZKU = 0.25;

	private long cekaOdMs = -1;

	/** Zpracuje stav v daném čase; vrací, zda se má proužek kreslit. */
	boolean aktualizuj(final long nyniMs, final int cekajicich) {
		if (cekajicich <= 0) {
			cekaOdMs = -1;
			return false;
		}
		if (cekaOdMs < 0) {
			cekaOdMs = nyniMs;
		}
		return nyniMs - cekaOdMs >= PRODLEVA_MS;
	}

	boolean cekaSe() {
		return cekaOdMs >= 0;
	}

	/** Proužek při plynulém posunu zleva doprava, který zajíždí za okraje; výška jen {@link #VYSKA} bodů. */
	static Rectangle prouzek(final long nyniMs, final int sirka) {
		final int w = Math.max(1, (int) (sirka * PODIL_PROUZKU));
		final double faze = (nyniMs % PERIODA_MS) / (double) PERIODA_MS;
		final int x = (int) (-w + faze * (sirka + w));
		return new Rectangle(Math.max(0, x), 0, Math.max(0, Math.min(x + w, sirka) - Math.max(0, x)), VYSKA);
	}
}
