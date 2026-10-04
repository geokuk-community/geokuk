package cz.geokuk.plugins.kesoid.mvc;

import java.io.File;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.MyPreferences;
import cz.geokuk.util.file.FileAndTime;
import cz.geokuk.util.file.Filex;
import cz.geokuk.util.file.KeFile;
import cz.geokuk.util.file.Root;

/** „Načítat až po vybrání“: databázi GSAKu, kterou GeoKuk ještě neviděl, načte až po vybrání uživatelem. */
public class GsakNacitatPoVybraniTest {

	private final MyPreferences pref = MyPreferences.current().node("test-gsak-po-vybrani");
	private final GsakParametryNacitani parametry = new GsakParametryNacitani();
	private final KesoidModel model = new KesoidModel() {
		@Override
		protected MyPreferences currPrefe() {
			return pref;
		}

		@Override
		public GsakParametryNacitani getGsakParametryNacitani() {
			return parametry;
		}

		@Override
		public KesoidUmisteniSouboru getUmisteniSouboru() {
			return umisteni;
		}
	};
	private KesoidUmisteniSouboru umisteni;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final File a = new File("/tmp/gsak/data/Default/sqlite.db3").getAbsoluteFile();
	private final File b = new File("/tmp/gsak/data/Nova/sqlite.db3").getAbsoluteFile();

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	private boolean nacte(final File f) {
		return model.maSeNacist(new KeFile(new FileAndTime(f, 0), new Root(f.getParentFile().getParentFile(), new Root.Def(2, null, null))));
	}

	@Test
	public void prvniProhledaniNicNezakaze() {
		parametry.setNacistVsechnyDatabaze(false);
		model.zaradGsakDatabaze(set(a));
		Assert.assertTrue(nacte(a));
	}

	@Test
	public void novaDatabazeVGsakuSeNenacte() {
		parametry.setNacistVsechnyDatabaze(false);
		model.zaradGsakDatabaze(set(a));
		model.zaradGsakDatabaze(set(a, b));
		Assert.assertTrue(nacte(a));
		Assert.assertFalse(nacte(b));
	}

	@Test
	public void poZapnutiGsakuSeDatabazeNenactou() {
		parametry.setNacistVsechnyDatabaze(false);
		model.zaradGsakDatabaze(set(a, b));
		model.zaradGsakDatabaze(set()); // GSAK neaktivní
		model.zaradGsakDatabaze(set(a, b));
		Assert.assertFalse(nacte(a));
		Assert.assertFalse(nacte(b));
	}

	@Test
	public void priNacitaniVsehoSeNicNezakaze() {
		parametry.setNacistVsechnyDatabaze(true);
		model.zaradGsakDatabaze(set(a));
		model.zaradGsakDatabaze(set(a, b));
		Assert.assertTrue(nacte(b));
		parametry.setNacistVsechnyDatabaze(false);
		model.zaradGsakDatabaze(set(a, b));
		Assert.assertTrue(nacte(b));
	}

	/** Zablokovaná databáze zůstane zablokovaná, i když byla složka GSAKu chvíli nedostupná. */
	@Test
	public void blokovanaZustanePoNedostupneSlozce() throws Exception {
		final File slozka = new File(tmp.getRoot(), "gsak");
		final File stara = new File(slozka, "Default/sqlite.db3");
		final File treti = new File(slozka, "Treti/sqlite.db3");
		umisteni = new KesoidUmisteniSouboru();
		umisteni.setKesDir(new Filex(tmp.newFolder("gpx"), false, true));
		umisteni.setGeogetDataDir(new Filex(new File(tmp.getRoot(), "geoget"), false, false));
		umisteni.setGsakDataDir(new Filex(slozka, false, true));
		parametry.setNacistVsechnyDatabaze(false);
		model.zaradGsakDatabaze(set(stara));
		model.zaradGsakDatabaze(set(stara, treti));
		Assert.assertFalse(nacte(treti));

		model.vycistiBlokovaneZdroje(set()); // složka nedostupná, nic se nenačetlo
		Assert.assertFalse(nacte(treti));

		Assert.assertTrue(new File(slozka, "Treti").mkdirs());
		model.vycistiBlokovaneZdroje(set(stara, treti));
		Assert.assertFalse(nacte(treti));

		model.vycistiBlokovaneZdroje(set(stara)); // databáze ve složce, která je k dispozici, zmizela
		Assert.assertTrue(nacte(treti));
	}

	/** Neplatná cesta k aktivní složce (ručně upravené nastavení) nesmí shodit úklid zablokovaných zdrojů. */
	@Test
	public void neplatnaSlozkaNevadi() throws Exception {
		umisteni = new KesoidUmisteniSouboru();
		umisteni.setKesDir(new Filex(new File(tmp.getRoot(), "a\0b"), false, true));
		umisteni.setGeogetDataDir(new Filex(new File(tmp.getRoot(), "geoget"), false, false));
		umisteni.setGsakDataDir(new Filex(new File(tmp.getRoot(), "gsak"), false, false));
		parametry.setNacistVsechnyDatabaze(false);
		model.zaradGsakDatabaze(set(a));
		model.zaradGsakDatabaze(set(a, b)); // b je zablokovaná, úklid má co dělat
		model.vycistiBlokovaneZdroje(set());
	}

	private static Set<File> set(final File... f) {
		return new HashSet<>(Arrays.asList(f));
	}
}
