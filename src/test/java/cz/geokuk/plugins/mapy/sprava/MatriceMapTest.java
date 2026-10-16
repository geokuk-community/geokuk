package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;

import cz.geokuk.plugins.mapy.sprava.MatriceMap.Bunka;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Radek;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.StavBunky;

/** Matice map × (mapa, trasy, vrstevnice, stínování): řádek je klíč mapy, buňka má stav a velikost, řádek existuje i bez stažené mapy. */
public class MatriceMapTest {

	private static final String CZ = "europe/czech-republic";

	private static StavMapy mapa(final KatalogovaMapa m, final StavMapy.Mistni mistni, final StavMapy.Server server, final StavMapy.Faze faze, final long stazeno) {
		return StavMapyProGui.stav(m, mistni, server, faze, stazeno, 1000, faze == StavMapy.Faze.CHYBA ? "chyba mapy" : null);
	}

	private static Radek radek(final List<Radek> radky, final String klic) {
		return radky.stream().filter(r -> r.getKlic().equals(klic)).findFirst().get();
	}

	private static Function<KatalogovaMapa, StavMapy> nemame() {
		return m -> mapa(m, StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.NIC, 0);
	}

	@Test
	public void kazdaMapaMaCtyriBunkyVPoradiKatalogu() {
		final KatalogMap katalog = KatalogMap.vestaveny();
		final List<Radek> radky = MatriceMap.sestav(katalog, nemame(), VrstvyProGui.katalogCesko(), v -> StavVrstvy.novy(v));
		assertEquals(katalog.getMapy().size(), radky.size());
		for (int i = 0; i < radky.size(); i++) {
			assertEquals(katalog.getMapy().get(i).getId(), radky.get(i).getKlic());
			for (final Cast c : Cast.values()) {
				assertEquals(c, radky.get(i).getBunka(c).getCast());
			}
		}
	}

	@Test
	public void bezKataloguVrstevJenSloupecMapa() {
		final List<Radek> radky = MatriceMap.sestav(KatalogMap.vestaveny(), nemame(), null, null);
		final Radek cz = radek(radky, CZ);
		assertEquals(StavBunky.KE_STAZENI, cz.getBunka(Cast.MAPA).getStav());
		for (final Cast c : new Cast[] { Cast.TRASY, Cast.VRSTEVNICE, Cast.STINOVANI }) {
			assertEquals(StavBunky.NENI, cz.getBunka(c).getStav());
			assertNull(cz.getBunka(c).getId());
		}
		assertFalse(cz.mameCokoli());
	}

	@Test
	public void stavyVrstevAJejichVelikosti() {
		final KatalogVrstev vrstvy = VrstvyProGui.katalogCesko();
		final Function<KatalogovaVrstva, StavVrstvy> stavy = v -> {
			switch (v.getTyp()) {
			case TRASY:
				return VrstvyProGui.stav(v, StavVrstvy.Mistni.MAME, StavMapy.Faze.NIC, v.getVelikost(), null);
			case VRSTEVNICE:
				return VrstvyProGui.stav(v, StavVrstvy.Mistni.NEMAME, StavMapy.Faze.NIC, 0, null);
			default:
				return VrstvyProGui.stav(v, StavVrstvy.Mistni.JINA_VERZE, StavMapy.Faze.NIC, v.getVelikost(), null);
			}
		};
		final Radek cz = radek(MatriceMap.sestav(KatalogMap.vestaveny(), m -> mapa(m, m.getId().equals(CZ) ? StavMapy.Mistni.MAME : StavMapy.Mistni.NEMAME, StavMapy.Server.AKTUALNI,
				StavMapy.Faze.NIC, 0), vrstvy, stavy), CZ);
		assertEquals(StavBunky.STAZENO, cz.getBunka(Cast.MAPA).getStav());
		assertEquals(StavBunky.STAZENO, cz.getBunka(Cast.TRASY).getStav());
		assertEquals("routes/" + CZ, cz.getBunka(Cast.TRASY).getId());
		assertEquals(25_000_000, cz.getBunka(Cast.TRASY).getVelikost());
		assertEquals(25_000_000, cz.getBunka(Cast.TRASY).getStazeno());
		assertEquals(StavBunky.KE_STAZENI, cz.getBunka(Cast.VRSTEVNICE).getStav());
		assertTrue(cz.getBunka(Cast.VRSTEVNICE).jeKeStazeni());
		assertFalse(cz.getBunka(Cast.VRSTEVNICE).jeStazeno());
		assertEquals(266_000_000, cz.getBunka(Cast.VRSTEVNICE).getVelikost());
		assertEquals(StavBunky.NOVEJSI, cz.getBunka(Cast.STINOVANI).getStav());
		assertTrue("novější verze se dá stáhnout i smazat", cz.getBunka(Cast.STINOVANI).jeKeStazeni() && cz.getBunka(Cast.STINOVANI).jeStazeno());
		assertTrue(cz.mameCokoli());
	}

	/** Řádek existuje i bez základní mapy: stažené trasy k online podkladu. */
	@Test
	public void vrstvaBezZakladniMapy() {
		final Radek cz = radek(MatriceMap.sestav(KatalogMap.vestaveny(), nemame(), VrstvyProGui.katalogCesko(),
				v -> VrstvyProGui.stav(v, v.getId().startsWith("routes") ? StavVrstvy.Mistni.MAME : StavVrstvy.Mistni.NEMAME, StavMapy.Faze.NIC, v.getId().startsWith("routes") ? v.getVelikost() : 0, null)),
				CZ);
		assertEquals(StavBunky.KE_STAZENI, cz.getBunka(Cast.MAPA).getStav());
		assertEquals(StavBunky.STAZENO, cz.getBunka(Cast.TRASY).getStav());
		assertTrue(cz.mameCokoli());
	}

	@Test
	public void stahovaniAChyba() {
		final KatalogVrstev vrstvy = VrstvyProGui.katalogCesko();
		final Radek stahuje = radek(MatriceMap.sestav(KatalogMap.vestaveny(), nemame(), vrstvy, v -> VrstvyProGui.stav(v, StavVrstvy.Mistni.NEMAME,
				v.getId().startsWith("contours") ? StavMapy.Faze.STAHUJE : StavMapy.Faze.NIC, v.getId().startsWith("contours") ? 100_000_000 : 0, null)), CZ);
		assertEquals(StavBunky.STAHUJE, stahuje.getBunka(Cast.VRSTEVNICE).getStav());
		assertEquals(100_000_000, stahuje.getBunka(Cast.VRSTEVNICE).getStazeno());
		assertTrue(stahuje.bezi());
		assertFalse(radek(MatriceMap.sestav(KatalogMap.vestaveny(), nemame(), vrstvy, StavVrstvy::novy), CZ).bezi());

		final Radek chyba = radek(MatriceMap.sestav(KatalogMap.vestaveny(), m -> mapa(m, StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.CHYBA, 0), vrstvy, StavVrstvy::novy), CZ);
		assertEquals(StavBunky.CHYBA, chyba.getBunka(Cast.MAPA).getStav());
		assertEquals("chyba mapy", chyba.getBunka(Cast.MAPA).getChyba());
		assertTrue("po chybě jde stáhnout znovu", chyba.getBunka(Cast.MAPA).jeKeStazeni());
	}

	@Test
	public void novejsiMapaNaServeru() {
		final Radek cz = radek(MatriceMap.sestav(KatalogMap.vestaveny(), m -> mapa(m, StavMapy.Mistni.MAME, StavMapy.Server.NOVEJSI, StavMapy.Faze.NIC, 0), null, null), CZ);
		assertEquals(StavBunky.NOVEJSI, cz.getBunka(Cast.MAPA).getStav());
	}

	/** Vrstva patří přesně svému klíči: vrstvy regionů se u celé země neukazují a naopak. */
	@Test
	public void vrstvaPatriPresneSvemuKlici() throws IOException {
		final KatalogVrstev k = KatalogVrstev.nacti(KatalogVrstevTest.katalog(KatalogVrstevTest.vrstva("routes/europe/germany/bayern", "routes", "europe/germany/bayern", "map",
				KatalogVrstevTest.soubor("bayern.routes.map", "v1/bayern.routes.map", 40_000_000, VrstvyProGui.SHA), "")), KatalogMap.vestaveny());
		final List<Radek> radky = MatriceMap.sestav(KatalogMap.vestaveny(), nemame(), k, StavVrstvy::novy);
		assertEquals(StavBunky.KE_STAZENI, radek(radky, "europe/germany/bayern").getBunka(Cast.TRASY).getStav());
		assertEquals(StavBunky.NENI, radek(radky, "europe/germany").getBunka(Cast.TRASY).getStav());
		assertEquals(StavBunky.NENI, radek(radky, "europe/germany/sachsen").getBunka(Cast.TRASY).getStav());
	}

	/** Vrstva celé země se regionu nepřidá (a naopak): párování je přesné, ne podle prefixu klíče. */
	@Test
	public void vrstvaCeleZemeNepatriRegionu() throws IOException {
		final KatalogVrstev k = KatalogVrstev.nacti(KatalogVrstevTest.katalog(KatalogVrstevTest.vrstva("routes/europe/germany", "routes", "europe/germany", "map",
				KatalogVrstevTest.soubor("germany.routes.map", "v1/germany.routes.map", 150_000_000, VrstvyProGui.SHA), "")), KatalogMap.vestaveny());
		final List<Radek> radky = MatriceMap.sestav(KatalogMap.vestaveny(), nemame(), k, StavVrstvy::novy);
		assertEquals(StavBunky.KE_STAZENI, radek(radky, "europe/germany").getBunka(Cast.TRASY).getStav());
		assertEquals(StavBunky.NENI, radek(radky, "europe/germany/bayern").getBunka(Cast.TRASY).getStav());
		assertEquals(StavBunky.NENI, radek(radky, "europe/germany/berlin").getBunka(Cast.TRASY).getStav());
		assertEquals(StavBunky.NENI, radek(radky, "europe/czech-republic").getBunka(Cast.TRASY).getStav());
	}

	@Test
	public void datumDatARozpracovanostVBunce() {
		final Radek cz = radek(MatriceMap.sestav(KatalogMap.vestaveny(), nemame(), VrstvyProGui.katalogCesko(),
				v -> VrstvyProGui.stav(v, StavVrstvy.Mistni.NEMAME, StavMapy.Faze.NIC, v.getId().startsWith("contours") ? 5_000_000 : 0, null)), CZ);
		assertEquals(java.time.LocalDate.of(2026, 10, 13), cz.getBunka(Cast.TRASY).getDatumDat());
		assertFalse(cz.getBunka(Cast.TRASY).jeCoMazat());
		assertTrue("rozpracovaná vrstva jde smazat", cz.getBunka(Cast.VRSTEVNICE).jeCoMazat());
		assertEquals(StavBunky.KE_STAZENI, cz.getBunka(Cast.VRSTEVNICE).getStav());
	}
}
