package cz.geokuk.plugins.kesoid.kind.cgp;

import org.junit.Assert;
import org.junit.Test;

public class CgpPrefixBoduTest {

	@Test
	public void stejneJakoRegularniVyraz() {
		final String[] jmena = { "TrB_", "TrB_123", "ZhB_x", "BTP_1-2", "ZGS_abc", "trb_1", "TrB", "TrB-1", "xTrB_1", "", "GC12345", "ZGS_a\nb", "BTP_\r", "ZhB_\u0085", "TrB_ ",
				"TrB_ x", "ZGS_\t", "BTP_ž" };
		for (final String jmeno : jmena) {
			Assert.assertEquals(jmeno, jmeno.matches("^(TrB_|ZhB_|BTP_|ZGS_).*$"), CgpGpxWptProcak.maPrefixBodu(jmeno));
		}
	}
}
