package cz.geokuk.plugins.mapy.kachle;

import java.io.File;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.Prefe;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboru;
import cz.geokuk.plugins.mapy.kachle.podklady.*;
import cz.geokuk.util.file.Filex;

/** Výběr vrstev se ukládá ke každému tématu zvlášť a po přepnutí témat se vrátí. */
public class VrstvyTematuNastaveniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private KachleModel model;
	private TemaOfflineMapy puvodni;

	@Before
	public void setUp() throws Exception {
		model = new KachleModel() {
			@Override
			public void fire(final Event0<?> event) {
				// bez správce událostí
			}
		};
		model.inject(new Prefe());
		model.inject(new KachleZiskavac());
		final KachleUmisteniSouboru u = new KachleUmisteniSouboru();
		u.setKachleCacheDir(new Filex(tmp.newFolder("cache"), false, true));
		u.setOfflineMapyDir(new Filex(tmp.newFolder("offline-mapy"), false, true));
		model.setUmisteniSouboru(u);
		puvodni = model.getTemaOfflineMapy();
	}

	@After
	public void tearDown() {
		model.setVrstvyTematu(VolbaVrstev.AUTOR);
		model.setTemaOfflineMapy(puvodni);
	}

	@Test
	public void volbaPatriKTematu() throws Exception {
		final TemaOfflineMapy a = TemaOfflineMapy.zeSouboru(new File(tmp.getRoot(), "a.xml"), null);
		final TemaOfflineMapy b = TemaOfflineMapy.zeSouboru(new File(tmp.getRoot(), "b.xml"), null);
		final VolbaVrstev volbaA = VolbaVrstev.AUTOR.seStylem("cyklo", "vychozi").sVrstvou("vrstevnice", false, true);

		model.setTemaOfflineMapy(a);
		model.setVrstvyTematu(volbaA);
		Assert.assertEquals(volbaA, model.getTemaOfflineMapy().getVrstvy());

		model.setTemaOfflineMapy(b);
		Assert.assertTrue(model.getTemaOfflineMapy().stejnyZdroj(b));
		Assert.assertEquals("téma B má svou volbu", VolbaVrstev.AUTOR, model.getTemaOfflineMapy().getVrstvy());
		model.setVrstvyTematu(VolbaVrstev.AUTOR.sVrstvou("domy", true, false));

		model.setTemaOfflineMapy(a);
		Assert.assertEquals("po návratu k A se volba vrátí", volbaA, model.getTemaOfflineMapy().getVrstvy());
		Assert.assertEquals("offline mapy dostanou téma s volbou", volbaA, model.getZiskavac().getOfflineMapy().getTema().getVrstvy());

		model.setVrstvyTematu(VolbaVrstev.AUTOR);
		Assert.assertEquals(VolbaVrstev.AUTOR, model.getTemaOfflineMapy().getVrstvy());
		model.setTemaOfflineMapy(b);
		Assert.assertEquals(VolbaVrstev.AUTOR.sVrstvou("domy", true, false), model.getTemaOfflineMapy().getVrstvy());
	}
}
