package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.core.coord.PrvniNacitaniMapy;
import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import cz.geokuk.plugins.mapy.kachle.data.DiagnosticsData;
import cz.geokuk.plugins.mapy.kachle.podklady.KaOneReq;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleManager;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleZiskavac;
import cz.geokuk.plugins.mapy.kachle.podklady.KachloStav;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;

/** Kdy se hledá náhled dlaždice a kde: brána podle priority, paměť před diskem, zastaralé dlaždice, fronta na pozadí. */
public class NahledBranyTest {

	private static final KaLoc LOC = KaLoc.ofJZ(new Mou(0x23800000, 0x28100000), 13);
	private static final Ka KA = new Ka(LOC, EKaType.OPEN_STREET);
	private static final Ka RODIC = new Ka(LOC.rodic(), EKaType.OPEN_STREET);

	private final Map<Ka, Image> disk = new HashMap<>();
	private final AtomicInteger cteniZDisku = new AtomicInteger();
	private KachleZiskavac ziskavac;
	private KachleModel model;

	@Before
	public void setUp() {
		// Mimo první načítání mapy a bez prodlevy; prodleva a první načítání mají vlastní testy.
		PrvniNacitaniMapy.ukonci();
		JKachle.prodlevaNahleduZCacheMs = 0;
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
				return false;
			}
		});
		ziskavac.setKachleManager(new KachleManager() {
			@Override
			public boolean exists(final Ka ki) {
				return false;
			}

			@Override
			public Image load(final Ka ki) {
				cteniZDisku.incrementAndGet();
				return disk.get(ki);
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				return true;
			}
		});
		model = new KachleModel() {
			@Override
			public KachleZiskavac getZiskavac() {
				return ziskavac;
			}
		};
	}

	@After
	public void tearDown() {
		JKachle.prodlevaNahleduZCacheMs = 150;
		PrvniNacitaniMapy.obnovProTesty();
	}

	/** Rodič je po tomto volání v paměťové cache (v režimu bez sítě dostane prázdný obrázek). */
	private void rodicDoPameti() throws Exception {
		ziskavac.ziskejObsah(new KaOneReq(RODIC, stav -> {
		}, Priority.KACHLE), DiagnosticsData.create(null, null, null));
		final long konec = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (ziskavac.nahledZPameti(RODIC) == null && System.nanoTime() < konec) {
			Thread.sleep(5);
		}
		Assert.assertNotNull("rodič v paměti", ziskavac.nahledZPameti(RODIC));
		cteniZDisku.set(0);
	}

	/** Počká, až doběhnou úlohy náhledů zadané dosud (fronta je jednovláknová). */
	private void pockejNaFrontu() throws Exception {
		final CountDownLatch hotovo = new CountDownLatch(1);
		ziskavac.naPozadiProNahled(hotovo::countDown);
		Assert.assertTrue(hotovo.await(10, TimeUnit.SECONDS));
	}

	@Test(timeout = 30000)
	public void onlineDlazdiceDostanouNahledZPameti() throws Exception {
		Assert.assertFalse(EKaType.OPEN_STREET.isOffline());
		rodicDoPameti();
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.pripravNahled(model, Priority.KACHLE);
		Assert.assertTrue(jkachle.maNahled());
	}

	@Test(timeout = 30000)
	public void davkoveStahovaniNahledNepripravuje() throws Exception {
		rodicDoPameti();
		disk.put(RODIC, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.pripravNahled(model, Priority.STAHOVANI);
		pockejNaFrontu();
		Assert.assertFalse(jkachle.maNahled());
		Assert.assertEquals("na pozadí se nehledalo ani na disku", 0, cteniZDisku.get());
	}

	@Test(timeout = 30000)
	public void diskSeNezkusiKdyzPametDalaNahled() throws Exception {
		rodicDoPameti();
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.pripravNahled(model, Priority.KACHLE);
		pockejNaFrontu();
		Assert.assertTrue(jkachle.maNahled());
		Assert.assertEquals("paměť stačila, disk se nečetl", 0, cteniZDisku.get());
	}

	@Test(timeout = 30000)
	public void diskSeZkusiKdyzPametNicNedala() throws Exception {
		disk.put(RODIC, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.pripravNahled(model, Priority.KACHLE);
		Assert.assertFalse("z paměti nic nebylo", jkachle.maNahled());
		pockejNaFrontu();
		Assert.assertTrue(jkachle.maNahled());
		Assert.assertTrue(cteniZDisku.get() > 0);
	}

	@Test(timeout = 30000)
	public void zastaralaDlazdiceNahledNehleda() throws Exception {
		disk.put(RODIC, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		// Dlaždice, která už není v kachlovníku (nemá rodiče), je zastaralá.
		final JKachle jkachle = new JKachle(new JKachlovnikPresCele(), KA);
		jkachle.doplnNahledZCache(ziskavac);
		Assert.assertFalse(jkachle.maNahled());
		Assert.assertEquals(0, cteniZDisku.get());
	}

	@Test(timeout = 30000)
	public void hotovaDlazdiceNahledNehleda() throws Exception {
		disk.put(RODIC, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.prijmi(new KachloStav(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)), Priority.KACHLE);
		jkachle.doplnNahledZCache(ziskavac);
		Assert.assertFalse(jkachle.maNahled());
		Assert.assertEquals(0, cteniZDisku.get());
	}

	@Test(timeout = 30000)
	public void prePlnouFrontuSeZahodiNejstarsi() throws Exception {
		final CountDownLatch drzi = new CountDownLatch(1);
		final CountDownLatch bezi = new CountDownLatch(1);
		ziskavac.naPozadiProNahled(() -> {
			bezi.countDown();
			try {
				drzi.await();
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		Assert.assertTrue(bezi.await(10, TimeUnit.SECONDS));
		final List<Integer> provedene = java.util.Collections.synchronizedList(new ArrayList<>());
		final int zadano = 80;
		for (int i = 1; i <= zadano; i++) {
			final int cislo = i;
			ziskavac.naPozadiProNahled(() -> provedene.add(cislo));
		}
		drzi.countDown();
		pockejNaFrontu();
		Assert.assertTrue("nejnovější platí", provedene.contains(zadano));
		Assert.assertFalse("nejstarší se zahodila", provedene.contains(1));
		Assert.assertTrue("fronta je omezená: " + provedene.size(), provedene.size() <= 64);
	}

	@Test(timeout = 30000)
	public void priPrvnimNacitaniMapySeCacheNaDiskuNehleda() throws Exception {
		PrvniNacitaniMapy.obnovProTesty();
		Assert.assertTrue("první načítání probíhá", PrvniNacitaniMapy.probiha());
		disk.put(RODIC, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.pripravNahled(model, Priority.KACHLE);
		pockejNaFrontu();
		Assert.assertFalse(jkachle.maNahled());
		Assert.assertEquals("při prvním načítání se disk nečetl", 0, cteniZDisku.get());
	}

	@Test(timeout = 30000)
	public void dlazdiceKteraDorazDrivNeZProdlevaSeCacheNehleda() throws Exception {
		JKachle.prodlevaNahleduZCacheMs = 300;
		disk.put(RODIC, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.pripravNahled(model, Priority.KACHLE);
		jkachle.prijmi(new KachloStav(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)), Priority.KACHLE);
		Thread.sleep(700);
		pockejNaFrontu();
		Assert.assertEquals("dlaždice dorazila před koncem prodlevy, disk se nečetl", 0, cteniZDisku.get());
		Assert.assertFalse(jkachle.maNahled());
	}

	@Test(timeout = 30000)
	public void dlazdiceKtereSeCekaDelePoProdleveDostaneNahledZCache() throws Exception {
		JKachle.prodlevaNahleduZCacheMs = 200;
		disk.put(RODIC, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.pripravNahled(model, Priority.KACHLE);
		Assert.assertEquals("hned se disk nečte", 0, cteniZDisku.get());
		final long konec = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!jkachle.maNahled() && System.nanoTime() < konec) {
			Thread.sleep(10);
		}
		Assert.assertTrue("po prodlevě se náhled objeví", jkachle.maNahled());
	}
}
