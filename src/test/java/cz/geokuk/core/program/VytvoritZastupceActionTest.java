package cz.geokuk.core.program;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class VytvoritZastupceActionTest {

	/** Hláška PowerShellu (i chyba) přijde s diakritikou, ne v kódování konzole. */
	@Test
	public void vystupPowerShelluSDiakritikou() throws Exception {
		Assume.assumeTrue("jen ve Windows", System.getProperty("os.name", "").startsWith("Windows"));
		final String text = "Zástupce nelze uložit: ěščřžýáíé";
		final VytvoritZastupceAction.Vysledek v = VytvoritZastupceAction.powershell(VytvoritZastupceAction.UTF8_VYSTUP + "Write-Output '" + text + "'", Collections.emptyMap());
		Assert.assertEquals(text, v.text);
		final VytvoritZastupceAction.Vysledek chyba = VytvoritZastupceAction.powershell(VytvoritZastupceAction.UTF8_VYSTUP + "$ErrorActionPreference='Stop';throw '" + text + "'",
				Collections.emptyMap());
		Assert.assertNotEquals(0, chyba.kod);
		Assert.assertTrue(chyba.text, chyba.text.contains(text));
	}
}
