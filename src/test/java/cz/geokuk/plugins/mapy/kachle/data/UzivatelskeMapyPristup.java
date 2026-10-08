package cz.geokuk.plugins.mapy.kachle.data;

import java.io.File;
import java.util.Collections;
import java.util.List;

/** Přístup testů z jiných balíků k načítání uživatelských map. */
public final class UzivatelskeMapyPristup {

	/** Načte mapy ze složky se soubory {@code *.mapa}. */
	public static List<String> nacti(final File slozka) {
		return UzivatelskeMapy.nactiSlozku(slozka);
	}

	/** Mapa ze vzoru URL, která pokrývá jen část světa; mimo ni server odpovídá danými kódy HTTP. */
	public static EKaType sOmezenymUzemim(final String id, final String vzorUrl, final Integer... kodyMimoUzemi) {
		return EKaType.omezeneUzemi(EKaType.uzivatelska(id, id, id, 0, 18, 18, 0, null, Collections.emptyMap(), "", false, new UzivatelskyUrlBuilder(vzorUrl)), kodyMimoUzemi);
	}

	/** Jako {@link #sOmezenymUzemim}, kódy ale platí jen pro dlaždice mimo rozsah v EPSG:3857 (minX, minY, maxX, maxY). */
	public static EKaType sRozsahem(final String id, final String vzorUrl, final double[] rozsah, final Integer... kodyMimoUzemi) {
		return EKaType.omezeneUzemi(EKaType.uzivatelska(id, id, id, 0, 18, 18, 0, null, Collections.emptyMap(), "", false, new UzivatelskyUrlBuilder(vzorUrl)), rozsah, kodyMimoUzemi);
	}

	public static void vycisti() {
		EKaType.setUzivatelske(Collections.emptyList());
	}

	private UzivatelskeMapyPristup() {}
}
