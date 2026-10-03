package cz.geokuk.plugins.mapy.kachle.podklady;

import static org.junit.Assert.assertTrue;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Příjemce dlaždice se smí odhlásit, i když mu ji právě posíláme (tak to dělá rendr). */
public class KachliceOdhlaseniTest {

	private static final Ka KACHLE = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13), EKaType.TURIST_M);

	@Test(timeout = 30000)
	public void ostatniPrijemciDostanouDlazdiciIPoOdhlaseni() throws Exception {
		final CountDownLatch nacteni = new CountDownLatch(1);
		final KachleZiskavac ziskavac = new KachleZiskavac();
		ziskavac.setKachleManager(new KachleManager() {
			@Override
			public boolean exists(final Ka ki) {
				return false;
			}

			@Override
			public Image load(final Ka ki) {
				try {
					nacteni.await();
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				return true;
			}
		});

		final AtomicReference<Kanceler> prvniKanceler = new AtomicReference<>();
		prvniKanceler.set(ziskavac.ziskejObsah(new KaOneReq(KACHLE, stav -> prvniKanceler.get().cancel(), Priority.KACHLE),
				DiagnosticsData.create(null, null, null)));
		final CountDownLatch druhyDostal = new CountDownLatch(1);
		ziskavac.ziskejObsah(new KaOneReq(KACHLE, stav -> druhyDostal.countDown(), Priority.KACHLE), DiagnosticsData.create(null, null, null));

		nacteni.countDown();
		assertTrue("Druhý příjemce dlaždici nedostal", druhyDostal.await(10, TimeUnit.SECONDS));
	}
}
