package cz.geokuk.plugins.mapy.sprava;

import java.io.IOException;

/** Katalog vrstev a stavy vrstev pro testy GUI (konstruktory stavu jsou jen pro balíček správy map). */
public final class VrstvyProGui {

	public static final String SHA = KatalogVrstevTest.SHA;

	private VrstvyProGui() {}

	/** Trasy a vrstevnice pro Česko, stínování pro Česko jako dvě dlaždice. */
	public static KatalogVrstev katalogCesko() {
		try {
			return KatalogVrstev.nacti(KatalogVrstevTest.katalog(
					KatalogVrstevTest.vrstva("routes/europe/czech-republic", "routes", "europe/czech-republic", "map",
							KatalogVrstevTest.soubor("czech_republic_trasy.map", "v1/czech_republic_trasy.map", 25_000_000, SHA), ", \"dataDate\": \"2026-10-13\""),
					KatalogVrstevTest.vrstva("contours/europe/czech-republic", "contours", "europe/czech-republic", "map",
							KatalogVrstevTest.soubor("czech_republic_vrstevnice.map", "v1/czech_republic_vrstevnice.map", 266_000_000, SHA), ""),
					KatalogVrstevTest.vrstva("dem/europe/czech-republic", "dem", "europe/czech-republic", "hgt-zip",
							KatalogVrstevTest.soubor("czech_republic_hgt.zip", "v1/czech_republic_hgt.zip", 80_000_000, SHA), "")),
					KatalogMap.vestaveny());
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public static StavVrstvy stav(final KatalogovaVrstva v, final StavVrstvy.Mistni mistni, final StavMapy.Faze faze, final long stazeno, final String chyba) {
		return StavVrstvy.novy(v).sMistni(mistni, stazeno, stazeno > 0 && mistni == StavVrstvy.Mistni.NEMAME).sFazi(faze, chyba);
	}
}
