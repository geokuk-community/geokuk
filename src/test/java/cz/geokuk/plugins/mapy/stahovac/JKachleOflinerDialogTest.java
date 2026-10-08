package cz.geokuk.plugins.mapy.stahovac;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.mapy.kachle.data.EKaType;

/** Hromadné stahování nejde spustit, když mapu nelze stahovat hromadně nebo se mapy neukládají. */
public class JKachleOflinerDialogTest {

	@Test
	public void sUkladanimLzeStahovat() {
		Assert.assertNull(JKachleOflinerDialog.procNelzeStahovat(EKaType.TUR_FREEMAP_SK_T, true));
	}

	@Test
	public void bezUkladaniVyzveKZapnuti() {
		Assert.assertEquals("Nejdřív zapněte Mapy > Ukládat mapy", JKachleOflinerDialog.procNelzeStahovat(EKaType.TUR_FREEMAP_SK_T, false));
	}

	@Test
	public void zakazanaMapaMaPrednost() {
		Assert.assertEquals("Mapu OpenStreetMap nelze stahovat hromadně", JKachleOflinerDialog.procNelzeStahovat(EKaType.OPEN_STREET, false));
	}

	@Test
	public void mapyCzNelzeStahovat() {
		Assert.assertEquals("Mapu Turistická nelze stahovat hromadně", JKachleOflinerDialog.procNelzeStahovat(EKaType.TURIST_M, true));
	}
}
