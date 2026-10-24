package cz.geokuk.plugins.mapy.sprava;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.mapy.kachle.podklady.KachleZiskavac;

/** Správa map předá offline mapám živé katalogy, ne vestavěné. */
public class ZapojeniKatalogovTest {

	@Test
	public void ziskavacDostaneKatalogySpravyMap() throws Exception {
		final KatalogMap mapy = KatalogyProOfflineTest.mapa("test/kukov", 49.9, 14.3, 50.1, 14.5);
		final KatalogVrstev vrstvy = KatalogyProOfflineTest.vrstva(mapy, "test/kukov", "tourist");
		final SpravaMapModel model = new SpravaMapModel(mapy, vrstvy);
		final KachleZiskavac ziskavac = new KachleZiskavac();
		model.inject(ziskavac);
		Assert.assertSame(vrstvy, ziskavac.getOfflineMapy().getKatalogVrstev());
		Assert.assertSame(vrstvy, ziskavac.getOfflineTrasy().getKatalogVrstev());
	}
}
