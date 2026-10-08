package cz.geokuk.core.program;

import org.junit.Assert;
import org.junit.Test;

public class LicenceTest {

	@Test
	public void programObsahujeLicenciASeznamKnihoven() {
		final String text = JOProgramuDialog.textLicence();
		Assert.assertTrue(text.contains("SQLJet"));
		Assert.assertTrue(text.contains("Sun Microsystems"));
		Assert.assertTrue(text.contains("Copyright (c) 1995 - 2008 Sun Microsystems"));
		Assert.assertTrue(text.contains("GNU GENERAL PUBLIC LICENSE"));
		Assert.assertTrue(text.contains("Copyright (c) 2004-2023 QOS.ch"));
		Assert.assertTrue(text.contains("Copyright (c) 2005-2009 Terence Parr"));
		Assert.assertTrue(text.contains("Copyright (c) Adobe Systems Incorporated"));
		Assert.assertTrue(text.contains("Copyright (c) 2006, David Crawshaw"));
		Assert.assertTrue(text.contains("GNU LESSER GENERAL PUBLIC LICENSE"));
		Assert.assertTrue(text.contains("Apache License"));
		Assert.assertTrue(text.contains("sqljet-1.1.15-sources.jar"));
		Assert.assertTrue(text.contains("ČÚZK"));
		Assert.assertTrue(text.contains("Freemap Slovakia"));
	}
}
