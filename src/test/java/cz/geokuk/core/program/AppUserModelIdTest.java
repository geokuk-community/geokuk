package cz.geokuk.core.program;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class AppUserModelIdTest {

	/** Mimo Windows se nic nevolá a nic nespadne. */
	@Test
	public void mimoWindowsSeNicNeudela() {
		Assume.assumeFalse("jen mimo Windows", System.getProperty("os.name", "").startsWith("Windows"));
		AppUserModelId.nastav();
	}

	/** Zástupce dostane stejné ID, jaké si nastaví proces. */
	@Test
	public void zastupceZapisujeStejneIdJakoProces() {
		Assert.assertTrue(VytvoritZastupceAction.SKRIPT.contains("GK_AUMID"));
		Assert.assertTrue(VytvoritZastupceAction.CSHARP.contains("System.AppUserModel.ID"));
		Assert.assertEquals("GeoKuk", AppUserModelId.ID);
	}

	/** Nabídka zástupce do Startu se ukáže jen jednou, když to jde a zástupce ještě není. */
	@Test
	public void nabidkaStartuSeUkazeJenNaZacatku() {
		Assert.assertTrue(VytvoritZastupceAction.zeptatSe(true, null, false));
		Assert.assertFalse("nejde vytvořit", VytvoritZastupceAction.zeptatSe(false, null, false));
		Assert.assertFalse("už odpověděl", VytvoritZastupceAction.zeptatSe(true, "ne", false));
		Assert.assertFalse("už odpověděl", VytvoritZastupceAction.zeptatSe(true, "ano", false));
		Assert.assertFalse("zástupce už je", VytvoritZastupceAction.zeptatSe(true, null, true));
	}
}
