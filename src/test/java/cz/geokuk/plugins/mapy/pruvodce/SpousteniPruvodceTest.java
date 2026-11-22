package cz.geokuk.plugins.mapy.pruvodce;

import java.util.Collections;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.plugins.mapy.sprava.StavMapyProGui;

/** Spuštěná dávka se hned ohlásí, aby popup Mapy vypnul tlačítka dalších dávek ještě před prvním krokem časovače. */
public class SpousteniPruvodceTest {

	@Test
	public void spustenaDavkaSeHnedOhlasi() throws Exception {
		final List<MatriceMap.Radek> matice = MatriceMap.sestav(KatalogMap.vestaveny(),
				k -> StavMapyProGui.stav(k, StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.NIC, 0, -1, null), null, null);
		final PruvodceKontext kontext = new PruvodceKontext() {
			@Override
			public KatalogMap getKatalog() {
				return KatalogMap.vestaveny();
			}

			@Override
			public List<MatriceMap.Radek> matice() {
				return matice;
			}

			@Override
			public long volneMisto() {
				return -1;
			}

			@Override
			public boolean stahni(final PlanPruvodce.Polozka polozka) {
				// Stahovač je zaneprázdněný: dávka čeká.
				return false;
			}

			@Override
			public void zrusStahovani() {}

			@Override
			public void prepni(final PruvodceVolba volba) {}
		};
		final PlanPruvodce plan = StahnoutMapuAVrstvy.plan(kontext, null, Collections.singletonList("europe/czech-republic"), PlanPruvodce.Rezim.STAHNOUT);
		final int[] ohlaseno = { 0 };
		final boolean[] beziPriOhlaseni = { false };
		SwingUtilities.invokeAndWait(() -> {
			SpousteniPruvodce.setPoZmene(() -> {
				ohlaseno[0]++;
				beziPriOhlaseni[0] = SpousteniPruvodce.bezi();
			});
			try {
				SpousteniPruvodce.spust(kontext, plan);
				Assert.assertTrue(SpousteniPruvodce.bezi());
				Assert.assertEquals("ohlášeno hned po spuštění", 1, ohlaseno[0]);
				Assert.assertTrue("při ohlášení už dávka běží", beziPriOhlaseni[0]);
			} finally {
				SpousteniPruvodce.zrus();
				SpousteniPruvodce.setPoZmene(() -> {});
			}
		});
	}
}
