package cz.geokuk.plugins.mapy.kachle.data;

import java.io.File;
import java.util.Collections;
import java.util.List;

/** Přístup testů z jiných balíků k načítání uživatelských map. */
public final class UzivatelskeMapyPristup {

	public static List<String> nacti(final File soubor) {
		return UzivatelskeMapy.nacti(soubor);
	}

	public static void vycisti() {
		EKaType.setUzivatelske(Collections.emptyList());
	}

	private UzivatelskeMapyPristup() {}
}
