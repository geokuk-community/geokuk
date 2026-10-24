package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.nio.file.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Wgs;

/** Rada při prázdné offline mapě: místo bez podrobné mapy (jen přehledová mapa světa do z10, nebo mimo území map). */
public class PodrobnaOfflineMapaTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final Wgs KUKOV = new Wgs(50.003, 14.405);
	private static final Wgs VIDEN = new Wgs(48.2082, 16.3738);

	private OfflineMapy mapy;
	private File slozka;

	@Before
	public void setUp() throws Exception {
		slozka = tmp.newFolder("offline-mapy");
		mapy = new OfflineMapy(() -> {});
		mapy.kontrolaSlozkyNs = 0;
		mapy.nastav(slozka, TemaOfflineMapy.VYCHOZI);
	}

	@After
	public void tearDown() {
		mapy.zavri();
	}

	private void otevri() throws Exception {
		mapy.pouzij().skonci();
	}

	@Test
	public void jenPrehledovaMapa() throws Exception {
		Files.copy(new File("distribuce/offline-mapy/prehled-svet-ne.map").toPath(), new File(slozka, "prehled-svet-ne.map").toPath());
		otevri();
		Assert.assertTrue("přehledová mapa má data do z10", mapy.jePodrobnaMapa(KUKOV, 8));
		Assert.assertTrue(mapy.jePodrobnaMapa(KUKOV, 10));
		Assert.assertFalse("od z11 jen šedá plocha", mapy.jePodrobnaMapa(KUKOV, 11));
		Assert.assertFalse(mapy.jePodrobnaMapa(KUKOV, 15));
	}

	@Test
	public void podrobnaMapaJenNaSvemUzemi() throws Exception {
		Files.copy(new File("distribuce/offline-mapy/prehled-svet-ne.map").toPath(), new File(slozka, "prehled-svet-ne.map").toPath());
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		otevri();
		Assert.assertTrue(mapy.jePodrobnaMapa(KUKOV, 15));
		Assert.assertTrue("mapsforge nad maximem jen zvětšuje", mapy.jePodrobnaMapa(KUKOV, 22));
		Assert.assertFalse(mapy.jePodrobnaMapa(VIDEN, 15));
		Assert.assertTrue(mapy.jePodrobnaMapa(VIDEN, 6));
	}

	@Test
	public void bezOtevrenychMapRadaNeni() throws Exception {
		Assert.assertTrue("mapy ještě nejsou otevřené", mapy.jePodrobnaMapa(KUKOV, 15));
		try {
			mapy.pouzij();
			Assert.fail();
		} catch (final java.io.IOException e) {
			// složka bez map: chyba na dlaždicích, rada ne
		}
		Assert.assertTrue(mapy.jePodrobnaMapa(KUKOV, 15));
	}

	private static final Wgs PRAHA = new Wgs(50.08, 14.42);
	private static final Wgs VARSAVA = new Wgs(52.23, 21.01);
	private static final Wgs WROCLAW = new Wgs(51.11, 17.04);
	/** Obdélník stažené mapy Česka zasahuje hluboko do Polska. */
	private static final org.mapsforge.core.model.BoundingBox OBDELNIK_CESKA = new org.mapsforge.core.model.BoundingBox(48.5, 12.0, 53.0, 22.0);

	private static cz.geokuk.plugins.mapy.sprava.PolygonPokryti obrysCeska() {
		return cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny().podleId("europe/czech-republic").get().getPokryti();
	}

	private static OfflineRenderer.Pokryti cesko(final boolean sObrysem) {
		return new OfflineRenderer.Pokryti(OBDELNIK_CESKA, 16, sObrysem ? obrysCeska() : null);
	}

	/** Mapa z katalogu pokrývá jen svůj obrys, ne celý obdélník souboru: střed v bílém čtverci v Polsku rada ukáže. */
	@Test
	public void obrysMistoObdelniku() {
		Assert.assertTrue("střed uvnitř obrysu", cesko(true).obsahuje(PRAHA.lat, PRAHA.lon));
		Assert.assertFalse("bílý čtverec v Polsku mimo obrys", cesko(true).obsahuje(VARSAVA.lat, VARSAVA.lon));
		Assert.assertFalse(cesko(true).obsahuje(WROCLAW.lat, WROCLAW.lon));
	}

	/** Soubor uživatele a mapa bez obrysu mají obdélník souboru. */
	@Test
	public void bezObrysuObdelnik() {
		Assert.assertTrue(cesko(false).obsahuje(VARSAVA.lat, VARSAVA.lon));
		Assert.assertFalse(cesko(false).obsahuje(40.0, 14.0));
	}

	/** U hranice obrysu je malá tolerance, aby rada neblikala přímo na čáře. */
	@Test
	public void tolerancePriHranici() {
		final cz.geokuk.plugins.mapy.sprava.PolygonPokryti obrys = obrysCeska();
		double lat = 51.5;
		while (!obrys.obsahuje(lat, 14.5)) {
			lat -= 0.001;
		}
		final double mimo = lat + 0.02;
		Assert.assertFalse("bod je těsně mimo obrys", obrys.obsahuje(mimo, 14.5));
		Assert.assertTrue("a přesto v toleranci", cesko(true).obsahuje(mimo, 14.5));
		Assert.assertFalse("daleko za hranicí", cesko(true).obsahuje(lat + 0.1, 14.5));
	}

	/** Obrys platí jen uvnitř obdélníku souboru: menší soubor se jménem katalogové mapy nezíská území celé země. */
	@Test
	public void obrysJenUvnitrObdelniku() {
		final OfflineRenderer.Pokryti male = new OfflineRenderer.Pokryti(new org.mapsforge.core.model.BoundingBox(49.9, 14.3, 50.1, 14.5), 16, obrysCeska());
		Assert.assertTrue(male.obsahuje(50.003, 14.405));
		Assert.assertFalse("v obrysu, mimo soubor", male.obsahuje(49.2, 16.6));
	}

	/** Soubor z katalogu: pokrytí podle obrysu z katalogu, ne podle obdélníku souboru. */
	@Test
	public void souborZKatalogu() throws Exception {
		OfflineMapyTest.zkopirujMapu(slozka, "czech-republic.map");
		otevri();
		Assert.assertTrue("Kukov je v obrysu i v souboru", mapy.jePodrobnaMapa(KUKOV, 15));
		Assert.assertFalse("Praha je v obrysu Česka, ale mimo soubor", mapy.jePodrobnaMapa(PRAHA, 15));
		Assert.assertFalse("Varšava není v obrysu", mapy.jePodrobnaMapa(VARSAVA, 15));
	}
}
