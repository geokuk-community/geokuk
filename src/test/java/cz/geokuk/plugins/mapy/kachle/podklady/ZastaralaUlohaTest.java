package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Příjemce, který se přihlásí k dlaždici v okamžiku, kdy fronta její úlohu vyřazuje jako zastaralou, dlaždici dostane. */
public class ZastaralaUlohaTest {

	@Test(timeout = 30000)
	public void prijemcePrihlasenyPriVyrazeniZastaraleUlohyDostaneDlazdici() throws Exception {
		final CountDownLatch uvolnit = new CountDownLatch(1);
		final KachleZiskavac ziskavac = new KachleZiskavac();
		ziskavac.inject(new OnofflineModel() {
			@Override
			public boolean isOnlineMode() {
				return true;
			}
		});
		final int zoomDlazdice = 12;
		ziskavac.setKachleManager(new KachleManager() {
			@Override
			public boolean exists(final Ka ki) {
				return false;
			}

			@Override
			public Image load(final Ka ki) {
				if (ki.getLoc().getMoumer() != zoomDlazdice) {
					try {
						uvolnit.await();
					} catch (final InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
				return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				return true;
			}
		});
		final DiagnosticsData diag = DiagnosticsData.create(null, null, null);
		final EKaType typ = UzivatelskeMapyPristup.sOmezenymUzemim("zastarala", "http://127.0.0.1:1/{z}/{y}/{x}");
		// všechna vlákna čtení z disku obsadíme, aby úloha zůstala ve frontě
		for (int i = 0; i < 32; i++) {
			ziskavac.ziskejObsah(new KaOneReq(new Ka(KaLoc.ofJZ(new Mou(0x40000000 + i * 0x100000, 0x20000000), 13), typ), stav -> {
			}, Priority.KACHLE), diag);
		}
		final Ka dlazdice = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), zoomDlazdice), typ);
		final CompletableFuture<KachloStav> druhy = new CompletableFuture<>();
		final AtomicBoolean prihlasen = new AtomicBoolean();
		final ImageReceiver zastaraly = new ImageReceiver() {
			@Override
			public void send(final KachloStav kachloStav) {
			}

			@Override
			public long dulezitost() {
				// první vyhodnocení pořadí ve frontě: úloha je zastaralá, ale než ji fronta zahodí, přihlásí se další příjemce
				if (prihlasen.compareAndSet(false, true)) {
					ziskavac.ziskejObsah(new KaOneReq(dlazdice, druhy::complete, Priority.KACHLE), diag);
				}
				return ZASTARALA;
			}
		};
		ziskavac.ziskejObsah(new KaOneReq(dlazdice, zastaraly, Priority.KACHLE), diag);
		uvolnit.countDown();
		Assert.assertNotNull("příjemce musí dostat dlaždici", druhy.get(10, TimeUnit.SECONDS).getImg());
	}
}
