package cz.geokuk.plugins.kesoid.importek;

import org.junit.Assert;
import org.junit.Test;

public class UvolneniPametiTest {

	@Test
	public void sberJenPoVyraznemUbytku() {
		Assert.assertFalse("první načtení: všechna data živá, pauza by byla dlouhá", UvolneniPameti.vyplatiSe(-1, 3_000_000));
		Assert.assertTrue("vypnutý velký zdroj", UvolneniPameti.vyplatiSe(192_000, 47_000));
		Assert.assertTrue("vypnuto vše", UvolneniPameti.vyplatiSe(192_000, 0));
		Assert.assertFalse("malý úbytek", UvolneniPameti.vyplatiSe(192_000, 190_000));
		Assert.assertTrue("20 %", UvolneniPameti.vyplatiSe(200_000, 160_000));
		Assert.assertFalse("pod 20 %", UvolneniPameti.vyplatiSe(200_000, 160_001));
		Assert.assertTrue("milion i pod 20 %", UvolneniPameti.vyplatiSe(10_000_000, 9_000_000));
		Assert.assertFalse("pod milion a pod 20 %", UvolneniPameti.vyplatiSe(3_283_310, 3_112_000));
		Assert.assertFalse("přibylo", UvolneniPameti.vyplatiSe(167_000, 192_000));
		Assert.assertFalse("málo waypointů", UvolneniPameti.vyplatiSe(500, 0));
	}
}
