package cz.geokuk.plugins.mapy.kachle;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.mapy.kachle.podklady.TemaOfflineMapy;

/** Atribuce dat offline mapy podle otevřených souborů. */
public class AtribuceOfflineMapyTest {

	@Test
	public void jenPrehledovaMapa() {
		Assert.assertEquals("Made with Natural Earth", KachleModel.atribuceDat(Collections.singletonList("prehled-svet-ne.map")));
	}

	@Test
	public void prehledovaMapaSMapamiOsm() {
		Assert.assertEquals("© přispěvatelé OpenStreetMap, Made with Natural Earth", KachleModel.atribuceDat(Arrays.asList("czech_republic.map", "prehled-svet-ne.map")));
	}

	@Test
	public void bezPrehledoveMapy() {
		Assert.assertEquals("© přispěvatelé OpenStreetMap", KachleModel.atribuceDat(Collections.singletonList("czech_republic.map")));
		Assert.assertEquals("© přispěvatelé OpenStreetMap", KachleModel.atribuceDat(Collections.emptyList()));
	}

	@Test
	public void neplatneTemaSeVAtribuciOznaci() {
		final TemaOfflineMapy tema = TemaOfflineMapy.zeSouboru(new File("moje-tema.xml"), null);
		Assert.assertEquals("© přispěvatelé OpenStreetMap, téma moje-tema", KachleModel.atribuce("© přispěvatelé OpenStreetMap", tema, null));
		Assert.assertEquals("© přispěvatelé OpenStreetMap, téma moje-tema (nenačteno)", KachleModel.atribuce("© přispěvatelé OpenStreetMap", tema, "chybí soubor"));
	}
}
