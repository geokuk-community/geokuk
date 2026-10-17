package cz.geokuk.plugins.mapy.pruvodce;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import cz.geokuk.plugins.mapy.pruvodce.FrontaPruvodce.Akce;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.StavBunky;
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.plugins.mapy.sprava.StavMapyProGui;
import cz.geokuk.plugins.mapy.sprava.StavVrstvy;
import cz.geokuk.plugins.mapy.sprava.VrstvyProGui;

/** Plán „Stáhnout vše“: bere chybějící i zastaralé části země, nic nepřepíná a hotové vynechá. */
public class PlanStahnoutVseTest {

	private static final String CZ = "europe/czech-republic";
	private static final EnumSet<Cast> VSE = EnumSet.allOf(Cast.class);

	private static List<MatriceMap.Radek> matice(final StavMapy.Mistni mistni, final StavMapy.Server server) {
		return MatriceMap.sestav(KatalogMap.vestaveny(), k -> StavMapyProGui.stav(k, mistni, server, StavMapy.Faze.NIC, 0, -1, null), VrstvyProGui.katalogCesko(), StavVrstvy::novy);
	}

	private static List<Cast> casti(final PlanPruvodce plan) {
		return plan.getPolozky().stream().map(PlanPruvodce.Polozka::getCast).collect(Collectors.toList());
	}

	@Test
	public void chybejiciCastiVPoradiMapaTrasyVrstevnice() {
		final PlanPruvodce plan = PlanPruvodce.sestavVse(CZ, Collections.singletonList(CZ), VSE, matice(StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY));
		assertTrue(plan.isStahnoutVse());
		assertFalse("zobrazení se nepřepíná", plan.prepnoutHned());
		final List<Cast> c = casti(plan);
		assertEquals(Cast.MAPA, c.get(0));
		assertTrue(c.indexOf(Cast.TRASY) > 0);
		assertTrue(c.indexOf(Cast.VRSTEVNICE) > c.indexOf(Cast.TRASY));
	}

	@Test
	public void odskrtnutaCastSeNestahuje() {
		final PlanPruvodce plan = PlanPruvodce.sestavVse(CZ, Collections.singletonList(CZ), EnumSet.of(Cast.TRASY), matice(StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY));
		assertEquals(Collections.singletonList(Cast.TRASY), casti(plan));
	}

	@Test
	public void stazenaMapaSeVynechaANovejsiSeAktualizuje() {
		final PlanPruvodce plan = PlanPruvodce.sestavVse(CZ, Collections.singletonList(CZ), VSE, matice(StavMapy.Mistni.MAME, StavMapy.Server.NOVEJSI));
		assertEquals("novější mapa se aktualizuje", Cast.MAPA, casti(plan).get(0));
		final FrontaPruvodce f = new FrontaPruvodce(plan);
		final FrontaPruvodce.Krok k = f.dalsi(id -> StavBunky.NOVEJSI);
		assertEquals("fronta novější nepřeskočí", Akce.STAHUJ, k.getAkce());
	}

	@Test
	public void hotovoKdyzJeVseStazene() {
		final PlanPruvodce plan = PlanPruvodce.sestavVse(CZ, Collections.singletonList(CZ), EnumSet.of(Cast.MAPA), matice(StavMapy.Mistni.MAME, StavMapy.Server.AKTUALNI));
		assertTrue(plan.jeHotovo());
	}

	@Test
	public void mapyRegionuSeBeruJenBezCelku() {
		final List<MatriceMap.Radek> m = matice(StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY);
		final String druhy = m.stream().map(MatriceMap.Radek::getKlic).filter(k -> !k.equals(CZ)).findFirst().get();
		final PlanPruvodce sCelkem = PlanPruvodce.sestavVse(CZ, Arrays.asList(CZ, druhy), EnumSet.of(Cast.MAPA), m);
		assertEquals("s celkem jen mapa celku", Collections.singletonList(CZ), sCelkem.getPolozky().stream().map(PlanPruvodce.Polozka::getKlic).collect(Collectors.toList()));
		final PlanPruvodce bezCelku = PlanPruvodce.sestavVse(null, Arrays.asList(CZ, druhy), EnumSet.of(Cast.MAPA), m);
		assertEquals("bez celku mapy všech řádků", 2, bezCelku.getPolozky().size());
	}
}
