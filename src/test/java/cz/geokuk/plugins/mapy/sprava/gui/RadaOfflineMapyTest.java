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
		Assert.assertTrue(r.odkaz, r.odkaz.startsWith("Stáhnout mapu Česko (~"));
		Assert.assertNull(r.text);
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
		Assert.assertEquals("Dokončit stažení mapy Česko", RadaOfflineMapy.pro(cesko, stav(cesko, NEMAME, ZRUSENO, 150, 600)).odkaz);
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

	/** Odkaz „Zkusit znovu“ u souseda říká, o kterou mapu jde. */
	@Test
	public void sousedSChybouMaVNazvuMapu() {
		final KatalogovaMapa sachsen = KATALOG.podleId("europe/germany/sachsen").get();
		final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, m -> m == sachsen ? stav(m, NEMAME, CHYBA, 0, -1) : StavMapy.novy(m), DECIN, null, U_HRANICE);
		Assert.assertTrue(r.sousedni.stream().anyMatch(s -> "Zkusit znovu stáhnout mapu Sasko".equals(s.odkaz)));
	}
}
