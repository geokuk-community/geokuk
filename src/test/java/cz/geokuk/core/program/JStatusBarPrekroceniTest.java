package cz.geokuk.core.program;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JStatusBarPrekroceniTest {

	@Test
	public void textPodleDruhuLimitu() {
		assertEquals("Překročen limit 90 000 waypointů", JStatusBar.textPrekroceni(false, 90_000));
		assertEquals("Překročen limit 300 000 teček", JStatusBar.textPrekroceni(true, 300_000));
	}
}
