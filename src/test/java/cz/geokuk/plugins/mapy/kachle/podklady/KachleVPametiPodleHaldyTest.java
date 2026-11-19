package cz.geokuk.plugins.mapy.kachle.podklady;

import org.junit.Assert;
import org.junit.Test;

/** Počet dlaždic v paměti se řídí velikostí haldy. */
public class KachleVPametiPodleHaldyTest {

	private static final long GB = 1L << 30;

	@Test
	public void malaHaldaDrziAsponMinimum() {
		Assert.assertEquals(KachleZiskavac.MIN_KACHLI_V_PAMETI, KachleZiskavac.maxKachliVPameti(64L << 20));
		Assert.assertEquals(KachleZiskavac.MIN_KACHLI_V_PAMETI, KachleZiskavac.maxKachliVPameti(0));
	}

	@Test
	public void ctvrtinaHaldy() {
		// 1 GB haldy: 256 MB po 256 kB = 1024 dlaždic; 3 GB: 768 MB = 3072 dlaždic.
		Assert.assertEquals(1024, KachleZiskavac.maxKachliVPameti(GB));
		Assert.assertEquals(3072, KachleZiskavac.maxKachliVPameti(3 * GB));
	}

	@Test
	public void velkaHaldaMaStrop() {
		Assert.assertEquals(4096, KachleZiskavac.maxKachliVPameti(4 * GB));
		Assert.assertEquals(4096, KachleZiskavac.maxKachliVPameti(64 * GB));
	}
}
