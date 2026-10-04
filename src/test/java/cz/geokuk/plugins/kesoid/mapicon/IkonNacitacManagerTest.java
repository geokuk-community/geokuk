package cz.geokuk.plugins.kesoid.mapicon;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.kesoid.mvc.KesoidUmisteniSouboru;

public class IkonNacitacManagerTest {

	/** Časovač může přijít dřív, než se nastaví umístění souborů. */
	@Test
	public void predNastavenimUmisteniNicNedela() {
		final KesoidModel kesoidModel = new KesoidModel();
		Assert.assertNull(kesoidModel.getUmisteniSouboru());
		new IkonNacitacManager(kesoidModel).startLoad(false);
	}

	/** První nastavení umístění změní umístění ikon, takže se ikony načtou. */
	@Test
	public void prvniUmisteniZmeniUmisteniIkon() {
		final KesoidUmisteniSouboru u = new KesoidUmisteniSouboru();
		u.setImage3rdPartyDir(KesoidUmisteniSouboru.IMAGE_3RDPARTY_DIR);
		u.setImageMyDir(KesoidUmisteniSouboru.IMAGE_MY_DIR);
		Assert.assertFalse(u.equalsImageLocations(null));
	}
}
