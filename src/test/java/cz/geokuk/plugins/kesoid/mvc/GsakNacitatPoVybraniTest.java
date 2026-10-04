package cz.geokuk.plugins.kesoid.mvc;

import java.io.File;
import java.util.*;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.framework.MyPreferences;
import cz.geokuk.util.file.FileAndTime;
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
	};

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

	private static Set<File> set(final File... f) {
		return new HashSet<>(Arrays.asList(f));
	}
}
