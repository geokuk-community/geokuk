package cz.geokuk.core.napoveda;

import org.junit.Assert;
import org.junit.Test;

public class VerzeJavyTest {

	@Test
	public void porovnani() {
		Assert.assertTrue(VerzeJavy.jeStarsi("21.0.3", "21.0.8"));
		Assert.assertFalse(VerzeJavy.jeStarsi("21.0.8", "21.0.8"));
		Assert.assertFalse(VerzeJavy.jeStarsi("21.0.12+7", "21.0.8"));
		Assert.assertTrue(VerzeJavy.jeStarsi("1.8.0_392", "21"));
		Assert.assertFalse(VerzeJavy.jeStarsi("21", "1.8"));
		Assert.assertFalse(VerzeJavy.jeStarsi("25", "21.0.8"));
		Assert.assertFalse(VerzeJavy.jeStarsi("21.0.1", null));
		Assert.assertFalse(VerzeJavy.jeStarsi("21.0.1", " "));
	}

	@Test
	public void vlastniSouborMaObeVerze() {
		Assert.assertNotNull(VerzeJavy.vlastni().getProperty("doporucena"));
		Assert.assertNotNull(VerzeJavy.vlastni().getProperty("minimalni"));
	}
}
