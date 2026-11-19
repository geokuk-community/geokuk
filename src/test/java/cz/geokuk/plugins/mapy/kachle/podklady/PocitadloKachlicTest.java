package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Počet kachlic se snižuje při odstranění z paměťové cache, ne až při úklidu garbage collectorem. */
public class PocitadloKachlicTest {

	@Test(timeout = 30000)
	public void pocetKachlicSeSnizujeVycistenimPametoveCache() throws Exception {
		final KachleZiskavac ziskavac = new KachleZiskavac();
		ziskavac.inject(new OnofflineModel() {
			@Override
			public boolean isOnlineMode() {
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
				return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				return true;
			}
		});
		final EKaType typ = UzivatelskeMapyPristup.sOmezenymUzemim("pocitadlo", "http://127.0.0.1:1/{z}/{y}/{x}");
		final int pred = ziskavac.pocitKachlice.get();
		for (int i = 0; i < 5; i++) {
			final CompletableFuture<KachloStav> stav = new CompletableFuture<>();
			ziskavac.ziskejObsah(new KaOneReq(new Ka(KaLoc.ofJZ(new Mou(0x40000000 + i * 0x100000, 0x20000000), 12), typ), stav::complete, Priority.KACHLE), DiagnosticsData.create(null, null, null));
			Assert.assertNotNull(stav.get(10, TimeUnit.SECONDS).getImg());
		}
		Assert.assertEquals(pred + 5, ziskavac.pocitKachlice.get());
		ziskavac.clearMemoryCache();
		Assert.assertEquals(pred, ziskavac.pocitKachlice.get());
	}
}
