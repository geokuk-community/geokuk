package cz.geokuk.core.program;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

public class JStatusBarZamcenoTest {

	@Test
	public void bezZamcenychNicNeukazuje() {
		Assert.assertEquals("", JStatusBar.textZamceno(Collections.emptyList()));
		Assert.assertNull(JStatusBar.tooltipZamceno(Collections.emptyList()));
	}

	@Test
	public void ukazePocetAJmenaBezCest() {
		Assert.assertEquals("Zamčeno: 3", JStatusBar.textZamceno(Arrays.asList("a.db3", "b.db3", "gsak")));
		final String tip = JStatusBar.tooltipZamceno(Arrays.asList("a.db3", "b.db3"));
		Assert.assertTrue(tip, tip.startsWith("a.db3\nb.db3\n"));
		Assert.assertTrue(tip, tip.contains("Zavřete program, který databázi používá"));
	}
}
