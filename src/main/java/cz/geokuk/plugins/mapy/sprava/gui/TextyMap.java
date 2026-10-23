package cz.geokuk.plugins.mapy.sprava.gui;

import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva;

/**
 * Texty správy offline map, ze kterých berou panel, rada v mapě, dialogy i blok ve stavovém řádku. Slovník: mapa = stažená mapa země nebo regionu, vrstvy = trasy,
 * vrstevnice a stínování terénu.
 */
final class TextyMap {

	private TextyMap() {}

	// oddíly panelu
	static final String STAZENE_MAPY = "Stažené mapy";
	static final String ZADNA_MAPA = "Zatím nemáte žádnou staženou mapu.";
	static final String PRO_TOTO_MISTO = "Pro toto místo";
	static final String MAPY_KE_STAZENI = "Mapy ke stažení";
	static final String HLEDAT = "Hledat zemi nebo region…";
	static final String SBALIT_STAZENE = "Sbalit stažené mapy";
	static final String ROZBALIT_STAZENE = "Rozbalit stažené mapy";

	// hlavička a pata panelu
	static final String STAHNOUT_VRSTVY_KE_STAZENYM = "Stáhnout vrstvy ke všem staženým mapám (%s)";
	static final String STAHNOUT_VRSTVY_KE_STAZENYM_TIP = "Stáhne chybějící trasy, vrstevnice a stínování ke všem staženým mapám. Mapy samotné se nestahují.";
	static final String OTEVRIT_SLOZKU = "Otevřít složku map";
	static final String OBRYS = "Ukázat obrysy map";
	static final String DALSI_WEBY = "Mapy z jiných webů:";
	static final String ZDROJ = "Zdroj map:";

	// řádky map
	static final String DOKONCIT_STAZENI = "Dokončit stažení";
	static final String ZKUSIT_ZNOVU = "Zkusit znovu";
	static final String CELA_ZEME = "v mapě celé země";

	// vrstvy
	static final String TRASY = "Trasy";
	static final String VRSTEVNICE = "Vrstevnice";
	static final String STINOVANI_TERENU = "Stínování terénu";

	/** Název vrstvy v textech pro uživatele podle typu vrstvy z katalogu. */
	static String nazevVrstvy(final KatalogovaVrstva.Typ typ) {
		switch (typ) {
		case TRASY:
			return TRASY;
		case VRSTEVNICE:
			return VRSTEVNICE;
		case DEM:
		default:
			return STINOVANI_TERENU;
		}
	}

	/** Titulek potvrzení smazání vrstvy: „Smazat vrstvu trasy – Andorra“. */
	static String smazatVrstvu(final KatalogovaVrstva.Typ typ, final String mapa) {
		return "Smazat vrstvu " + nazevVrstvy(typ).toLowerCase(java.util.Locale.forLanguageTag("cs")) + " – " + mapa;
	}

	/** Upozornění na zemi staženou celou i po regionech; parametry: název země, seznam regionů. */
	static String zemeDvakrat(final String zeme, final String regiony) {
		return zeme + ": máte staženou celou zemi i regiony (" + regiony + "). Stačí jedno.";
	}

	// rada v mapě
	static final String RADA_NADPIS = "Pro toto místo nemáte offline mapu.";
	static final String RADA_SOUSEDNI = "V záběru je i:";
	static final String RADA_DOKONCIT = "Dokončit stažení mapy %s";
	static final String RADA_ZKUSIT_ZNOVU = "Zkusit znovu stáhnout mapu %s";
}
