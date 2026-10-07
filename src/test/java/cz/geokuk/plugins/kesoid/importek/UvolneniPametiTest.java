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
		Assert.assertFalse("přibylo", UvolneniPameti.vyplatiSe(167_000, 192_000));
		Assert.assertFalse("málo waypointů", UvolneniPameti.vyplatiSe(500, 0));
	}
}
