package cz.geokuk.plugins.mapy.pruvodce;

import org.junit.Assert;
import org.junit.Test;

/** Kdy se průvodce otevře sám a jak se hlásí chyba. */
public class PruvodceMapActionTest {

	@Test
	public void samSeOtevreJenPriPrvniInstalaci() {
		Assert.assertTrue(PruvodceMapAction.lzeOtevritSam(false, false, false, true));
		Assert.assertFalse("automatizace", PruvodceMapAction.lzeOtevritSam(true, false, false, true));
		Assert.assertFalse("bez displeje", PruvodceMapAction.lzeOtevritSam(false, true, false, true));
		Assert.assertFalse("už jednou", PruvodceMapAction.lzeOtevritSam(false, false, true, true));
		Assert.assertFalse("existující instalace", PruvodceMapAction.lzeOtevritSam(false, false, false, false));
	}

	@Test
	public void hlaskaOChybeSPricinou() {
		Assert.assertEquals("Stahování se zastavilo u „Česko – mapa“: Na disku není místo.\nZkuste to znovu přes Mapy > Průvodce nastavením map.", SpousteniPruvodce.textChyby("Česko – mapa", "Na disku není místo."));
		Assert.assertEquals("Stahování se zastavilo u „Česko – mapa“.\nZkuste to znovu přes Mapy > Průvodce nastavením map.", SpousteniPruvodce.textChyby("Česko – mapa", null));
	}
}
