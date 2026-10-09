package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Dlaždice offline mapy se hledá v cache pod klíčem map a tématu, jinak se vykreslí a uloží; režim bez sítě na ni nemá vliv. */
public class OfflineKachleZiskavacTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final Ka KACHLE = new Ka(OfflineMapyTest.STRED_Z15, EKaType.OFFLINE_MF);

	private final Map<String, Image> disk = new ConcurrentHashMap<>();
	private final BlockingQueue<KachleManager.ItemToSave> ulozene = new LinkedBlockingQueue<>();
	private final List<String> hledaneTypy = Collections.synchronizedList(new ArrayList<>());
	private File slozka;
	private KachleZiskavac ziskavac;

	@Before
	public void setUp() throws Exception {
		slozka = tmp.newFolder("offline-mapy");
		ziskavac = new KachleZiskavac();
		ziskavac.inject(new OnofflineModel() {
			@Override
			public boolean isOnlineMode() {
				return false;
			}
		});
		ziskavac.inject(new KachleModel() {
			@Override
			public boolean isUkladatMapyNaDisk() {
				return true;
			}
		});
		ziskavac.setKachleManager(new KachleManager() {
			@Override
			public boolean exists(final Ka ki) {
				return false;
			}

			@Override
			public Image load(final Ka ki) {
				throw new AssertionError("offline mapa se hledá s typem");
			}

			@Override
			public Image load(final Ka ki, final String typ) {
				hledaneTypy.add(typ);
				return disk.get(ki.getLoc() + typ);
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				ulozene.addAll(itemsToSave);
				return true;
			}
		});
		ziskavac.getOfflineMapy().nastav(slozka, TemaOfflineMapy.VYCHOZI);
	}

	@After
	public void tearDown() {
		ziskavac.getOfflineMapy().zavri();
	}

	private KachloStav ziskej() throws Exception {
		final CompletableFuture<KachloStav> stav = new CompletableFuture<>();
		ziskavac.ziskejObsah(new KaOneReq(KACHLE, stav::complete, Priority.KACHLE), DiagnosticsData.create(null, null, null));
		return stav.get(20, TimeUnit.SECONDS);
	}

	@Test(timeout = 30000)
	public void vykresliUlozASpravnymKlicemNajde() throws Exception {
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final KachloStav stav = ziskej();
		Assert.assertNull(stav.getThr());
		Assert.assertEquals(256, stav.getImg().getWidth(null));

		final KachleManager.ItemToSave ulozena = ulozene.poll(15, TimeUnit.SECONDS);
		Assert.assertNotNull("vykreslená dlaždice se uloží", ulozena);
		Assert.assertTrue(ulozena.typ, ulozena.typ.matches("o[0-9a-f]{8}"));
		Assert.assertEquals(KACHLE, ulozena.key);
		Assert.assertTrue(ulozena.imageData.length > 0);
		Assert.assertEquals(Collections.singletonList(ulozena.typ), hledaneTypy);

		final Image zDisku = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		disk.put(KACHLE.getLoc() + ulozena.typ, zDisku);
		ziskavac.clearMemoryCache();
		Assert.assertSame(zDisku, ziskej().getImg());
	}

	@Test
	public void vlaknaPodleJader() {
		Assert.assertEquals(1, KachleZiskavac.pocetVlakenRenderu(1));
		Assert.assertEquals(2, KachleZiskavac.pocetVlakenRenderu(2));
		Assert.assertEquals(3, KachleZiskavac.pocetVlakenRenderu(4));
		Assert.assertEquals(4, KachleZiskavac.pocetVlakenRenderu(8));
		Assert.assertEquals(4, KachleZiskavac.pocetVlakenRenderu(32));
	}

	/** Dlaždice, které po posunu nebo zoomu nikdo nechce, se zruší dřív, než se vykreslí. */
	@Test(timeout = 30000)
	public void zruseneDlazdiceSeNevykresli() throws Exception {
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		ziskavac.getOfflineMapy().predpriprav();
		final List<Kanceler> zrusit = new ArrayList<>();
		final KaLoc stred = KaLoc.ofJZ(new cz.geokuk.core.coordinates.Wgs(50.0, 14.405).toMou(), 18);
		final OfflineRenderer r = ziskavac.getOfflineMapy().pouzij();
		for (int i = 0; i < 100; i++) {
			final KaLoc loc = KaLoc.ofJZ(new cz.geokuk.core.coordinates.Mou(stred.getMouJZ().xx + (i % 10 - 5) * (1 << 14), stred.getMouJZ().yy + (i / 10 - 5) * (1 << 14)), 18);
			Assert.assertTrue("dlaždice v mapě se ukládají", r.pokryva(loc));
			zrusit.add(ziskavac.ziskejObsah(new KaOneReq(new Ka(loc, EKaType.OFFLINE_MF), stav -> {}, Priority.KACHLE), DiagnosticsData.create(null, null, null)));
		}
		r.skonci();
		for (final Kanceler k : zrusit) {
			k.cancel();
		}
		Assert.assertNull(ziskej().getThr());
		Thread.sleep(6000); // ukládání na disk se spouští po 5 s
		final int ulozeno = ulozene.size();
		Assert.assertTrue("vykresleno a uloženo " + ulozeno + " ze 100 zrušených", ulozeno < 30);
	}

	@Test(timeout = 30000)
	public void bezMapyChybaSeSlozkou() throws Exception {
		final Throwable chyba = ziskej().getThr();
		Assert.assertTrue(String.valueOf(chyba), chyba instanceof IOException);
		Assert.assertTrue(chyba.getMessage(), chyba.getMessage().contains(slozka.toString()));
	}
}
