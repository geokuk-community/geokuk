package cz.geokuk.plugins.mapy.sprava.gui;

import static cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.*;
import static cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.*;
import static cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.*;

/** Rada při chybějící offline mapě nabídne mapu z katalogu, která zobrazené místo pokrývá. */
public class RadaOfflineMapyTest {

	private static final KatalogMap KATALOG = KatalogMap.vestaveny();
	private static final Wgs PRAHA = new Wgs(50.08, 14.42);
	private static final Wgs MNICHOV = new Wgs(48.14, 11.58);

	private static StavMapy stav(final KatalogovaMapa m, final StavMapy.Mistni mistni, final StavMapy.Faze faze, final long stazeno, final long celkem) {
		return StavMapyProGui.stav(m, mistni, NEZNAMY, faze, stazeno, celkem, faze == CHYBA ? "Server je nedostupný." : null);
	}

	@Test
	public void nabidneZemiProMisto() {
		final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, StavMapy::novy, PRAHA);
		Assert.assertEquals("europe/czech-republic", r.mapa.getId());
		Assert.assertTrue(r.odkaz, r.odkaz.startsWith("Stáhnout mapu (~"));
		Assert.assertNull(r.text);
	}

	/** S nabídkou řádku rada nabídne dvě tlačítka na jedné mapě: Stáhnout mapu (velikost) a Stáhnout mapu a vrstvy (velikost). */
	@Test
	public void dveTlacitkaNaJednePrazdneMape() {
		final java.util.function.Function<KatalogovaMapa, cz.geokuk.plugins.mapy.sprava.NabidkaRadku> nabidky = nabidky(StavMapy::novy);
		final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, StavMapy::novy, PRAHA, null, null, nabidky);
		Assert.assertEquals("Česko", r.predpona);
		Assert.assertTrue(r.odkaz, r.odkaz.startsWith("Stáhnout mapu ("));
		Assert.assertTrue(r.odkaz2, r.odkaz2 != null && r.odkaz2.startsWith("Stáhnout mapu a vrstvy ("));
		Assert.assertEquals(cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce.Rezim.STAHNOUT, r.rezim2);
		Assert.assertEquals("europe/czech-republic", r.mapa.getId());
		final RadaOfflineMapy bezNabidky = RadaOfflineMapy.pro(KATALOG, StavMapy::novy, PRAHA, null, null, null);
		Assert.assertNull("bez nabídky jen jeden odkaz", bezNabidky.odkaz2);
	}

	/** Po chybě a u rozpracované mapy se nabízí jen jedno tlačítko. */
	@Test
	public void poChybeJenJednoTlacitko() {
		final java.util.function.Function<KatalogovaMapa, StavMapy> chyba = m -> stav(m, NEMAME, CHYBA, 0, -1);
		final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, chyba, PRAHA, null, null, nabidky(chyba));
		Assert.assertEquals("Zkusit znovu", r.odkaz);
		Assert.assertNull(r.odkaz2);
	}

	private static java.util.function.Function<KatalogovaMapa, cz.geokuk.plugins.mapy.sprava.NabidkaRadku> nabidky(final java.util.function.Function<KatalogovaMapa, StavMapy> stavy) {
		final java.util.List<cz.geokuk.plugins.mapy.sprava.MatriceMap.Radek> matice = cz.geokuk.plugins.mapy.sprava.MatriceMap.sestav(KATALOG, stavy,
				cz.geokuk.plugins.mapy.sprava.VrstvyProGui.katalogCesko(), v -> cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, NIC, 0, null));
		return m -> matice.stream().filter(r -> r.getKlic().equals(m.getId())).findFirst().map(r -> cz.geokuk.plugins.mapy.sprava.NabidkaRadku.pro(r, new cz.geokuk.plugins.mapy.sprava.NabidkaRadku.Okolnosti(-1, null))).orElse(null);
	}

	/** Region má přednost před zemí: v Mnichově se nabídne Bavorsko, ne celé Německo. */
	@Test
	public void regionPredZemi() {
		Assert.assertEquals("Bavorsko", RadaOfflineMapy.pro(KATALOG, StavMapy::novy, MNICHOV).mapa.getNazev());
		Assert.assertEquals("Berlín", RadaOfflineMapy.pro(KATALOG, StavMapy::novy, new Wgs(52.52, 13.40)).mapa.getNazev());
	}

	/** Během stahování jiné mapy rada stažení nenabízí (klik by nic neudělal), ale řekne proč. */
	@Test
	public void behemStahovaniJineMapy() {
		final KatalogovaMapa bavorsko = KATALOG.podleId("europe/germany/bayern").get();
		final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, StavMapy::novy, PRAHA, bavorsko);
		Assert.assertNull(r.odkaz);
		Assert.assertEquals("Stahuje se mapa Bavorsko. Mapu Česko stáhnete, až skončí.", r.text);
		final KatalogovaMapa cesko = KATALOG.podleId("europe/czech-republic").get();
		Assert.assertEquals("stahovaná mapa sama", "Stahuje se mapa Česko: 25 %.",
				RadaOfflineMapy.pro(KATALOG, m -> stav(m, NEMAME, STAHUJE, 150, 600), PRAHA, cesko).text);
	}

	@Test
	public void stazenaMapaSeNenabizi() {
		final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, m -> stav(m, MAME, NIC, 0, -1), PRAHA);
		Assert.assertNull(r.odkaz);
		Assert.assertEquals(RadaOfflineMapy.BEZ_KATALOGU, r.text);
	}

	@Test
	public void mimoKatalog() {
		final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, StavMapy::novy, new Wgs(35.68, 139.69));
		Assert.assertNull(r.mapa);
		Assert.assertNull(r.odkaz);
	}

	@Test
	public void prubehAChyba() {
		final KatalogovaMapa cesko = KATALOG.podleId("europe/czech-republic").get();
		final RadaOfflineMapy stahuje = RadaOfflineMapy.pro(cesko, stav(cesko, NEMAME, STAHUJE, 150, 600));
		Assert.assertEquals("Stahuje se mapa Česko: 25 %.", stahuje.text);
		Assert.assertNull(stahuje.odkaz);
		final RadaOfflineMapy chyba = RadaOfflineMapy.pro(cesko, stav(cesko, NEMAME, CHYBA, 0, -1));
		Assert.assertEquals("Zkusit znovu", chyba.odkaz);
		Assert.assertEquals("Dokončit stažení", RadaOfflineMapy.pro(cesko, stav(cesko, NEMAME, ZRUSENO, 150, 600)).odkaz);
	}

	private static final RadaOfflineMapy.Vyrez U_HRANICE = new RadaOfflineMapy.Vyrez(50.4, 13.4, 51.2, 15.0);
	private static final Wgs DECIN = new Wgs(50.78, 14.2);

	private static java.util.List<String> idSousednich(final RadaOfflineMapy r) {
		final java.util.List<String> id = new java.util.ArrayList<>();
		for (final RadaOfflineMapy s : r.sousedni) {
			id.add(s.mapa.getId());
		}
		return id;
	}

	/** U hranice nabídne vedle mapy pod středem i sousední mapy, které do výřezu zasahují; mapa pod středem mezi nimi není. */
	@Test
	public void nabidneSousedniMapyVeVyrezu() {
		final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, StavMapy::novy, DECIN, null, U_HRANICE);
		Assert.assertEquals("europe/czech-republic", r.mapa.getId());
		final java.util.List<String> sousedni = idSousednich(r);
		Assert.assertTrue(sousedni.toString(), sousedni.contains("europe/germany/sachsen"));
		Assert.assertFalse(sousedni.contains("europe/czech-republic"));
		Assert.assertFalse("Bavorsko do výřezu nezasahuje", sousedni.contains("europe/germany/bayern"));
		Assert.assertTrue(sousedni.size() <= RadaOfflineMapy.NEJVIC_SOUSEDNICH);
		for (final RadaOfflineMapy s : r.sousedni) {
			Assert.assertTrue(s.odkaz, s.odkaz.startsWith("Stáhnout mapu "));
		}
	}

	@Test
	public void sousedniMapyBezVyrezuNeboPriStahovani() {
		Assert.assertTrue(RadaOfflineMapy.pro(KATALOG, StavMapy::novy, DECIN, null, null).sousedni.isEmpty());
		final KatalogovaMapa bavorsko = KATALOG.podleId("europe/germany/bayern").get();
		Assert.assertTrue("při stahování jiné mapy se nic dalšího nenabízí", RadaOfflineMapy.pro(KATALOG, StavMapy::novy, DECIN, bavorsko, U_HRANICE).sousedni.isEmpty());
	}

	/** Co už máme, se nenabízí; malý výřez uprostřed země sousedy nemá. */
	@Test
	public void stazeneASousedniMimoVyrez() {
		final RadaOfflineMapy bezSaska = RadaOfflineMapy.pro(KATALOG, m -> m.getId().equals("europe/germany/sachsen") ? stav(m, MAME, NIC, 0, -1) : StavMapy.novy(m), DECIN, null,
				U_HRANICE);
		Assert.assertFalse(idSousednich(bezSaska).contains("europe/germany/sachsen"));
		final RadaOfflineMapy uprostred = RadaOfflineMapy.pro(KATALOG, StavMapy::novy, PRAHA, null, new RadaOfflineMapy.Vyrez(50.0, 14.3, 50.2, 14.6));
		Assert.assertTrue(idSousednich(uprostred).toString(), uprostred.sousedni.isEmpty());
	}

	/** Sousedé jsou nejbližší mapy, ne nejmenší: Berlín je menší než Polsko, ale od Děčína dál. */
	@Test
	public void sousedniPodleBlizkostiNeVelikosti() {
		final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, StavMapy::novy, DECIN, null, new RadaOfflineMapy.Vyrez(50.0, 12.0, 53.0, 16.5));
		final java.util.List<String> sousedni = idSousednich(r);
		Assert.assertEquals(sousedni.toString(), "europe/germany/sachsen", sousedni.get(0));
		Assert.assertFalse(sousedni.toString(), sousedni.contains("europe/germany/berlin"));
		Assert.assertTrue(sousedni.size() <= RadaOfflineMapy.NEJVIC_SOUSEDNICH);
	}

	/** Tabulka rady: hlavní mapa, štítek a sousedé; region je pod hlavičkou své země. */
	@Test
	public void tabulkaMaRegionyPodZemi() {
		final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, StavMapy::novy, DECIN, null, U_HRANICE);
		final java.util.List<RadaOfflineMapy.RadekRady> t = r.tabulka();
		Assert.assertEquals("Česko", t.get(0).nazev);
		Assert.assertFalse(t.get(0).region);
		Assert.assertSame(r, t.get(0).rada);
		Assert.assertTrue("štítek před sousedy", t.get(1).stitek);
		Assert.assertEquals(RadaOfflineMapy.SOUSEDNI, t.get(1).nazev);
		final int sasko = indexNazvu(t, "Sasko");
		Assert.assertTrue(t.toString(), sasko > 2);
		Assert.assertTrue(t.get(sasko).region);
		Assert.assertEquals("hlavička země nad regionem", "Německo", t.get(sasko - 1).nazev);
		Assert.assertEquals("celá země před regionem", "europe/germany", t.get(sasko - 1).rada.mapa.getId());
	}

	private static final Wgs TOLEDO = new Wgs(39.86, -4.02);

	private static long pocetNazvu(final java.util.List<RadaOfflineMapy.RadekRady> t, final String nazev) {
		return t.stream().filter(r -> nazev.equals(r.nazev)).count();
	}

	/** Střed v regionu: celá země má řádek se dvěma tlačítky a střed-region je první pod ní. */
	@Test
	public void spanelskoNabidneCelekARegionPodNim() {
		final java.util.List<RadaOfflineMapy.RadekRady> t = RadaOfflineMapy.pro(KATALOG, StavMapy::novy, TOLEDO, null, null, nabidky(StavMapy::novy)).tabulka();
		Assert.assertEquals("Španělsko", t.get(0).nazev);
		Assert.assertFalse(t.get(0).region);
		Assert.assertEquals("europe/spain", t.get(0).rada.mapa.getId());
		Assert.assertTrue(t.get(0).rada.odkaz, t.get(0).rada.odkaz.startsWith("Stáhnout mapu ("));
		Assert.assertEquals("Kastilie-La Mancha", t.get(1).nazev);
		Assert.assertTrue(t.get(1).region);
		Assert.assertEquals("europe/spain/castilla-la-mancha", t.get(1).rada.mapa.getId());
	}

	/** Španělsko je v tabulce jednou, i když je současně hlavní a v záběru jsou další jeho regiony. */
	@Test
	public void zemeVTabulceJednou() {
		final RadaOfflineMapy.Vyrez siroky = new RadaOfflineMapy.Vyrez(37.0, -7.0, 42.5, 2.0);
		final java.util.List<RadaOfflineMapy.RadekRady> t = RadaOfflineMapy.pro(KATALOG, StavMapy::novy, TOLEDO, null, siroky, nabidky(StavMapy::novy)).tabulka();
		Assert.assertEquals(t.toString(), 1, pocetNazvu(t, "Španělsko"));
		Assert.assertEquals(1, pocetNazvu(t, "Kastilie-La Mancha"));
		Assert.assertTrue("sousední regiony Španělska jsou pod ním", t.stream().filter(r -> r.region).count() > 1);
		Assert.assertTrue("Španělsko nemá štítek „V záběru je i:“ před svými regiony", t.stream().noneMatch(r -> r.stitek) || t.indexOf(t.stream().filter(r -> r.stitek).findFirst().get()) > 1);
		final java.util.Set<String> id = new java.util.HashSet<>();
		for (final RadaOfflineMapy.RadekRady r : t) {
			if (r.rada != null) {
				Assert.assertTrue("mapa jednou: " + r.rada.mapa.getId(), id.add(r.rada.mapa.getId()));
			}
		}
	}

	/** Když máme region, celou zemi rada nenabízí. */
	@Test
	public void stazenyRegionCelekNenabidne() {
		final java.util.function.Function<KatalogovaMapa, StavMapy> stavy = m -> m.getId().equals("europe/spain/andalucia") ? stav(m, MAME, NIC, 0, -1) : StavMapy.novy(m);
		final java.util.List<RadaOfflineMapy.RadekRady> t = RadaOfflineMapy.pro(KATALOG, stavy, TOLEDO, null, null, nabidky(stavy)).tabulka();
		Assert.assertEquals("europe/spain/castilla-la-mancha", t.get(t.size() - 1).rada.mapa.getId());
		Assert.assertNull(t.get(0).rada);
	}

	private static int indexNazvu(final java.util.List<RadaOfflineMapy.RadekRady> t, final String nazev) {
		for (int i = 0; i < t.size(); i++) {
			if (nazev.equals(t.get(i).nazev)) {
				return i;
			}
		}
		return -1;
	}

	/** Odkaz „Zkusit znovu“ u souseda je v tabulce u názvu mapy. */
	@Test
	public void sousedSChybouMaVNazvuMapu() {
		final KatalogovaMapa sachsen = KATALOG.podleId("europe/germany/sachsen").get();
		final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, m -> m == sachsen ? stav(m, NEMAME, CHYBA, 0, -1) : StavMapy.novy(m), DECIN, null, U_HRANICE);
		Assert.assertTrue(r.sousedni.stream().anyMatch(s -> "Zkusit znovu".equals(s.odkaz) && "Sasko".equals(s.mapa.getNazev())));
	}
}
