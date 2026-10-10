package cz.geokuk.core.program;

import org.junit.Assert;
import org.junit.Test;

/** Texty dialogu Paměť programu. */
public class PametProgramuActionTest {

	@Test
	public void automatickaVolbaUkazujeSkutecnouPamet() {
		Assert.assertEquals("Automaticky (teď 4 GB)", PametProgramuAction.popisAutomaticky(32 * 1024));
		Assert.assertEquals("Automaticky (teď 3 GB)", PametProgramuAction.popisAutomaticky(8 * 1024));
		Assert.assertEquals("Automaticky (teď 1,5 GB)", PametProgramuAction.popisAutomaticky(3 * 1024));
	}

	@Test
	public void stavProgramuAPocitace() {
		Assert.assertEquals("Teď 3 072 MB z 32 GB. Platí po restartu.", PametProgramuAction.stav(3072, 32 * 1024 - 5));
	}
}
