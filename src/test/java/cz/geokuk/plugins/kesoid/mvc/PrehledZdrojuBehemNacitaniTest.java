package cz.geokuk.plugins.kesoid.mvc;

import java.io.File;

import org.junit.*;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.importek.InformaceOZdrojich;
import cz.geokuk.util.file.FileAndTime;
import cz.geokuk.util.file.KeFile;
import cz.geokuk.util.file.Root;

/** Přehled zdrojů jde otevřít a zdroj vypnout, ještě než se poprvé načtou keše. */
public class PrehledZdrojuBehemNacitaniTest {

	private final MyPreferences pref = MyPreferences.current().node("test-prehled-zdroju-nacitani");
	private final KesoidModel model = new KesoidModel() {
		@Override
		protected MyPreferences currPrefe() {
			return pref;
		}

		@Override
		public void fire(final Event0<?> udalost) {}
	};

	private final Root root = new Root(new File("/tmp/geoget").getAbsoluteFile(), new Root.Def(1, null, null));
	private final KeFile a = new KeFile(new FileAndTime(new File(root.dir, "a.db3"), 0), root);
	private final KeFile b = new KeFile(new FileAndTime(new File(root.dir, "b.db3"), 0), root);

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	@Test
	public void zdrojJdeVypnoutPredPrvnimNactenim() {
		final InformaceOZdrojich.Builder zdroje = InformaceOZdrojich.builder();
		zdroje.add(a, true);
		zdroje.add(b, true);
		model.setNacitaneZdroje(zdroje.done());

		model.setNacitatSoubor(a, false);
		Assert.assertFalse(model.maSeNacist(a));
		Assert.assertTrue(model.maSeNacist(b));
	}

	@Test
	public void akceSePovoliUzBehemNacitani() {
		final InformaceoZdrojichAction akce = new InformaceoZdrojichAction();
		Assert.assertFalse(akce.isEnabled());
		akce.onEvent(new NacitaneZdrojeEvent(InformaceOZdrojich.builder().done()));
		Assert.assertTrue(akce.isEnabled());
	}
}
