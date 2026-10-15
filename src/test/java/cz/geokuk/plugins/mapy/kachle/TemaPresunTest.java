package cz.geokuk.plugins.mapy.kachle;

import java.io.File;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.mapy.kachle.podklady.TemaOfflineMapy;

/** Téma offline mapy přežije přesun přenosného GeoKuku: v nastavení je relativně ke složce programu a staré absolutní cesty se dohledají ve složce map. */
public class TemaPresunTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void souborVeSlozceProgramuRelativne() {
		final File zip = new File(FConst.KOREN.getAbsoluteFile(), "data/offline-mapy/paws_5.zip");
		final String text = KachleModel.temaDoNastaveni(TemaOfflineMapy.zeSouboru(zip, "paws_5/paws.xml"));
		Assert.assertEquals(MyPreferences.ZNACKA_KORENE + "/data/offline-mapy/paws_5.zip|paws_5/paws.xml", text);
		final TemaOfflineMapy zpet = KachleModel.temaZNastaveni(text, null);
		Assert.assertEquals(zip.getAbsoluteFile().toPath().normalize().toFile(), zpet.getSoubor().toPath().normalize().toFile());
		Assert.assertEquals("paws_5.zip – paws_5/paws.xml", zpet.getNazev());
	}

	@Test
	public void vestaveneAMimoProgramBezZmeny() throws Exception {
		Assert.assertEquals("OSMARENDER", KachleModel.temaDoNastaveni(TemaOfflineMapy.zTextu("OSMARENDER")));
		final File jinde = tmp.newFile("Elevate.xml");
		Assert.assertEquals(jinde.getPath(), KachleModel.temaDoNastaveni(TemaOfflineMapy.zeSouboru(jinde, null)));
	}

	/** Nastavení z 6.4.0 má absolutní cestu, která po přesunu neexistuje; téma se najde ve složce offline map. */
	@Test
	public void staraAbsolutniCestaPoPresunu() throws Exception {
		final File slozka = tmp.newFolder("offline-mapy");
		final File zip = new File(slozka, "paws_5.zip");
		Assert.assertTrue(zip.createNewFile());
		final String stare = new File(tmp.getRoot(), "puvodni/data/offline-mapy/paws_5.zip").getPath() + "|paws.xml";
		final TemaOfflineMapy tema = KachleModel.temaZNastaveni(stare, slozka);
		Assert.assertEquals(zip, tema.getSoubor());
		Assert.assertEquals("paws_5.zip – paws.xml", tema.getNazev());
		Assert.assertEquals("když ani tam není, zůstane jak je (chybu ukáže kreslení)", new File(tmp.getRoot(), "puvodni/x.zip"),
				KachleModel.temaZNastaveni(new File(tmp.getRoot(), "puvodni/x.zip").getPath(), slozka).getSoubor());
	}
}
