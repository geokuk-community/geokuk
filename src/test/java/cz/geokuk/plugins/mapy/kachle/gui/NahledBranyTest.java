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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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
	private final Map<Ka, AtomicInteger> cteniKlice = new java.util.concurrent.ConcurrentHashMap<>();
	private KachleZiskavac ziskavac;
	private KachleModel model;

	@Before
	public void setUp() {
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
				cteniKlice.computeIfAbsent(ki, k -> new AtomicInteger()).incrementAndGet();
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
		// Fronta na pozadí je jednovláknová: dokud ji drží tato úloha, hledání náhledu na disku nezačne a test nezávisí na načasování.
		final CountDownLatch drzi = new CountDownLatch(1);
		ziskavac.naPozadiProNahled(() -> {
			try {
				drzi.await();
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.pripravNahled(model, Priority.KACHLE);
		Assert.assertFalse("z paměti nic nebylo", jkachle.maNahled());
		Assert.assertEquals("disk se zatím nečetl", 0, cteniZDisku.get());
		drzi.countDown();
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

	private static Ka sourozenec(final int vpravo, final int nahoru) {
		return new Ka(LOC.rodic().dite(vpravo, nahoru), EKaType.OPEN_STREET);
	}

	private int cteni(final Ka ka) {
		final AtomicInteger n = cteniKlice.get(ka);
		return n == null ? 0 : n.get();
	}

	@Test(timeout = 30000)
	public void offlineDlazdiceNehledajiNaDisku() throws Exception {
		final Ka offline = new Ka(LOC, EKaType.OFFLINE_MF);
		final Ka rodicOffline = new Ka(LOC.rodic(), EKaType.OFFLINE_MF);
		disk.put(rodicOffline, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		Assert.assertFalse(ziskavac.lzeNahledZCache(offline));
		Assert.assertNull("offline se z disku nečte ani přímo", ziskavac.nahledZPametiNeboCache(rodicOffline));
		final JKachle jkachle = new JKachle(null, offline);
		jkachle.pripravNahled(model, Priority.KACHLE);
		pockejNaFrontu();
		jkachle.doplnNahledZCache(ziskavac);
		Assert.assertFalse(jkachle.maNahled());
		Assert.assertEquals("offline dlaždice nečtou cache na disku", 0, cteniZDisku.get());
	}

	@Test(timeout = 30000)
	public void rodicSeDekodujeJednouProVsechnySourozence() throws Exception {
		disk.put(RODIC, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		for (int v = 0; v <= 1; v++) {
			for (int n = 0; n <= 1; n++) {
				final JKachle jkachle = new JKachle(null, sourozenec(v, n));
				jkachle.doplnNahledZCache(ziskavac);
				Assert.assertTrue("sourozenec " + v + "," + n + " má náhled", jkachle.maNahled());
			}
		}
		Assert.assertEquals("rodič se z cache načetl a dekódoval jednou", 1, cteni(RODIC));
	}

	@Test(timeout = 30000)
	public void chybejiciRodicSeProSourozenceNehledaZnovu() throws Exception {
		for (int v = 0; v <= 1; v++) {
			for (int n = 0; n <= 1; n++) {
				new JKachle(null, sourozenec(v, n)).doplnNahledZCache(ziskavac);
			}
		}
		Assert.assertEquals("rodič i prarodič se hledali jednou, ne pro každého sourozence", 1, cteni(RODIC));
		Assert.assertEquals(2, cteniZDisku.get());
	}

	@Test(timeout = 30000)
	public void zCacheSeHledajiJenRodice() throws Exception {
		// Dlaždice z vyššího měřítka (děti) ani rodiče o víc než dvě úrovně výš se z disku nečtou.
		final Ka dite = new Ka(LOC.dite(0, 0), EKaType.OPEN_STREET);
		final Ka predek3 = new Ka(LOC.rodic().rodic().rodic(), EKaType.OPEN_STREET);
		disk.put(dite, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		disk.put(predek3, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.doplnNahledZCache(ziskavac);
		Assert.assertFalse(jkachle.maNahled());
		Assert.assertEquals("dítě se nečetlo", 0, cteni(dite));
		Assert.assertEquals("třetí předek se nečetl", 0, cteni(predek3));
		Assert.assertEquals("jen dva rodiče", 2, cteniZDisku.get());
	}
}
