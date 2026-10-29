package cz.geokuk.plugins.mapy.kachle.gui;

import java.util.Locale;

import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze;
import cz.geokuk.plugins.mapy.kachle.podklady.StaraData;
import cz.geokuk.plugins.mapy.kachle.podklady.StavSouboruCache;

/** Texty oken Optimalizace cache dlaždic. */
final class OptimalizaceTexty {

	private static final Locale CS = new Locale("cs");

	static final String NAZEV = "Optimalizace cache dlaždic";

	static String velikost(final long bajtu) {
		if (bajtu >= 1L << 30) {
			return String.format(CS, "%.1f GB", bajtu / (double) (1L << 30));
		}
		return Math.max(1, bajtu >> 20) + " MB";
	}

	static String procenta(final StavSouboruCache stav) {
		return Math.round(stav.podilVolnych() * 100) + " %";
	}

	static String minut(final int minut) {
		return minut == 1 ? "1 minutu" : minut >= 2 && minut <= 4 ? minut + " minuty" : minut + " minut";
	}

	static String starychDlazdic(final StaraData stare) {
		return "V cache je " + velikost(stare.bajtu()) + " dlaždic starých verzí tématu; optimalizace je odstraní a zmenší soubor.";
	}

	/** Doporučení při startu: co je v cache špatně a řádek s odhadem; zastaralé dlaždice se počítají mezi prázdné stránky. */
	static String doporuceni(final StavSouboruCache stav, final StaraData stare, final boolean maloMista, final long volne) {
		final StavSouboruCache po = stav.bezStarych(stare.bajtu());
		final StringBuilder sb = new StringBuilder();
		if (stav.podilVolnych() > OptimalizaceDatabaze.PRAH_VOLNYCH || stare.bajtu() == 0) {
			sb.append(String.format("Cache dlaždic je fragmentovaná: %s volného místa z %s.", procenta(stav), velikost(stav.velikost())));
		}
		if (stare.bajtu() > 0) {
			sb.append(sb.length() > 0 ? "\n" : "").append(starychDlazdic(stare));
		}
		if (maloMista) {
			return sb + "\n" + String.format("Na optimalizaci chybí místo na disku: potřeba asi %s, volných %s.", velikost(OptimalizaceDatabaze.potrebneMisto(po)),
					velikost(Math.max(0, volne)));
		}
		return sb + "\n" + String.format("Optimalizace potrvá asi %s a potřebuje %s volného místa.", minut(OptimalizaceDatabaze.odhadMinut(po)),
				velikost(OptimalizaceDatabaze.potrebneMisto(po)));
	}

	static String odpocet(final int sekund) {
		return "Zavře se za " + sekund + " s";
	}

	static final String ODPOCET_POZASTAVEN = "Odpočet pozastaven";

	/** Kdy připomeneme, když okno zmizí odpočtem, popř. volbou Spustit bez optimalizace. */
	static String pripomenuti(final boolean kriticky, final boolean vyprsel) {
		return "Připomeneme to " + (kriticky ? "zítra" : vyprsel ? "za 3 dny" : "za týden") + ".";
	}

	/** Řádky přehledu v dialogu před spuštěním: popisek a hodnota; {@code stare} je null, dokud se zastaralé dlaždice zjišťují. */
	static String[][] prehled(final StavSouboruCache stav, final StaraData stare, final long volne) {
		final StavSouboruCache po = stav.bezStarych(stare == null ? 0 : stare.bajtu());
		final java.util.List<String[]> radky = new java.util.ArrayList<>();
		radky.add(new String[] { "Velikost", velikost(stav.velikost()) });
		radky.add(new String[] { "Volných stránek", procenta(stav) });
		radky.add(new String[] { "Dlaždice starých verzí tématu", stare == null ? "zjišťuje se…" : velikost(stare.bajtu()) });
		radky.add(new String[] { "Po optimalizaci", "asi " + velikost(po.zivaData()) });
		radky.add(new String[] { "Volné místo na disku", volne < 0 ? "neznámé" : velikost(volne) });
		radky.add(new String[] { "Potrvá", "asi " + minut(OptimalizaceDatabaze.odhadMinut(po)) });
		return radky.toArray(new String[0][]);
	}

	static String maloMista(final StavSouboruCache stav, final long volne) {
		return String.format("Na disku chybí místo: potřeba asi %s, volných %s.", velikost(OptimalizaceDatabaze.potrebneMisto(stav)),
				velikost(Math.max(0, volne)));
	}

	/** Průběh kopie: kolik dlaždic je zpracováno, kolik MB zkopírováno a odstraněno a kolik zbývá času podle dosavadní rychlosti. */
	static String prubeh(final long hotovo, final long celkem, final long zkopirovano, final long vynechano, final long uplynulo) {
		final StringBuilder sb = new StringBuilder(String.format("Zpracováno %d z %d dlaždic, zkopírováno %s", hotovo, celkem, velikost(zkopirovano)));
		if (vynechano > 0) {
			sb.append(", odstraněno ").append(velikost(vynechano));
		}
		sb.append('.');
		if (hotovo > 0 && hotovo < celkem && uplynulo >= 5_000) {
			final long zbyva = uplynulo * (celkem - hotovo) / hotovo;
			sb.append(" Zbývá asi ").append(minut((int) Math.max(1, (zbyva + 59_999) / 60_000))).append('.');
		}
		return sb.toString();
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
