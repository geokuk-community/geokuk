package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.imageio.ImageIO;

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
	private final BlockingQueue<Set<String>> uklizeno = new LinkedBlockingQueue<>();
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
			public int smazOfflineKrome(final Set<String> ponechat) {
				uklizeno.add(ponechat);
				return 0;
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
		ziskavac.getOfflineTrasy().zavri();
	}

	private KachloStav ziskej() throws Exception {
		return ziskej(KACHLE);
	}

	private KachloStav ziskej(final Ka ka) throws Exception {
		final CompletableFuture<KachloStav> stav = new CompletableFuture<>();
		ziskavac.ziskejObsah(new KaOneReq(ka, stav::complete, Priority.KACHLE), DiagnosticsData.create(null, null, null));
		return stav.get(20, TimeUnit.SECONDS);
	}

	/** Překryv tras se kreslí ze stejné složky map vlastním tématem a ukládá pod vlastním klíčem. */
	@Test(timeout = 30000)
	public void prekryvTrasSeVykresliAUlozi() throws Exception {
		ziskavac.getOfflineTrasy().nastav(slozka, TemaOfflineMapy.TRASY);
		PrekryvTrasTest.zkopirujMapu(slozka);
		final Ka trasy = new Ka(PrekryvTrasTest.PESINA_Z16, EKaType.TRASY);
		final KachloStav stav = ziskej(trasy);
		Assert.assertNull(stav.getThr());
		Assert.assertTrue(((BufferedImage) stav.getImg()).getColorModel().hasAlpha());
		final KachleManager.ItemToSave ulozena = ulozene.poll(15, TimeUnit.SECONDS);
		Assert.assertNotNull("vykreslená dlaždice se uloží", ulozena);
		Assert.assertEquals(trasy, ulozena.key);
		Assert.assertTrue(ulozena.typ, ulozena.typ.matches("t[0-9a-f]{8}\\.[0-9a-f]{8}"));
	}

	/** Bez map překryv nic nekreslí a chybu neukazuje, tu má jen offline mapa jako podklad. */
	@Test(timeout = 30000)
	public void prekryvTrasBezMapJePruhlednyBezChyby() throws Exception {
		ziskavac.getOfflineTrasy().nastav(slozka, TemaOfflineMapy.TRASY);
		final int render = ziskavac.pocitRenderError.get();
		final KachloStav stav = ziskej(new Ka(PrekryvTrasTest.PESINA_Z16, EKaType.TRASY));
		Assert.assertNull(String.valueOf(stav.getThr()), stav.getThr());
		final BufferedImage img = (BufferedImage) stav.getImg();
		for (final int p : img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth())) {
			Assert.assertEquals(0, p >>> 24);
		}
		Assert.assertEquals(render, ziskavac.pocitRenderError.get());
	}

	@Test(timeout = 30000)
	public void vykresliUlozASpravnymKlicemNajde() throws Exception {
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final KachloStav stav = ziskej();
		Assert.assertNull(stav.getThr());
		Assert.assertEquals(256, stav.getImg().getWidth(null));

		final KachleManager.ItemToSave ulozena = ulozene.poll(15, TimeUnit.SECONDS);
		Assert.assertNotNull("vykreslená dlaždice se uloží", ulozena);
		Assert.assertTrue(ulozena.typ, ulozena.typ.matches("o[0-9a-f]{8}\\.[0-9a-f]{8}"));
		Assert.assertEquals(KACHLE, ulozena.key);
		final BufferedImage zobrazena = (BufferedImage) stav.getImg();
		final BufferedImage png = ImageIO.read(new ByteArrayInputStream(ulozena.imageData));
		Assert.assertArrayEquals("uloží se PNG zobrazené dlaždice", zobrazena.getRGB(0, 0, 256, 256, null, 0, 256), png.getRGB(0, 0, 256, 256, null, 0, 256));
		Assert.assertEquals(Collections.singletonList(ulozena.typ), hledaneTypy);

		final Image zDisku = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		disk.put(KACHLE.getLoc() + ulozena.typ, zDisku);
		ziskavac.clearMemoryCache();
		Assert.assertSame(zDisku, ziskej().getImg());
	}

	/** Po otevření mapy s novým klíčem se zapíše soubor klíčů a uklidí symboly starých klíčů; dlaždice se z cache nemažou. */
	@Test(timeout = 30000)
	public void poOtevreniSeUkliziCache() throws Exception {
		final File symboly = tmp.newFolder("offline-temata");
		ziskavac.getOfflineMapy().setSlozkaSymbolu(symboly);
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final OfflineRenderer r = ziskavac.getOfflineMapy().pouzij();
		final String klic = r.getKlic();
		r.skonci();
		// Soubor klíčů se zapisuje až po smazání dlaždic.
		final File klice = new File(symboly, UklidOfflineCache.SOUBOR);
		final long konec = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
		while (!klice.isFile() && System.nanoTime() < konec) {
			Thread.sleep(20);
		}
		Assert.assertTrue(klice.isFile());
		Assert.assertEquals(Collections.singletonList(klic + " " + r.getSouborSymbolu().getName() + " " + r.getOblastniKlic().getSestava() + " " + r.getOblastniKlic().getIdentita()),
				java.nio.file.Files.readAllLines(klice.toPath(), java.nio.charset.StandardCharsets.UTF_8));
		Assert.assertTrue("dlaždice se při úklidu z cache nemažou", uklizeno.isEmpty());
	}

	@Test
	public void vlaknaPodleJader() {
		Assert.assertEquals(1, KachleZiskavac.pocetVlakenRenderu(1));
		Assert.assertEquals(2, KachleZiskavac.pocetVlakenRenderu(2));
		Assert.assertEquals(3, KachleZiskavac.pocetVlakenRenderu(4));
		Assert.assertEquals(5, KachleZiskavac.pocetVlakenRenderu(6));
		Assert.assertEquals(6, KachleZiskavac.pocetVlakenRenderu(8));
		Assert.assertEquals(6, KachleZiskavac.pocetVlakenRenderu(12));
		Assert.assertEquals(6, KachleZiskavac.pocetVlakenRenderu(32));
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
		final int chybPred = cz.geokuk.framework.ChybyVDiagnostice.pocet("offline mapy");
		final Throwable chyba = ziskej().getThr();
		Assert.assertTrue(String.valueOf(chyba), chyba instanceof IOException);
		Assert.assertTrue(chyba.getMessage(), chyba.getMessage().contains(slozka.toString()));
		Assert.assertFalse("chybějící mapy jsou běžný stav, ne chyba v Diagnostice", cz.geokuk.framework.ChybyVDiagnostice.pribude("offline mapy", chybPred));
	}

	/** Nečitelná mapa je chyba vykreslení, ne čtení cache; po zkopírování mapy se dlaždice vykreslí. */
	@Test(timeout = 30000)
	public void bezMapyJenChybaVykresleniPakSeVykresli() throws Exception {
		ziskavac.getOfflineMapy().kontrolaSlozkyNs = 0;
		final int disk = ziskavac.pocitDiskLoadError.get();
		final int render = ziskavac.pocitRenderError.get();
		Assert.assertNotNull(ziskej().getThr());
		Assert.assertEquals("chyba čtení cache", disk, ziskavac.pocitDiskLoadError.get());
		Assert.assertEquals("chyba vykreslení", render + 1, ziskavac.pocitRenderError.get());

		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final KachloStav stav = ziskej();
		Assert.assertNull(stav.getThr());
		Assert.assertNotNull(stav.getImg());
	}

	/** Předvykreslení přehledové dlaždice se počítá zvlášť, ať neposouvá p95 a max vykreslení dlaždic na obrazovce. */
	@Test(timeout = 30000)
	public void predvykresleniPrehleduSePocitaZvlast() throws Exception {
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		cz.geokuk.core.napoveda.Vykon.vynuluj();
		final OfflineRenderer r = ziskavac.getOfflineMapy().pouzij();
		final String klic;
		try {
			klic = r.getKlic() + '.' + r.getOblastniKlic().getSestava();
		} finally {
			r.skonci();
		}
		Assert.assertTrue(ziskavac.mapyProPredvykresleniPrehledu().vykresli(OfflineMapyTest.STRED_Z15, klic));
		Assert.assertEquals("nezapisuje se mezi dlaždice na obrazovce", 0, cz.geokuk.core.napoveda.Vykon.souhrn(cz.geokuk.core.napoveda.Vykon.Velicina.VYKRESLENI_OFFLINE).pocet);
		Assert.assertEquals(1, cz.geokuk.core.napoveda.Vykon.souhrn(cz.geokuk.core.napoveda.Vykon.Velicina.VYKRESLENI_PREHLEDU).pocet);
	}
}
