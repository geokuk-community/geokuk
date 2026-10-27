package cz.geokuk.plugins.mapy.pruvodce;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.plugins.mapy.sprava.StavMapyProGui;

/** Tlačítko Stáhnout mapu a vrstvy: plán bez dialogu, potvrzení jen nad mezí průvodce. */
public class StahnoutMapuAVrstvyTest {

	private static final String CZ = "europe/czech-republic";

	private static List<MatriceMap.Radek> matice() {
		return MatriceMap.sestav(KatalogMap.vestaveny(), k -> StavMapyProGui.stav(k, StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.NIC, 0, -1, null), null, null);
	}

	private static PruvodceKontext kontext(final List<MatriceMap.Radek> m, final int[] spusteno) {
		return new PruvodceKontext() {
			@Override
			public KatalogMap getKatalog() {
				return KatalogMap.vestaveny();
			}

			@Override
			public List<MatriceMap.Radek> matice() {
				return m;
			}

			@Override
			public long volneMisto() {
				return -1;
			}

			@Override
			public boolean stahni(final PlanPruvodce.Polozka polozka) {
				spusteno[0]++;
				return false;
			}

			@Override
			public void zrusStahovani() {}

			@Override
			public void prepni(final PruvodceVolba volba) {}
		};
	}

	@Test
	public void jednaMapaSeSpustiBezDotazu() {
		final int[] spusteno = { 0 };
		final PruvodceKontext k = kontext(matice(), spusteno);
		final PlanPruvodce plan = StahnoutMapuAVrstvy.plan(k, null, Collections.singletonList(CZ), PlanPruvodce.Rezim.STAHNOUT);
		assertFalse(plan.jeHotovo());
		assertFalse("nad mezí není", JPruvodceMap.potrebujePotvrzeni(plan));
		final boolean[] zeptal = { false };
		StahnoutMapuAVrstvy.spust(k, plan, p -> {
			zeptal[0] = true;
			return true;
		});
		assertFalse("bez dialogu", zeptal[0]);
	}

	@Test
	public void nadMeziSeZeptaAKdyzNeNeni() {
		final List<MatriceMap.Radek> m = matice();
		final List<String> klice = new java.util.ArrayList<>();
		m.stream().limit(8).forEach(r -> klice.add(r.getKlic()));
		final int[] spusteno = { 0 };
		final PruvodceKontext k = kontext(m, spusteno);
		final PlanPruvodce plan = PlanPruvodce.sestavVse(null, klice, EnumSet.of(MatriceMap.Cast.MAPA), m);
		assertTrue(JPruvodceMap.potrebujePotvrzeni(plan));
		final boolean[] zeptal = { false };
		assertFalse("zamítnuto", StahnoutMapuAVrstvy.spust(k, plan, p -> {
			zeptal[0] = true;
			return false;
		}));
		assertTrue(zeptal[0]);
		assertEquals(0, spusteno[0]);
	}

	@Test
	public void dotazNerikaNulaMap() {
		final List<MatriceMap.Radek> m = matice();
		final PlanPruvodce jenVrstvy = PlanPruvodce.sestavVse(CZ, Collections.singletonList(CZ), EnumSet.of(MatriceMap.Cast.TRASY), m);
		assertTrue(StahnoutMapuAVrstvy.textDotazu(jenVrstvy), StahnoutMapuAVrstvy.textDotazu(jenVrstvy).startsWith("Opravdu stáhnout vrstvy, "));
		final PlanPruvodce mapa = PlanPruvodce.sestavVse(CZ, Collections.singletonList(CZ), EnumSet.of(MatriceMap.Cast.MAPA), m);
		assertTrue(StahnoutMapuAVrstvy.textDotazu(mapa), StahnoutMapuAVrstvy.textDotazu(mapa).startsWith("Opravdu stáhnout 1 mapu, "));
	}

	@Test
	public void dialogMaTitulekSpravceOfflineMap() {
		assertEquals("Správce offline map", StahnoutMapuAVrstvy.TITULEK);
	}
}
