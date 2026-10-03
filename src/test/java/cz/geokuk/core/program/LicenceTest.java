package cz.geokuk.core.program;

import org.junit.Assert;
import org.junit.Test;

public class LicenceTest {

	@Test
	public void programObsahujeLicenciASeznamKnihoven() {
		final String text = JOProgramuDialog.textLicence();
		Assert.assertTrue(text.contains("SQLJet"));
		Assert.assertTrue(text.contains("Sun Microsystems"));
		Assert.assertTrue(text.contains("GNU GENERAL PUBLIC LICENSE"));
	}
}
