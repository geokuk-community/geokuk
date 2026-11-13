package cz.geokuk.plugins.challenge;

import java.util.AbstractList;
import java.util.BitSet;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class TvarCacheTest {

	@After
	public void uklid() {
		TvarCache.vymaz();
	}

	@Test
	public void errorPriStavbeNenechaPodkladNedokonceny() {
		final java.util.List<VysledekSektoru.KandidatKes> vadny = new AbstractList<VysledekSektoru.KandidatKes>() {
			@Override
			public VysledekSektoru.KandidatKes get(final int index) {
				throw new IndexOutOfBoundsException();
			}

			@Override
			public int size() {
				throw new StackOverflowError("zkouška");
			}
		};
		final VysledekSektoru v = new VysledekSektoru(new BitSet(360), new BitSet(360), new VysledekSektoru.Kandidati[360], 0, vadny);
		final PrehledChallenge prehled = PrehledChallenge.zeSektoru(TvarPlanovaceTest.sektory(), v, 50.0, 14.0);
		final Error[] zachyceno = new Error[1];
		final java.util.concurrent.Executor stavba = r -> {
			try {
				r.run();
			} catch (final Error e) {
				zachyceno[0] = e;
			}
		};

		Assert.assertNull(TvarCache.dej(prehled, null, stavba, () -> {}));

		Assert.assertNotNull("Error se nepolyká", zachyceno[0]);
		Assert.assertTrue("stavba skončila chybou, ne věčným čekáním", TvarCache.selhalo());
	}
}
