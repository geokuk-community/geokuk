package cz.geokuk.plugins.mapy.kachle;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

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
}
