package cz.geokuk.plugins.mapy.kachle.data;

import org.junit.Assert;
import org.junit.Test;

public class EKaTypeHromadneTest {

	@Test
	public void openStreetMapNelzeStahovatHromadne() {
		Assert.assertFalse(EKaType.OPEN_STREET.isHromadneStahovaniPovoleno());
		Assert.assertTrue(EKaType.TUR_FREEMAP_SK_T.isHromadneStahovaniPovoleno());
		Assert.assertTrue(EKaType.TUR_FREEMAP_SK_F.isHromadneStahovaniPovoleno());
	}

	@Test
	public void mapyCzNelzeStahovatHromadne() {
		for (final EKaType typ : EKaType.vestavene()) {
			if (typ.getUrlBuilder() instanceof MapyCzUrlBuilder) {
				Assert.assertFalse(typ.name(), typ.isHromadneStahovaniPovoleno());
			}
		}
		Assert.assertFalse(EKaType.TURIST_M.isHromadneStahovaniPovoleno());
		Assert.assertFalse(EKaType.OPHOTO_M.isHromadneStahovaniPovoleno());
	}
}
