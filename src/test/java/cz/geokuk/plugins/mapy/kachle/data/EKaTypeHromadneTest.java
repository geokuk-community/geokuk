package cz.geokuk.plugins.mapy.kachle.data;

import org.junit.Assert;
import org.junit.Test;

public class EKaTypeHromadneTest {

	@Test
	public void openStreetMapNelzeStahovatHromadne() {
		Assert.assertFalse(EKaType.OPEN_STREET.isHromadneStahovaniPovoleno());
		Assert.assertTrue(EKaType.TURIST_M.isHromadneStahovaniPovoleno());
	}
}
