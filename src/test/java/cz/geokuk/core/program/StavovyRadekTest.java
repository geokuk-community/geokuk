package cz.geokuk.core.program;

import org.junit.Assert;
import org.junit.Test;

/** Stavový řádek bez načtených zdrojů a bez pozice neukazuje rok 1970 ani anglické N/A. */
public class StavovyRadekTest {

	@Test
	public void bezZdrojuBezCasu() {
		Assert.assertEquals("", JStatusBar.casZdroju(0));
	}

	@Test
	public void casNejmladsihoZdroje() {
		Assert.assertTrue(JStatusBar.casZdroju(1_700_000_000_000L).startsWith("2023-11-1"));
	}

	@Test
	public void bezPozice() {
		Assert.assertNotEquals("N/A", JStatusBar.BEZ_POZICE);
	}
}
