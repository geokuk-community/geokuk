package cz.geokuk.core.napoveda;

import org.junit.Assert;
import org.junit.Test;

public class VerzeJavyTest {

	@org.junit.Rule
	public org.junit.rules.TemporaryFolder tmp = new org.junit.rules.TemporaryFolder();

	@Test
	public void porovnani() {
		Assert.assertTrue(VerzeJavy.jeStarsi("21.0.3", "21.0.8"));
		Assert.assertFalse(VerzeJavy.jeStarsi("21.0.8", "21.0.8"));
		Assert.assertFalse(VerzeJavy.jeStarsi("21.0.12+7", "21.0.8"));
		Assert.assertTrue(VerzeJavy.jeStarsi("1.8.0_392", "21"));
		Assert.assertFalse(VerzeJavy.jeStarsi("21", "1.8"));
		Assert.assertTrue("příliš dlouhé číslo verze nespadne", VerzeJavy.jeStarsi("21.0.8", "21.0.99999999999"));
		Assert.assertFalse(VerzeJavy.jeStarsi("25", "21.0.8"));
		Assert.assertFalse(VerzeJavy.jeStarsi("21.0.1", null));
		Assert.assertFalse(VerzeJavy.jeStarsi("21.0.1", " "));
	}

	@Test
	public void pribalenaJavaVeSlozceProgramu() throws Exception {
		final java.io.File program = tmp.newFolder("program");
		Assert.assertTrue(VerzeJavy.jePribalena(new java.io.File(program, "runtime"), program));
		Assert.assertTrue("balík pro macOS", VerzeJavy.jePribalena(new java.io.File(program, "runtime-arm64"), program));
		Assert.assertTrue("balík pro macOS", VerzeJavy.jePribalena(new java.io.File(program, "runtime-x64"), program));
		Assert.assertFalse(VerzeJavy.jePribalena(new java.io.File(program, "jdk"), program));
		Assert.assertFalse(VerzeJavy.jePribalena(tmp.newFolder("runtime"), program));
	}

	@Test
	public void vlastniSouborMaObeVerze() {
		Assert.assertNotNull(VerzeJavy.vlastni().getProperty("doporucena"));
		Assert.assertNotNull(VerzeJavy.vlastni().getProperty("minimalni"));
	}
}
