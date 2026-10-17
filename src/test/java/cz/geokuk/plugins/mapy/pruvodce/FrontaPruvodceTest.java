package cz.geokuk.plugins.mapy.pruvodce;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

/** Fronta průvodce: pouští položky po jedné, čeká na dokončení, po chybě nebo zrušení zastaví. */
public class FrontaPruvodceTest {

	private static final String CZ = "europe/czech-republic";

	private static PlanPruvodce plan() {
		final List<MatriceMap.Radek> m = MatriceMap.sestav(KatalogMap.vestaveny(),
				k -> StavMapyProGui.stav(k, StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.NIC, 0, -1, null), VrstvyProGui.katalogCesko(), StavVrstvy::novy);
		return PlanPruvodce.sestav(new PruvodceVolba(Collections.singletonList(CZ), PodkladPruvodce.TURISTICKA_OFFLINE, EnumSet.of(Cast.TRASY, Cast.VRSTEVNICE)), m);
	}

	private static final String MAPA = CZ;
	private static final String TRASY = "routes/" + CZ;
	private static final String VRSTEVNICE = "contours/" + CZ;

	private static Map<String, StavBunky> stavy(final StavBunky mapa, final StavBunky trasy, final StavBunky vrstevnice) {
		final Map<String, StavBunky> s = new HashMap<>();
		s.put(MAPA, mapa);
		s.put(TRASY, trasy);
		s.put(VRSTEVNICE, vrstevnice);
		return s;
	}

	@Test
	public void poustiPoJedneACeka() {
		final FrontaPruvodce f = new FrontaPruvodce(plan());
		Map<String, StavBunky> s = stavy(StavBunky.KE_STAZENI, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI);
		FrontaPruvodce.Krok k = f.dalsi(s::get);
		assertEquals(Akce.STAHUJ, k.getAkce());
		assertEquals(MAPA, k.getPolozka().getId());
		s = stavy(StavBunky.STAHUJE, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI);
		assertEquals("mapa se stahuje, čekáme", Akce.CEKEJ, f.dalsi(s::get).getAkce());
		s = stavy(StavBunky.STAZENO, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI);
		k = f.dalsi(s::get);
		assertEquals(Akce.STAHUJ, k.getAkce());
		assertEquals(TRASY, k.getPolozka().getId());
		s = stavy(StavBunky.STAZENO, StavBunky.STAZENO, StavBunky.KE_STAZENI);
		assertEquals(VRSTEVNICE, f.dalsi(s::get).getPolozka().getId());
		s = stavy(StavBunky.STAZENO, StavBunky.STAZENO, StavBunky.STAZENO);
		assertEquals(Akce.HOTOVO, f.dalsi(s::get).getAkce());
	}

	@Test
	public void chybaZastaviFrontu() {
		final FrontaPruvodce f = new FrontaPruvodce(plan());
		assertEquals(Akce.STAHUJ, f.dalsi(stavy(StavBunky.KE_STAZENI, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI)::get).getAkce());
		final FrontaPruvodce.Krok k = f.dalsi(stavy(StavBunky.CHYBA, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI)::get);
		assertEquals(Akce.ZASTAVENO, k.getAkce());
		assertEquals(MAPA, k.getPolozka().getId());
		assertTrue("chyba, ne zrušení", k.isChyba());
		assertEquals("a už nic dalšího", Akce.ZASTAVENO, f.dalsi(stavy(StavBunky.STAZENO, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI)::get).getAkce());
	}

	@Test
	public void zruseniZastaviFrontu() {
		final FrontaPruvodce f = new FrontaPruvodce(plan());
		assertEquals(Akce.STAHUJ, f.dalsi(stavy(StavBunky.KE_STAZENI, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI)::get).getAkce());
		f.zrus();
		assertEquals(Akce.ZASTAVENO, f.dalsi(stavy(StavBunky.STAHUJE, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI)::get).getAkce());
	}

	/** Zrušená položka (zpátky ve stavu ke stažení bez chyby) frontu zastaví, ale až když se to potvrdí podruhé, aby to nespletlo zpoždění stavu po spuštění. */
	@Test
	public void zrusenaPolozkaBezChybyZastaviPoDruhemPozorovani() {
		final FrontaPruvodce f = new FrontaPruvodce(plan());
		final Map<String, StavBunky> ke = stavy(StavBunky.KE_STAZENI, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI);
		assertEquals(Akce.STAHUJ, f.dalsi(ke::get).getAkce());
		assertEquals("stav ještě nedoběhl", Akce.CEKEJ, f.dalsi(ke::get).getAkce());
		final FrontaPruvodce.Krok krok = f.dalsi(ke::get);
		assertEquals(Akce.ZASTAVENO, krok.getAkce());
		assertFalse("zrušení není chyba", krok.isChyba());
	}

	@Test
	public void nespustenaPolozkaSePoustiZnovu() {
		final FrontaPruvodce f = new FrontaPruvodce(plan());
		final Map<String, StavBunky> ke = stavy(StavBunky.KE_STAZENI, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI);
		final FrontaPruvodce.Krok prvni = f.dalsi(ke::get);
		f.nespusteno(prvni.getPolozka());
		final FrontaPruvodce.Krok druhy = f.dalsi(ke::get);
		assertEquals(Akce.STAHUJ, druhy.getAkce());
		assertEquals(prvni.getPolozka().getId(), druhy.getPolozka().getId());
	}

	@Test
	public void novejsiMapaSeNepocitaAJeHotovoBezPolozek() {
		final List<MatriceMap.Radek> m = MatriceMap.sestav(KatalogMap.vestaveny(),
				k -> StavMapyProGui.stav(k, StavMapy.Mistni.MAME, StavMapy.Server.NOVEJSI, StavMapy.Faze.NIC, 0, -1, null), null, null);
		final PlanPruvodce plan = PlanPruvodce.sestav(new PruvodceVolba(Collections.singletonList(CZ), PodkladPruvodce.TURISTICKA_OFFLINE, EnumSet.noneOf(Cast.class)), m);
		assertEquals(Akce.HOTOVO, new FrontaPruvodce(plan).dalsi(id -> StavBunky.NOVEJSI).getAkce());
	}

	@Test
	public void stahovaniCekaNaDokonceniBezOhleduNaPocetTiknuti() {
		final FrontaPruvodce f = new FrontaPruvodce(plan());
		assertEquals(Akce.STAHUJ, f.dalsi(stavy(StavBunky.KE_STAZENI, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI)::get).getAkce());
		final Map<String, StavBunky> bezi = stavy(StavBunky.STAHUJE, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI);
		for (int i = 0; i < 5; i++) {
			assertEquals("tik " + i, Akce.CEKEJ, f.dalsi(bezi::get).getAkce());
		}
		final FrontaPruvodce.Krok dal = f.dalsi(stavy(StavBunky.STAZENO, StavBunky.KE_STAZENI, StavBunky.KE_STAZENI)::get);
		assertEquals(Akce.STAHUJ, dal.getAkce());
		assertEquals(TRASY, dal.getPolozka().getId());
	}

	@Test
	public void novejsiMapaUprostredPlanuSePreskoci() {
		final FrontaPruvodce f = new FrontaPruvodce(plan());
		final FrontaPruvodce.Krok k = f.dalsi(stavy(StavBunky.STAZENO, StavBunky.NOVEJSI, StavBunky.KE_STAZENI)::get);
		assertEquals(Akce.STAHUJ, k.getAkce());
		assertEquals(VRSTEVNICE, k.getPolozka().getId());
	}
}
