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

	public static void vycisti() {
		EKaType.setUzivatelske(Collections.emptyList());
	}

	private UzivatelskeMapyPristup() {}
}
