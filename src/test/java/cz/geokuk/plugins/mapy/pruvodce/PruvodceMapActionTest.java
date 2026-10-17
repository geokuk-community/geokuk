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
	public void souhrnDavkySChybami() {
		final PlanPruvodce.Polozka p = new PlanPruvodce.Polozka(cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast.MAPA, "x", "x", "Česko – mapa", 1);
		final PlanPruvodce.Polozka q = new PlanPruvodce.Polozka(cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast.TRASY, "x", "t", "Česko – trasy", 1);
		Assert.assertEquals(java.util.Arrays.asList("Staženo 3, nepodařilo se 2:", "Česko – mapa – Na disku není místo.", "Česko – trasy"),
				SpousteniPruvodce.radkySouhrnu(5, java.util.Arrays.asList(new BehPruvodce.Selhana(p, "Na disku není místo."), new BehPruvodce.Selhana(q, null))));
	}
}
