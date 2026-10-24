package cz.geokuk.plugins.mapy.sprava;

import java.io.IOException;
import java.util.Locale;

/** Katalogy s jednou mapou a její vrstvou pro testy mimo balíček. */
public final class KatalogyProOfflineTest {

	private KatalogyProOfflineTest() {}

	/** Katalog s mapou {@code id}, jejíž obrys je obdélník {jih, západ, sever, východ}. */
	public static KatalogMap mapa(final String id, final double jih, final double zapad, final double sever, final double vychod) throws IOException {
		return KatalogMap.nacti("{\"zdroj\": \"test\", \"licence\": \"ODbL 1.0\", \"atribuce\": \"© OSM\", \"mapy\": \"https://example.org/maps/v5/\","
				+ " \"polygony\": \"https://example.org/maps/poly/\", \"polozky\": [{\"id\": \"" + id + "\", \"nazev\": \"Test\", \"velikost\": 100, \"obdelnik\": [" + jih + ", " + zapad
				+ ", " + sever + ", " + vychod + "]}]}", i -> null);
	}

	/** Katalog s vrstvou typu {@code typ} k mapě {@code region}; soubor ve složce vrstev má cestu „region.typ.map“. */
	public static KatalogVrstev vrstva(final KatalogMap mapy, final String region, final String typ) throws IOException {
		final String cesta = region + "." + typ.toLowerCase(Locale.ROOT) + ".map";
		return KatalogVrstev.nacti(KatalogVrstevTest.katalog(KatalogVrstevTest.vrstva(typ + "/" + region, typ, region, "map",
				KatalogVrstevTest.soubor("v.map", cesta, "v1/v.map", 10, KatalogVrstevTest.SHA), "")), mapy);
	}
}
