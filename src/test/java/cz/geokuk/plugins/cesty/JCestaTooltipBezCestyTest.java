package cz.geokuk.plugins.cesty;

import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;

/** Tooltip přidávání do cesty bez cesty (nová cesta) nespadne. */
public class JCestaTooltipBezCestyTest {

	@Test
	public void bezCesty() {
		new JCestaTooltip().setPridavaciDalkoviny(null, new Mou(0, 0));
	}
}
