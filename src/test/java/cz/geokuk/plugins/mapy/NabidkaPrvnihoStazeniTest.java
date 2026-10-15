package cz.geokuk.plugins.mapy;

import java.io.File;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.program.FPref;
import cz.geokuk.core.program.PrehledovaMapa;
import cz.geokuk.framework.Event0;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.framework.Prefe;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.sprava.*;

/** Nová instalace bez podrobné mapy nabízí stažení první mapy; po stažení se přepne na offline mapu, ledaže si uživatel zvolil jiný podklad. */
public class NabidkaPrvnihoStazeniTest {

	private static final String NABIDKA = "nabidkaPrvnihoStazeni";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private MapyModel model;
	private String puvodniPodklad;
	private boolean puvodniNabidka;

	private static MyPreferences uzel() {
		return MyPreferences.current().node(FPref.NODE_KTERE_MAPY_node);
	}

	@Before
	public void setUp() {
		puvodniPodklad = uzel().get(FPref.VALUE_MAPOVE_PODKLADY_value, null);
		puvodniNabidka = uzel().getBoolean(NABIDKA, false);
		uzel().remove(FPref.VALUE_MAPOVE_PODKLADY_value);
		uzel().putBoolean(NABIDKA, false);
		model = new MapyModel() {
			@Override
			public void fire(final Event0<?> event) {
				// bez posluchačů
			}
		};
		model.inject(new Prefe());
	}

	@After
	public void tearDown() {
		if (puvodniPodklad == null) {
			uzel().remove(FPref.VALUE_MAPOVE_PODKLADY_value);
		} else {
			uzel().put(FPref.VALUE_MAPOVE_PODKLADY_value, puvodniPodklad);
		}
		uzel().putBoolean(NABIDKA, puvodniNabidka);
	}

	private static SpravaMapEvent hotovo() {
		final KatalogovaMapa mapa = KatalogMap.vestaveny().podleId("europe/czech-republic").get();
		return new SpravaMapEvent(StavMapyProGui.stav(mapa, StavMapy.Mistni.MAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.HOTOVO, 0, -1, null));
	}

	@Test
	public void novaInstalaceBezMapNabidneStazeni() throws Exception {
		model.nastavPodklad(null, tmp.newFolder("prazdna"));
		Assert.assertSame(EKaType.CUZK_ORTO, model.getPodklad());
		Assert.assertTrue(model.isNabidkaPrvnihoStazeni());
	}

	@Test
	public void sPodrobnouMapouNicNenabizi() throws Exception {
		final File slozka = tmp.newFolder("offline-mapy");
		Assert.assertTrue(new File(slozka, PrehledovaMapa.SOUBOR).createNewFile());
		Assert.assertTrue(new File(slozka, "czech_republic.map").createNewFile());
		model.nastavPodklad(null, slozka);
		Assert.assertSame(EKaType.OFFLINE_MF, model.getPodklad());
		Assert.assertFalse(model.isNabidkaPrvnihoStazeni());
	}

	@Test
	public void uzivatelPodkladNesmiNabidkuZpustit() throws Exception {
		model.nastavPodklad(EKaType.TURIST_M.name(), tmp.newFolder("prazdna"));
		Assert.assertSame(EKaType.TURIST_M, model.getPodklad());
		Assert.assertFalse("stávající uživatel nabídku nedostane", model.isNabidkaPrvnihoStazeni());
		model.onEvent(hotovo());
		Assert.assertSame("zvolený podklad se nemění", EKaType.TURIST_M, model.getPodklad());
	}

	@Test
	public void poStazeniSePrepneNaOfflineMapu() throws Exception {
		model.nastavPodklad(null, tmp.newFolder("prazdna"));
		model.onEvent(hotovo());
		Assert.assertSame(EKaType.OFFLINE_MF, model.getPodklad());
		Assert.assertFalse("nabídka se po přepnutí neopakuje", model.isNabidkaPrvnihoStazeni());
		model.setPodklad(EKaType.CUZK_ORTO);
		Assert.assertFalse("ani po návratu na ortofoto", model.isNabidkaPrvnihoStazeni());
	}

	@Test
	public void rozpracovanaMapaNepriepina() throws Exception {
		model.nastavPodklad(null, tmp.newFolder("prazdna"));
		final KatalogovaMapa mapa = KatalogMap.vestaveny().podleId("europe/czech-republic").get();
		model.onEvent(new SpravaMapEvent(StavMapyProGui.stav(mapa, StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.STAHUJE, 100, 600, null)));
		Assert.assertSame(EKaType.CUZK_ORTO, model.getPodklad());
		Assert.assertTrue(model.isNabidkaPrvnihoStazeni());
	}

	@Test
	public void volbaJinehoPodkladuNabidkuZrusi() throws Exception {
		model.nastavPodklad(null, tmp.newFolder("prazdna"));
		model.setPodklad(EKaType.OPEN_STREET);
		model.setPodklad(EKaType.CUZK_ORTO);
		Assert.assertFalse(model.isNabidkaPrvnihoStazeni());
		model.nastavPodklad(null, tmp.newFolder("prazdna2"));
		model.zrusNabidkuPrvnihoStazeni();
		Assert.assertFalse("Nyní ne", model.isNabidkaPrvnihoStazeni());
	}
}
