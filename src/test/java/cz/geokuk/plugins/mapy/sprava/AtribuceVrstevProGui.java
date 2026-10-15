package cz.geokuk.plugins.mapy.sprava;

import java.io.IOException;

/** Katalog vrstev s plnou atribucí a stav „máme“ pro testy GUI (konstruktory stavu jsou jen pro balíček správy map). */
public final class AtribuceVrstevProGui {

	private AtribuceVrstevProGui() {}

	/** Vrstva DEM pro Česko s plnou atribucí (povinná věta u Copernicu). */
	public static KatalogVrstev katalog(final String atribuceDlouha) {
		try {
			return KatalogVrstev.nacti(KatalogVrstevTest.katalog(KatalogVrstevTest.vrstva("dem/europe/czech-republic", "dem", "europe/czech-republic", "hgt-zip",
					KatalogVrstevTest.soubor("czech_republic_hgt.zip", "v1/czech_republic_hgt.zip", 80_000_000, KatalogVrstevTest.SHA),
					", \"atribuceDlouha\": \"" + atribuceDlouha + "\"")), KatalogMap.vestaveny());
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public static StavVrstvy mame(final KatalogovaVrstva v) {
		return StavVrstvy.novy(v).sMistni(StavVrstvy.Mistni.MAME, v.getVelikost(), false);
	}
}
