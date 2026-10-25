package cz.geokuk.plugins.mapy.kachle.gui;

import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze;
import cz.geokuk.plugins.mapy.kachle.podklady.StavSouboruCache;

/** Texty oken Optimalizace databáze. */
final class OptimalizaceTexty {

	static final String NAZEV = "Optimalizace databáze";

	static String velikost(final long bajtu) {
		if (bajtu >= 1L << 30) {
			return String.format("%.1f GB", bajtu / (double) (1L << 30));
		}
		return Math.max(1, bajtu >> 20) + " MB";
	}

	static String procenta(final StavSouboruCache stav) {
		return Math.round(stav.podilVolnych() * 100) + " %";
	}

	static String minut(final int minut) {
		return minut == 1 ? "1 minutu" : minut >= 2 && minut <= 4 ? minut + " minuty" : minut + " minut";
	}

	/** Doporučení při startu: úvodní věta a řádek s odhadem. */
	static String doporuceni(final StavSouboruCache stav, final boolean maloMista, final long volne) {
		final String zaklad = String.format("Cache dlaždic je fragmentovaná: %s volného místa z %s.", procenta(stav), velikost(stav.velikost()));
		if (maloMista) {
			return zaklad + "\n" + String.format("Na optimalizaci chybí místo na disku: potřeba asi %s, volných %s.", velikost(OptimalizaceDatabaze.potrebneMisto(stav)),
					velikost(Math.max(0, volne)));
		}
		return zaklad + "\n" + String.format("Optimalizace potrvá asi %s a potřebuje %s volného místa.", minut(OptimalizaceDatabaze.odhadMinut(stav)),
				velikost(OptimalizaceDatabaze.potrebneMisto(stav)));
	}

	static String odpocet(final int sekund) {
		return "Zavře se za " + sekund + " s";
	}

	static final String ODPOCET_POZASTAVEN = "Odpočet pozastaven";

	/** Kdy připomeneme, když okno zmizí odpočtem, popř. volbou Spustit bez optimalizace. */
	static String pripomenuti(final boolean kriticky, final boolean vyprsel) {
		return "Připomeneme to " + (kriticky ? "zítra" : vyprsel ? "za 3 dny" : "za týden") + ".";
	}

	/** Řádky přehledu v dialogu před spuštěním: popisek a hodnota. */
	static String[][] prehled(final StavSouboruCache stav, final long volne) {
		return new String[][] { { "Velikost", velikost(stav.velikost()) }, { "Volných stránek", procenta(stav) }, { "Po optimalizaci", "asi " + velikost(stav.zivaData()) },
				{ "Volné místo na disku", volne < 0 ? "neznámé" : velikost(volne) }, { "Potrvá", "asi " + minut(OptimalizaceDatabaze.odhadMinut(stav)) } };
	}

	static String maloMista(final StavSouboruCache stav, final long volne) {
		return String.format("Na disku chybí místo: potřeba asi %s, volných %s.", velikost(OptimalizaceDatabaze.potrebneMisto(stav)),
				velikost(Math.max(0, volne)));
	}

	static final String UVOD = "Optimalizace zmenší soubor s cache dlaždic a zrychlí čtení map.";
	static final String ZRUSENO = "Optimalizace byla zrušena, cache zůstala beze změny.";
	static final String NELZE = "Cache dlaždic není co optimalizovat: soubor neexistuje nebo ho nejde přečíst.";

	static String hotovo(final long pred, final long po) {
		return "Hotovo. Velikost cache dlaždic klesla z " + velikost(pred) + " na " + velikost(po) + ".";
	}

	static String chyba(final String zprava) {
		return "Optimalizace se nepovedla, cache zůstala beze změny.\n" + zprava;
	}

	private OptimalizaceTexty() {}
}
