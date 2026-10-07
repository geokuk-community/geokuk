package cz.geokuk.core.program;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JStatusBarPrekroceniTest {

	@Test
	public void textPodleDruhuLimitu() {
		assertEquals("Limit 90 000 waypointů", JStatusBar.textPrekroceni(false, 90_000));
		assertEquals("Limit 300 000 teček", JStatusBar.textPrekroceni(true, 300_000));
	}

	@Test
	public void bublinaRikaCeleZneni() {
		final String tip = JStatusBar.tooltipPrekroceni(true, 300_000);
		assertTrue(tip, tip.startsWith("Ve výřezu je víc než 300") && tip.contains("teček") && tip.contains("Přibližte mapu"));
	}
}
