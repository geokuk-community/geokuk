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

	/** Výběr vrstev je uložený pod klíčem z textu v nastavení (relativně ke složce programu), takže přesun složky programu ho nezmění. */
	@Test
	public void vrstvyPrezijiPresun() {
		final File zip = new File(FConst.KOREN.getAbsoluteFile(), "data/offline-mapy/paws_5.zip");
		final TemaOfflineMapy tema = TemaOfflineMapy.zeSouboru(zip, "paws.xml");
		Assert.assertEquals(TemaOfflineMapy.klicNastaveni(MyPreferences.ZNACKA_KORENE + "/data/offline-mapy/paws_5.zip|paws.xml"), KachleModel.klicVrstev(tema));
		Assert.assertEquals("vestavěné téma má klíč jako dřív", TemaOfflineMapy.zTextu("OSMARENDER").klicNastaveni(), KachleModel.klicVrstev(TemaOfflineMapy.zTextu("OSMARENDER")));
	}

	/** V modelu: výběr vrstev uložený k tématu ve složce programu se po načtení z nastavení vrátí. */
	@Test
	public void vrstvyVModelu() throws Exception {
		final File slozka = new File(FConst.KOREN.getAbsoluteFile(), "target/tema-presun-test");
		Assert.assertTrue(slozka.isDirectory() || slozka.mkdirs());
		final File zip = new File(slozka, "paws_5.zip");
		Assert.assertTrue(zip.isFile() || zip.createNewFile());
		final KachleModel model = new KachleModel() {
			@Override
			public void fire(final cz.geokuk.framework.Event0<?> event) {
				// bez správce událostí
			}
		};
		model.inject(new cz.geokuk.framework.Prefe());
		model.inject(new cz.geokuk.plugins.mapy.kachle.podklady.KachleZiskavac());
		final cz.geokuk.plugins.mapy.KachleUmisteniSouboru u = new cz.geokuk.plugins.mapy.KachleUmisteniSouboru();
		u.setKachleCacheDir(new cz.geokuk.util.file.Filex(tmp.newFolder("cache"), false, true));
		u.setOfflineMapyDir(new cz.geokuk.util.file.Filex(tmp.newFolder("offline-mapy"), false, true));
		model.setUmisteniSouboru(u);
		final TemaOfflineMapy puvodni = model.getTemaOfflineMapy();
		try {
			model.setTemaOfflineMapy(TemaOfflineMapy.zeSouboru(zip, null));
			final cz.geokuk.plugins.mapy.kachle.podklady.VolbaVrstev volba = cz.geokuk.plugins.mapy.kachle.podklady.VolbaVrstev.AUTOR.sVrstvou("vrstevnice", false, true);
			model.setVrstvyTematu(volba);
			Assert.assertEquals(volba, model.getTemaOfflineMapy().getVrstvy());
			Assert.assertEquals("uloženo pod klíčem relativního textu", volba.naText(),
					cz.geokuk.framework.MyPreferences.current().node("offlineMapaVrstvy").get(TemaOfflineMapy.klicNastaveni(MyPreferences.ZNACKA_KORENE + "/target/tema-presun-test/paws_5.zip"), null));
			model.setVrstvyTematu(cz.geokuk.plugins.mapy.kachle.podklady.VolbaVrstev.AUTOR);
		} finally {
			model.setTemaOfflineMapy(puvodni);
		}
	}
}
