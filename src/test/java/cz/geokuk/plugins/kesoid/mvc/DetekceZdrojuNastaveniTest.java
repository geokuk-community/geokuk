package cz.geokuk.plugins.kesoid.mvc;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.program.FPref;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.importek.DetekceZdroju;
import cz.geokuk.util.file.FileAndTime;
import cz.geokuk.util.file.Filex;
import cz.geokuk.util.file.KeFile;
import cz.geokuk.util.file.Root;

/** Při prvním spuštění se nalezený zdroj zapne jen s poslední databází; uložené nastavení zůstane, jak je. */
public class DetekceZdrojuNastaveniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final MyPreferences koren = MyPreferences.current().node("test-detekce-zdroju");
	private GsakParametryNacitani parametry = new GsakParametryNacitani();
	private final KesoidModel model = new KesoidModel() {
		@Override
		protected MyPreferences currPrefe() {
			return koren;
		}

		@Override
		public GsakParametryNacitani getGsakParametryNacitani() {
			return parametry;
		}

		@Override
		public void fire(final cz.geokuk.framework.Event0<?> event) {
			// události tu nikdo neposlouchá
		}
	};

	private DetekceZdroju.Prostredi prostredi;
	private File geoget;
	private File posledni;
	private File jina;

	@Before
	public void priprav() throws Exception {
		final File domov = tmp.newFolder("domov");
		final Map<String, String> promenne = new HashMap<>();
		promenne.put("APPDATA", new File(domov, "AppData/Roaming").getPath());
		promenne.put("SystemDrive", new File(tmp.getRoot(), "c").getPath());
		prostredi = new DetekceZdroju.Prostredi("Windows 11", domov, promenne);
		geoget = Filex.canonize(new File(domov, "AppData/Roaming/GeoGet"));
		Files.createDirectories(geoget.toPath());
		posledni = new File(geoget, "geoget.db3");
		jina = new File(geoget, "Vylet.db3");
		Files.write(posledni.toPath(), new byte[] { 1 });
		Files.write(jina.toPath(), new byte[] { 1 });
	}

	@After
	public void uklid() throws Exception {
		koren.removeNode();
	}

	@Test
	public void nalezenyGeogetSeZapneJenSPosledniDatabazi() throws Exception {
		Files.write(new File(geoget, "geoget.ini").toPath(), "[geoget]\r\nlastDB=geoget\r\n".getBytes("UTF-8"));
		model.detekujZdroje(prostredi);
		final Filex slozka = koren.node(FPref.UMISTENI_SOUBORU_node).getFilex(FPref.GEOGET_DATA_DIR_value, null);
		Assert.assertEquals(geoget, slozka.getEffectiveFile());
		Assert.assertTrue(slozka.isActive());
		parametry = model.loadGsakParametryNacitani();
		Assert.assertFalse("další databáze čekají na výběr", parametry.isNoveDatabazeGeogetuHned());
		model.zaradGeogetDatabaze(set(posledni, jina));
		Assert.assertTrue(nacte(posledni));
		Assert.assertFalse(nacte(jina));
	}

	@Test
	public void bezPosledniDatabazeZapnutyBezVybraneDatabaze() throws Exception {
		model.detekujZdroje(prostredi);
		Assert.assertTrue(koren.node(FPref.UMISTENI_SOUBORU_node).getFilex(FPref.GEOGET_DATA_DIR_value, null).isActive());
		parametry = model.loadGsakParametryNacitani();
		model.zaradGeogetDatabaze(set(posledni, jina));
		Assert.assertFalse(nacte(posledni));
		Assert.assertFalse(nacte(jina));
	}

	@Test
	public void ulozenaSlozkaSeNemeni() throws Exception {
		final MyPreferences umisteni = koren.node(FPref.UMISTENI_SOUBORU_node);
		final File jinde = tmp.newFolder("geoget", "data");
		umisteni.putFilex(FPref.GEOGET_DATA_DIR_value, new Filex(jinde, false, false));
		model.detekujZdroje(prostredi);
		final Filex slozka = umisteni.getFilex(FPref.GEOGET_DATA_DIR_value, null);
		Assert.assertFalse(slozka.isActive());
		Assert.assertEquals(Filex.canonize(jinde), slozka.getEffectiveFile());
		Assert.assertNull(koren.node(FPref.KESOID_node).get(FPref.ZNAME_GEOGET_DATABAZE_value, null));
		Assert.assertNull(koren.node(FPref.GEOGET_node).get(FPref.NOVE_DATABAZE_HNED_value, null));
	}

	@Test
	public void nenalezenyZdrojNicNezapise() throws Exception {
		model.detekujZdroje(prostredi);
		Assert.assertNull(koren.node(FPref.UMISTENI_SOUBORU_node).get(FPref.GSAK_DATA_DIR_value, null));
		Assert.assertNull(koren.node(FPref.UMISTENI_SOUBORU_node).get(FPref.OPENSAK_DATA_DIR_value, null));
		Assert.assertNull(koren.node(FPref.GSAK_node).get(FPref.NOVE_DATABAZE_HNED_value, null));
	}

	private boolean nacte(final File f) {
		return model.maSeNacist(new KeFile(new FileAndTime(f, 0), new Root(f.getParentFile(), new Root.Def(1, null, null))));
	}

	private static Set<File> set(final File... f) {
		return new HashSet<>(Arrays.asList(f));
	}
}
