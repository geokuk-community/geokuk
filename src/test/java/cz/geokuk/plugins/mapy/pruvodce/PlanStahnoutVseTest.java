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
	public void chybejiciCastiOdNejmensiho() {
		final PlanPruvodce plan = PlanPruvodce.sestavVse(CZ, Collections.singletonList(CZ), VSE, matice(StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY));
		assertTrue(plan.isStahnoutVse());
		assertFalse("zobrazení se nepřepíná", plan.prepnoutHned());
		final List<Cast> c = casti(plan);
		assertTrue(c.containsAll(Arrays.asList(Cast.MAPA, Cast.TRASY, Cast.VRSTEVNICE)));
		long predchozi = 0;
		for (final PlanPruvodce.Polozka p : plan.getPolozky()) {
			assertTrue("fronta jde od nejmenších souborů: " + p.getNazev(), p.getVelikost() >= predchozi);
			predchozi = p.getVelikost();
		}
		assertEquals("nejmenší jsou trasy, největší mapa", Cast.TRASY, c.get(0));
	}

	@Test
	public void odskrtnutaCastSeNestahuje() {
		final PlanPruvodce plan = PlanPruvodce.sestavVse(CZ, Collections.singletonList(CZ), EnumSet.of(Cast.TRASY), matice(StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY));
		assertEquals(Collections.singletonList(Cast.TRASY), casti(plan));
	}

	@Test
	public void stazenaMapaSeVynechaANovejsiSeAktualizuje() {
		final PlanPruvodce plan = PlanPruvodce.sestavVse(CZ, Collections.singletonList(CZ), VSE, matice(StavMapy.Mistni.MAME, StavMapy.Server.NOVEJSI));
		assertTrue("novější mapa se aktualizuje", casti(plan).contains(Cast.MAPA));
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
		final String de = "europe/germany";
		final String bayern = "europe/germany/bayern";
		final PlanPruvodce sCelkem = PlanPruvodce.sestavVse(de, Arrays.asList(de, bayern), EnumSet.of(Cast.MAPA), m);
		assertEquals("s celkem jen mapa celku", Collections.singletonList(de), sCelkem.getPolozky().stream().map(PlanPruvodce.Polozka::getKlic).collect(Collectors.toList()));
		final PlanPruvodce bezCelku = PlanPruvodce.sestavVse(null, Arrays.asList(de, bayern), EnumSet.of(Cast.MAPA), m);
		assertEquals("bez celku mapy všech řádků", 2, bezCelku.getPolozky().size());
	}

	@Test
	public void vrstvuMaCelekNeboRegionyNikdyOboje() {
		final List<MatriceMap.Radek> m = matice(StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY);
		final String druhy = m.stream().map(MatriceMap.Radek::getKlic).filter(k -> !k.equals(CZ)).findFirst().get();
		final PlanPruvodce plan = PlanPruvodce.sestavVse(CZ, Arrays.asList(CZ, druhy), EnumSet.of(Cast.TRASY, Cast.VRSTEVNICE), m);
		final long trasyCelku = plan.getPolozky().stream().filter(p -> p.getCast() == Cast.TRASY).count();
		assertEquals("trasy jen z celku, když je celek má", 1, trasyCelku);
		assertTrue(plan.getPolozky().stream().noneMatch(p -> p.getKlic().equals(druhy) && p.getCast() == Cast.TRASY));
	}

	/** Před stažením víc než pěti map nebo víc než 10 GB se uživatel ptá. */
	@Test
	public void velkeStahovaniVyzadujePotvrzeni() {
		final List<MatriceMap.Radek> m = matice(StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY);
		final List<String> sest = m.stream().map(MatriceMap.Radek::getKlic).limit(6).collect(Collectors.toList());
		assertTrue(JPruvodceMap.potrebujePotvrzeni(PlanPruvodce.sestavVse(null, sest, EnumSet.of(Cast.MAPA), m)));
		assertFalse(JPruvodceMap.potrebujePotvrzeni(PlanPruvodce.sestavVse(CZ, Collections.singletonList(CZ), EnumSet.of(Cast.MAPA), m)));
	}

	private static final String DE = "europe/germany";

	private static List<MatriceMap.Radek> maticeNemecko(final boolean celekStazen, final boolean bayernStazen) {
		return MatriceMap.sestav(KatalogMap.vestaveny(), k -> StavMapyProGui.stav(k,
				k.getId().equals(DE) && celekStazen || k.getId().equals(DE + "/bayern") && bayernStazen ? StavMapy.Mistni.MAME : StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.NIC, 0, -1, null),
				VrstvyProGui.katalogNemecko(), StavVrstvy::novy);
	}

	/** Stažená celá země: Stáhnout vše i Dostáhnout vrstvy stáhnou trasy celku a vrstevnice regionů, ale žádnou základní mapu regionu. */
	@Test
	public void celaZemeStahujeRegionalniVrstvyBezMapRegionu() {
		final List<MatriceMap.Radek> m = maticeNemecko(true, false);
		final List<String> klice = m.stream().map(MatriceMap.Radek::getKlic).filter(k -> k.equals(DE) || k.startsWith(DE + "/")).collect(Collectors.toList());
		assertTrue("země má regiony", klice.size() > 2);
		for (final PlanPruvodce plan : new PlanPruvodce[] { PlanPruvodce.sestavVse(DE, klice, VSE, m), PlanPruvodce.sestavDostahnout(VSE, m) }) {
			final List<String> id = plan.getPolozky().stream().map(PlanPruvodce.Polozka::getId).collect(Collectors.toList());
			assertEquals(new java.util.HashSet<>(Arrays.asList("routes/" + DE, "contours/" + DE + "/bayern", "contours/" + DE + "/sachsen")), new java.util.HashSet<>(id));
		}
	}

	/** Dostáhnout vrstvy se dívá jen na stažené mapy: nestažené Německo nic, stažený region jen své vrstvy. */
	@Test
	public void dostahnoutVrstvyJenKeStazenymMapam() {
		assertTrue(PlanPruvodce.sestavDostahnout(VSE, maticeNemecko(false, false)).jeHotovo());
		final PlanPruvodce region = PlanPruvodce.sestavDostahnout(VSE, maticeNemecko(false, true));
		assertEquals(Collections.singletonList("contours/" + DE + "/bayern"), region.getPolozky().stream().map(PlanPruvodce.Polozka::getId).collect(Collectors.toList()));
	}

	/** Kanáry jsou ve skupině Španělsko, ale mapa Španělska je nepokrývá: Stáhnout vše je bere samostatně. */
	@Test
	public void kanaryNejsouRegionemSpanelska() {
		final List<MatriceMap.Radek> m = matice(StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY);
		final String es = "europe/spain";
		final String kanary = "africa/canary-islands";
		final PlanPruvodce plan = PlanPruvodce.sestavVse(es, Arrays.asList(es, kanary), EnumSet.of(Cast.MAPA), m);
		assertEquals(new java.util.HashSet<>(Arrays.asList(es, kanary)), plan.getPolozky().stream().map(PlanPruvodce.Polozka::getKlic).collect(Collectors.toSet()));
	}
}
