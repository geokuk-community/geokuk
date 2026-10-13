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
}
