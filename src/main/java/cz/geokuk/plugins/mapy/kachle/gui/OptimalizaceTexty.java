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

	/** Doporučení při startu. */
	static String doporuceni(final StavSouboruCache stav, final boolean maloMista, final long volne) {
		final String zaklad = String.format("Cache dlaždic je fragmentovaná (%s volného místa, %s).", procenta(stav), velikost(stav.velikost()));
		if (maloMista) {
			return zaklad + "\n" + String.format("Na Optimalizaci databáze chybí místo na disku: je potřeba asi %s volných, volných je %s. Až místo uvolníte, spusťte ji z menu Mapy.",
					velikost(OptimalizaceDatabaze.potrebneMisto(stav)), velikost(Math.max(0, volne)));
		}
		return zaklad + "\n" + String.format("Doporučujeme provést Optimalizaci databáze (trvá asi %s, potřeba %s volného místa).", minut(OptimalizaceDatabaze.odhadMinut(stav)),
				velikost(OptimalizaceDatabaze.potrebneMisto(stav)));
	}

	static String odpocet(final int sekund) {
		return "Program se mezitím normálně spouští, okno se za " + sekund + " s zavře bez optimalizace. Připomeneme to za týden.";
	}

	/** Přehled v dialogu před spuštěním. */
	static String prehled(final StavSouboruCache stav, final long volne) {
		return String.format("<html>Velikost cache dlaždic: %s<br>Volných stránek: %s<br>Po optimalizaci: asi %s<br>Volné místo na disku: %s<br>Potrvá asi %s.</html>",
				velikost(stav.velikost()), procenta(stav), velikost(stav.zivaData()), volne < 0 ? "neznámé" : velikost(volne), minut(OptimalizaceDatabaze.odhadMinut(stav)));
	}

	static String maloMista(final StavSouboruCache stav, final long volne) {
		return String.format("Na disku chybí místo: je potřeba asi %s, volných je %s. Uvolněte místo a spusťte optimalizaci znovu.", velikost(OptimalizaceDatabaze.potrebneMisto(stav)),
				velikost(Math.max(0, volne)));
	}

	static final String UVOD = "Optimalizace zmenší soubor s cache dlaždic a zrychlí čtení map. Po dobu optimalizace se mapy neukládají do cache, původní cache zůstane do konce beze změny.";
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
