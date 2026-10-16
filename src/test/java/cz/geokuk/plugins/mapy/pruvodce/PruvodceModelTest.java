package cz.geokuk.plugins.mapy.pruvodce;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;

import cz.geokuk.plugins.mapy.pruvodce.NabidkaZemi.Zeme;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.plugins.mapy.sprava.StavMapyProGui;
import cz.geokuk.plugins.mapy.sprava.StavVrstvy;
import cz.geokuk.plugins.mapy.sprava.VrstvyProGui;

/** Model průvodce nastavením map bez Swingu: nabídka zemí, dostupnost podkladů a vrstev, plán stažení. */
public class PruvodceModelTest {

	private static final String CZ = "europe/czech-republic";

	private static List<MatriceMap.Radek> matice(final Function<KatalogovaMapa, StavMapy.Mistni> mapy, final Function<KatalogovaVrstva, StavVrstvy.Mistni> vrstvy) {
		return MatriceMap.sestav(KatalogMap.vestaveny(), m -> StavMapyProGui.stav(m, mapy.apply(m), StavMapy.Server.NEZNAMY, StavMapy.Faze.NIC, 0, -1, null), VrstvyProGui.katalogCesko(),
				v -> VrstvyProGui.stav(v, vrstvy.apply(v), StavMapy.Faze.NIC, vrstvy.apply(v) == StavVrstvy.Mistni.MAME ? v.getVelikost() : 0, null));
	}

	private static List<MatriceMap.Radek> nicNemame() {
		return matice(m -> StavMapy.Mistni.NEMAME, v -> StavVrstvy.Mistni.NEMAME);
	}

	@Test
	public void ceskoASlovenskoJsouNahore() {
		final List<Zeme> zeme = NabidkaZemi.sestav(KatalogMap.vestaveny());
		assertEquals("Česko", zeme.get(0).getNazev());
		assertEquals(Collections.singletonList(CZ), zeme.get(0).getKlice());
		assertEquals("Slovensko", zeme.get(1).getNazev());
		assertTrue(zeme.size() > 20);
	}

	@Test
	public void zemeSRegionyMaCelekZemeBezCelkuVsechnyRegiony() {
		final List<Zeme> zeme = NabidkaZemi.sestav(KatalogMap.vestaveny());
		final Zeme nemecko = zeme.stream().filter(z -> z.getNazev().equals("Německo")).findFirst().get();
		assertEquals("celá země je jedna mapa", Collections.singletonList("europe/germany"), nemecko.getKlice());
		final Zeme uk = zeme.stream().filter(z -> z.getNazev().equals("Spojené království")).findFirst().get();
		assertTrue("země bez celku se bere po regionech", uk.getKlice().size() >= 3);
		assertTrue(uk.getKlice().contains("europe/united-kingdom/wales"));
		// Nic se v nabídce neopakuje a regiony samostatně v seznamu nejsou.
		final List<String> vsechna = new ArrayList<>();
		zeme.forEach(z -> vsechna.addAll(z.getKlice()));
		assertEquals(vsechna.size(), new java.util.HashSet<>(vsechna).size());
		assertFalse(vsechna.contains("europe/germany/bayern"));
	}

	@Test
	public void podkladyPodleVybranychZemi() {
		final List<String> cz = Collections.singletonList(CZ);
		assertTrue(PodkladPruvodce.CUZK_TOPO.jeDostupny(cz));
		assertTrue(PodkladPruvodce.CUZK_ORTO.jeDostupny(cz));
		assertFalse(PodkladPruvodce.SK_ORTO.jeDostupny(cz));
		assertTrue(PodkladPruvodce.OSM.jeDostupny(cz));
		assertTrue(PodkladPruvodce.TURISTICKA_OFFLINE.jeDostupny(cz));
		final List<String> sk = Collections.singletonList("europe/slovakia");
		assertFalse(PodkladPruvodce.CUZK_TOPO.jeDostupny(sk));
		assertTrue(PodkladPruvodce.SK_ORTO.jeDostupny(sk));
		assertTrue("víc zemí: podklad stačí pro jednu", PodkladPruvodce.CUZK_TOPO.jeDostupny(Arrays.asList("europe/slovakia", CZ)));
		assertFalse("klíč jen začínající stejně není ta země", PodkladPruvodce.CUZK_TOPO.jeDostupny(Collections.singletonList("europe/czech-republic-x")));
		assertTrue(PodkladPruvodce.TURISTICKA_OFFLINE.isOffline());
		assertFalse(PodkladPruvodce.OSM.isOffline());
		assertNotNull(PodkladPruvodce.CUZK_TOPO.getPodklad());
	}

	@Test
	public void kterePodkladyNabizejiKterevrstvy() {
		assertFalse("offline Turistická má trasy v mapě", PlanPruvodce.jeVrstvaDostupna(PodkladPruvodce.TURISTICKA_OFFLINE, Cast.TRASY));
		assertTrue(PlanPruvodce.jeVrstvaDostupna(PodkladPruvodce.TURISTICKA_OFFLINE, Cast.VRSTEVNICE));
		assertTrue(PlanPruvodce.jeVrstvaDostupna(PodkladPruvodce.TURISTICKA_OFFLINE, Cast.STINOVANI));
		assertTrue(PlanPruvodce.jeVrstvaDostupna(PodkladPruvodce.CUZK_TOPO, Cast.TRASY));
		assertFalse(PlanPruvodce.jeVrstvaDostupna(PodkladPruvodce.CUZK_TOPO, Cast.VRSTEVNICE));
		assertEquals("Jen s offline mapou.", PlanPruvodce.duvodNedostupnosti(PodkladPruvodce.OSM, Cast.STINOVANI));
		assertNull(PlanPruvodce.duvodNedostupnosti(PodkladPruvodce.OSM, Cast.TRASY));
	}

	/** Příklad vedoucího: ČÚZK topo (online) + trasy → stáhne se jen balíček tras, zobrazení se přepne hned. */
	@Test
	public void onlinePodkladSTrasamiStahneJenTrasy() {
		final PlanPruvodce plan = PlanPruvodce.sestav(new PruvodceVolba(Collections.singletonList(CZ), PodkladPruvodce.CUZK_TOPO, EnumSet.of(Cast.TRASY)), nicNemame());
		assertEquals(1, plan.getPolozky().size());
		assertEquals("Česko – trasy", plan.getPolozky().get(0).getNazev());
		assertEquals("routes/" + CZ, plan.getPolozky().get(0).getId());
		assertTrue(plan.getPolozky().get(0).isVrstva());
		assertEquals(25_000_000L, plan.getCelkovaVelikost());
		assertTrue(plan.prepnoutHned());
		assertFalse(plan.jeHotovo());
	}

	@Test
	public void offlinePodkladStahneMapuAVybraneVrstvy() {
		final PlanPruvodce plan = PlanPruvodce.sestav(
				new PruvodceVolba(Collections.singletonList(CZ), PodkladPruvodce.TURISTICKA_OFFLINE, EnumSet.of(Cast.TRASY, Cast.VRSTEVNICE, Cast.STINOVANI)), nicNemame());
		final List<String> nazvy = new ArrayList<>();
		plan.getPolozky().forEach(p -> nazvy.add(p.getNazev()));
		assertEquals("trasy se u offline mapy nestahují (jsou v mapě)", Arrays.asList("Česko – mapa", "Česko – vrstevnice", "Česko – stínování"), nazvy);
		assertEquals(Cast.MAPA, plan.getPolozky().get(0).getCast());
		assertFalse("mapa není stažená, přepne se až po jejím stažení", plan.prepnoutHned());
		assertTrue(plan.getCelkovaVelikost() > 266_000_000L + 80_000_000L);
	}

	@Test
	public void stazeneSeNestahujeAHotovoJeBezPolozek() {
		final List<MatriceMap.Radek> vse = matice(m -> StavMapy.Mistni.MAME, v -> StavVrstvy.Mistni.MAME);
		final PlanPruvodce plan = PlanPruvodce.sestav(
				new PruvodceVolba(Collections.singletonList(CZ), PodkladPruvodce.TURISTICKA_OFFLINE, EnumSet.of(Cast.VRSTEVNICE, Cast.STINOVANI)), vse);
		assertTrue(plan.jeHotovo());
		assertEquals(0, plan.getCelkovaVelikost());
		assertTrue("stažená mapa: přepnout hned", plan.prepnoutHned());
	}

	@Test
	public void stazenaMapaChybejiciVrstvy() {
		final List<MatriceMap.Radek> jenMapa = matice(m -> StavMapy.Mistni.MAME, v -> StavVrstvy.Mistni.NEMAME);
		final PlanPruvodce plan = PlanPruvodce.sestav(new PruvodceVolba(Collections.singletonList(CZ), PodkladPruvodce.TURISTICKA_OFFLINE, EnumSet.of(Cast.VRSTEVNICE)), jenMapa);
		assertEquals(1, plan.getPolozky().size());
		assertEquals("Česko – vrstevnice", plan.getPolozky().get(0).getNazev());
		assertTrue("mapa už je, přepne se hned a vrstevnice dotáhne fronta", plan.prepnoutHned());
	}

	@Test
	public void vrstvaKterouKatalogNemaNebudeVPlanuAMapaMimoKatalogTaky() {
		final PlanPruvodce plan = PlanPruvodce.sestav(
				new PruvodceVolba(Arrays.asList("europe/slovakia", "europe/neexistuje"), PodkladPruvodce.TURISTICKA_OFFLINE, EnumSet.of(Cast.TRASY, Cast.VRSTEVNICE, Cast.STINOVANI)), nicNemame());
		assertEquals("jen mapa Slovenska, vrstvy pro Slovensko katalog nemá", 1, plan.getPolozky().size());
		assertEquals("Slovensko – mapa", plan.getPolozky().get(0).getNazev());
	}

	@Test
	public void viceZemiSeScita() {
		final PlanPruvodce plan = PlanPruvodce.sestav(
				new PruvodceVolba(Arrays.asList(CZ, "europe/slovakia"), PodkladPruvodce.TURISTICKA_OFFLINE, EnumSet.noneOf(Cast.class)), nicNemame());
		assertEquals(2, plan.getPolozky().size());
		assertEquals("pořadí zemí zůstane", "Česko – mapa", plan.getPolozky().get(0).getNazev());
		assertEquals(605_630_432L + 236_910_343L, plan.getCelkovaVelikost());
	}

	@Test
	public void chybaSeZkusiZnovuANovejsiSeNestahuje() {
		final List<MatriceMap.Radek> m = MatriceMap.sestav(KatalogMap.vestaveny(),
				k -> StavMapyProGui.stav(k, StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, k.getId().equals(CZ) ? StavMapy.Faze.CHYBA : StavMapy.Faze.NIC, 0, -1, "chyba"), null, null);
		final PlanPruvodce plan = PlanPruvodce.sestav(new PruvodceVolba(Collections.singletonList(CZ), PodkladPruvodce.TURISTICKA_OFFLINE, EnumSet.noneOf(Cast.class)), m);
		assertEquals("po chybě jde stahovat znovu", 1, plan.getPolozky().size());
		final List<MatriceMap.Radek> novejsi = MatriceMap.sestav(KatalogMap.vestaveny(),
				k -> StavMapyProGui.stav(k, StavMapy.Mistni.MAME, StavMapy.Server.NOVEJSI, StavMapy.Faze.NIC, 0, -1, null), null, null);
		assertTrue("novější verze už stažené mapy se průvodcem nestahuje", PlanPruvodce.sestav(
				new PruvodceVolba(Collections.singletonList(CZ), PodkladPruvodce.TURISTICKA_OFFLINE, EnumSet.noneOf(Cast.class)), novejsi).jeHotovo());
	}

	@Test
	public void volbaNeobsahujeMapuJakoVrstvu() {
		final PruvodceVolba v = new PruvodceVolba(Collections.singletonList(CZ), PodkladPruvodce.OSM, EnumSet.of(Cast.MAPA, Cast.TRASY));
		assertEquals(EnumSet.of(Cast.TRASY), v.getVrstvy());
	}
}
