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

	/** HRESULT S_FALSE (1) je úspěch: za chybu se bere jen záporný. */
	@Test
	public void kladnyHresultNeniChyba() {
		Assert.assertFalse(VytvoritZastupceAction.CSHARP, VytvoritZastupceAction.CSHARP.contains("h!=0"));
		for (final String volani : new String[] { "PSGetPropertyKeyFromName", "SetValue", "Commit" }) {
			Assert.assertTrue(volani, VytvoritZastupceAction.CSHARP.contains("if(h<0)throw new Exception(\"" + volani));
		}
	}

	/** Nabídka zástupce do Startu se ukáže jen jednou, když to jde a zástupce ještě není. */
	@Test
	public void nabidkaStartuSeUkazeJenNaZacatku() {
		Assert.assertTrue(VytvoritZastupceAction.zeptatSe(true, null, false, false));
		Assert.assertFalse("nejde vytvořit", VytvoritZastupceAction.zeptatSe(false, null, false, false));
		Assert.assertFalse("už odpověděl", VytvoritZastupceAction.zeptatSe(true, "ne", false, false));
		Assert.assertFalse("už odpověděl", VytvoritZastupceAction.zeptatSe(true, "ano", false, false));
		Assert.assertFalse("automatizace (dálkové ovládání)", VytvoritZastupceAction.zeptatSe(true, null, false, true));
		Assert.assertFalse("zástupce už je", VytvoritZastupceAction.zeptatSe(true, null, true, false));
	}
}
