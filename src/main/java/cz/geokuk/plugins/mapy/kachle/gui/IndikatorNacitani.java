package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Rectangle;

/**
 * Kdy a kde se ukáže nenápadné kolečko načítání: až když se na dlaždice čeká déle než {@link #PRODLEVA_MS}; objeví se a po načtení poslední dlaždice zmizí
 * plynule.
 */
final class IndikatorNacitani {

	static final long PRODLEVA_MS = 300;
	/** Doba jednoho otočení. */
	static final long PERIODA_MS = 1000;
	static final long OBJEVENI_MS = 150;
	static final long ZMIZENI_MS = 250;
	/** Průměr kolečka a jeho odstup od pravého a horního okraje mapy v bodech. */
	static final int PRUMER = 22;
	static final int ODSTUP = 10;
	/** Úhel viditelného oblouku. */
	static final int OBLOUK_STUPNU = 270;

	private long cekaOdMs = -1;
	private long posledniMs = -1;
	private double pruhlednost;

	/** Zpracuje stav v daném čase; vrací, zda se má kolečko kreslit (i když už mizí). */
	boolean aktualizuj(final long nyniMs, final int cekajicich) {
		final boolean ukaz;
		if (cekajicich <= 0) {
			cekaOdMs = -1;
			ukaz = false;
		} else {
			if (cekaOdMs < 0) {
				cekaOdMs = nyniMs;
			}
			ukaz = nyniMs - cekaOdMs >= PRODLEVA_MS;
		}
		final long uplynulo = posledniMs < 0 ? 0 : Math.max(0, nyniMs - posledniMs);
		posledniMs = nyniMs;
		if (ukaz) {
			pruhlednost = Math.min(1, pruhlednost + (double) uplynulo / OBJEVENI_MS);
		} else {
			pruhlednost = Math.max(0, pruhlednost - (double) uplynulo / ZMIZENI_MS);
		}
		if (!beziAnimace()) {
			posledniMs = -1;
		}
		return pruhlednost > 0;
	}

	/** Na dlaždice se čeká, nebo kolečko ještě mizí; jinak není co animovat a časovač má stát. */
	boolean beziAnimace() {
		return cekaOdMs >= 0 || pruhlednost > 0;
	}

	/** Průhlednost kolečka od 0 (nevidět) do 1 (plně). */
	double getPruhlednost() {
		return pruhlednost;
	}

	/** Úhel začátku oblouku ve stupních, plynule se otáčí po směru hodinových ručiček. */
	static double uhel(final long nyniMs) {
		return (nyniMs % PERIODA_MS) * 360.0 / PERIODA_MS;
	}

	/** Čtverec kolečka vpravo nahoře v mapě širší než {@code sirka}. */
	static Rectangle ramecek(final int sirka) {
		return new Rectangle(sirka - ODSTUP - PRUMER, ODSTUP, PRUMER, PRUMER);
	}
}
