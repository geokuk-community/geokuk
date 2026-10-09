package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Image;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.DiagnosticsData;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import cz.geokuk.plugins.mapy.kachle.podklady.KaOneReq;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleManager;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleZiskavac;
import cz.geokuk.plugins.mapy.kachle.podklady.KachloStav;
import cz.geokuk.plugins.mapy.kachle.podklady.Kanceler;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;
import cz.geokuk.plugins.mapy.kachle.podklady.TemaOfflineMapy;

/** Předvykreslení okolí přes skutečný získávač dlaždic s malou offline mapou. */
public class PredvykresleniOkoliZiskavacTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private KachleZiskavac ziskavac;
	private final AtomicInteger zadanoPredvykreslenim = new AtomicInteger();

	@Before
	public void setUp() throws Exception {
		final File slozka = tmp.newFolder("offline-mapy");
		try (InputStream in = getClass().getResourceAsStream("/offline-mapy/kukov.map")) {
			Files.copy(in, new File(slozka, "kukov.map").toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
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
				return null;
			}

			@Override
			public Image load(final Ka ki, final String typ) {
				return null;
			}

			@Override
			public int smazOfflineKrome(final java.util.Set<String> ponechat) {
				return 0;
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				return true;
			}
		});
		ziskavac.getOfflineMapy().nastav(slozka, TemaOfflineMapy.VYCHOZI);
	}

	@After
	public void tearDown() {
		ziskavac.getOfflineMapy().zavri();
	}

	private static List<Ka> okoli(final int polomer) {
		final KaLoc stred = KaLoc.ofJZ(new Wgs(50.003, 14.405).toMou(), 15);
		final int krok = 1 << 32 - 15;
		final List<Ka> vysledek = new ArrayList<>();
		for (int i = -polomer; i <= polomer; i++) {
			for (int j = -polomer; j <= polomer; j++) {
				if (i != 0 || j != 0) {
					vysledek.add(new Ka(KaLoc.ofJZ(new Mou(stred.getMouJZ().xx + i * krok, stred.getMouJZ().yy + j * krok), 15), EKaType.OFFLINE_MF));
				}
			}
		}
		return vysledek;
	}

	private PredvykresleniOkoli predvykresleni(final CountDownLatch prvni) {
		return new PredvykresleniOkoli((ka, prijemce) -> {
			zadanoPredvykreslenim.incrementAndGet();
			return ziskavac.ziskejObsah(new KaOneReq(ka, stav -> {
				prvni.countDown();
				prijemce.send(stav);
			}, Priority.KACHLE), DiagnosticsData.create(null, null, null));
		});
	}

	@Test(timeout = 60000)
	public void predvykreslenaDlazdiceSeDoruciOkamzite() throws Exception {
		final List<Ka> okoli = okoli(1);
		final CountDownLatch prvni = new CountDownLatch(okoli.size());
		predvykresleni(prvni).spust(okoli);
		Assert.assertTrue(prvni.await(40, TimeUnit.SECONDS));
		for (final Ka ka : okoli) {
			final CompletableFuture<KachloStav> stav = new CompletableFuture<>();
			ziskavac.ziskejObsah(new KaOneReq(ka, stav::complete, Priority.KACHLE), DiagnosticsData.create(null, null, null));
			Assert.assertTrue("dlaždice " + ka + " má být v paměti", stav.isDone());
		}
	}

	@Test(timeout = 60000)
	public void zrusenePredvykresleniNezdrziViditelnouDlazdiciANepokracuje() throws Exception {
		final CountDownLatch prvni = new CountDownLatch(1);
		final PredvykresleniOkoli p = predvykresleni(prvni);
		p.spust(okoli(3));
		Assert.assertTrue(prvni.await(40, TimeUnit.SECONDS));
		p.zrus();
		final int po = zadanoPredvykreslenim.get();
		final Ka viditelna = new Ka(KaLoc.ofJZ(new Wgs(50.003, 14.405).toMou(), 15), EKaType.OFFLINE_MF);
		final CompletableFuture<KachloStav> stav = new CompletableFuture<>();
		ziskavac.ziskejObsah(new KaOneReq(viditelna, stav::complete, Priority.KACHLE), DiagnosticsData.create(null, null, null));
		Assert.assertNull(stav.get(20, TimeUnit.SECONDS).getThr());
		Thread.sleep(300);
		Assert.assertTrue("po zrušení se nežádá další", zadanoPredvykreslenim.get() <= po + 1);
		Assert.assertTrue(zadanoPredvykreslenim.get() < okoli(3).size());
	}
}
