package cz.geokuk.plugins.mapy.kachle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.Prefe;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboru;
import cz.geokuk.plugins.mapy.ZmenaMapNastalaEvent;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.podklady.*;
import cz.geokuk.util.file.Filex;

/** Zapnutí překryvu tras se ukládá; nad offline mapou s tématem paws je překryv nadbytečný a nekreslí se. */
public class PrekryvTrasNastaveniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final List<PrekryvTrasEvent> udalosti = new ArrayList<>();
	private KachleModel model;
	private TemaOfflineMapy puvodniTema;
	private boolean puvodniPrekryv;

	@Before
	public void setUp() throws Exception {
		model = new KachleModel() {
			@Override
			public void fire(final Event0<?> event) {
				if (event instanceof PrekryvTrasEvent) {
					udalosti.add((PrekryvTrasEvent) event);
				}
			}
		};
		model.inject(new Prefe());
		model.inject(new KachleZiskavac());
		final KachleUmisteniSouboru u = new KachleUmisteniSouboru();
		u.setKachleCacheDir(new Filex(tmp.newFolder("cache"), false, true));
		u.setOfflineMapyDir(new Filex(tmp.newFolder("offline-mapy"), false, true));
		model.setUmisteniSouboru(u);
		puvodniTema = model.getTemaOfflineMapy();
		puvodniPrekryv = model.isPrekryvTras();
		model.setTemaOfflineMapy(TemaOfflineMapy.VYCHOZI);
		model.setPrekryvTras(false);
		model.onEvent(new ZmenaMapNastalaEvent(EKaType.OPEN_STREET));
		udalosti.clear();
	}

	@After
	public void tearDown() {
		model.setTemaOfflineMapy(puvodniTema);
		model.setPrekryvTras(puvodniPrekryv);
	}

	private PrekryvTrasEvent posledni() {
		return udalosti.get(udalosti.size() - 1);
	}

	@Test
	public void zapnutiSeUlozi() {
		model.setPrekryvTras(true);
		Assert.assertTrue(model.isPrekryvTras());
		Assert.assertTrue(posledni().isViditelny());
		Assert.assertEquals("trasy se kreslí ze složky offline map", model.getUmisteniSouboru().getOfflineMapyDir().getEffectiveFile(), model.getZiskavac().getOfflineTrasy().getSlozka());
		Assert.assertSame(TemaOfflineMapy.TRASY, model.getZiskavac().getOfflineTrasy().getTema());
	}

	@Test
	public void nadOfflineMapouPawsSeNekresli() {
		model.setPrekryvTras(true);
		model.setTemaOfflineMapy(TemaOfflineMapy.zeSouboru(new File(tmp.getRoot(), "paws_5.zip"), null));
		Assert.assertTrue("online podklad: téma offline mapy nehraje roli", posledni().isViditelny());

		model.onEvent(new ZmenaMapNastalaEvent(EKaType.OFFLINE_MF));
		Assert.assertTrue(posledni().isNadbytecny());
		Assert.assertFalse(posledni().isViditelny());
		Assert.assertTrue("volba uživatele zůstává", posledni().isZapnuto());

		model.setTemaOfflineMapy(TemaOfflineMapy.VYCHOZI);
		Assert.assertTrue("výchozí téma trasy nekreslí", posledni().isViditelny());

		model.setTemaOfflineMapy(TemaOfflineMapy.zeSouboru(new File(tmp.getRoot(), "paws_5.zip"), null));
		Assert.assertFalse(posledni().isViditelny());
		model.onEvent(new ZmenaMapNastalaEvent(EKaType.CUZK_ORTO));
		Assert.assertTrue(posledni().isViditelny());
	}

	/** Offline podklad s tématem GeoKuk kreslí stažené vrstvy tras sám, překryv je proto nebere (jinak by trasy byly dvakrát přes popisky). */
	@Test
	public void vrstvyTrasKresliTuristickyPodklad() {
		model.setPrekryvTras(true);
		final OfflineMapy trasy = model.getZiskavac().getOfflineTrasy();
		model.setTemaOfflineMapy(TemaOfflineMapy.GEOKUK);
		Assert.assertFalse("online podklad vrstvy tras nekreslí", trasy.isTrasyKresliPodklad());

		model.onEvent(new ZmenaMapNastalaEvent(EKaType.OFFLINE_MF));
		Assert.assertTrue(trasy.isTrasyKresliPodklad());
		Assert.assertTrue("překryv dál kreslí trasy ze základních map", posledni().isViditelny());

		model.setTemaOfflineMapy(TemaOfflineMapy.VYCHOZI);
		Assert.assertFalse("výchozí téma vrstvy tras nekreslí", trasy.isTrasyKresliPodklad());

		model.setTemaOfflineMapy(TemaOfflineMapy.GEOKUK.sVrstvami(VolbaVrstev.AUTOR));
		Assert.assertTrue(trasy.isTrasyKresliPodklad());
		model.onEvent(new ZmenaMapNastalaEvent(EKaType.CUZK_ORTO));
		Assert.assertFalse(trasy.isTrasyKresliPodklad());
	}
}
