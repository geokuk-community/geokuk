package cz.geokuk.plugins.mapy;

import java.io.File;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.program.PrehledovaMapa;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;

/** Nová instalace začíná offline mapou, když ji má, jinak ortofotem ČÚZK; Mapy.cz výchozí nejsou. */
public class VychoziPodkladTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void bezMapOrtofotoCuzk() throws Exception {
		Assert.assertSame(EKaType.CUZK_ORTO, MapyModel.vychoziPodklad(tmp.newFolder("prazdna")));
		Assert.assertSame("složka ještě neexistuje", EKaType.CUZK_ORTO, MapyModel.vychoziPodklad(new File(tmp.getRoot(), "neni")));
	}

	@Test
	public void jenPrehledovaMapaNestaci() throws Exception {
		final File slozka = tmp.newFolder("offline-mapy");
		Assert.assertTrue(new File(slozka, PrehledovaMapa.SOUBOR).createNewFile());
		Assert.assertSame(EKaType.CUZK_ORTO, MapyModel.vychoziPodklad(slozka));
	}

	@Test
	public void sPodrobnouMapouOfflineMapa() throws Exception {
		final File slozka = tmp.newFolder("offline-mapy");
		Assert.assertTrue(new File(slozka, PrehledovaMapa.SOUBOR).createNewFile());
		Assert.assertTrue(new File(slozka, "czech_republic_gccz.MAP").createNewFile());
		Assert.assertSame(EKaType.OFFLINE_MF, MapyModel.vychoziPodklad(slozka));
	}
}
