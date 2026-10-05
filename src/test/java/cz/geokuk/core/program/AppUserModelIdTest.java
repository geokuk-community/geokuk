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
}
