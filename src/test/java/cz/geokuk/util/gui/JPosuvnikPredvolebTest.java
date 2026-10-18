package cz.geokuk.util.gui;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class JPosuvnikPredvolebTest {

	private static final int[] PREDVOLBY = { 0, 100, 150, 200 };

	@Test
	public void vlastniHodnotaSeVlozi() {
		Assert.assertEquals(Arrays.asList(0, 100, 150, 200), JPosuvnikPredvoleb.hodnoty(PREDVOLBY, 150));
		Assert.assertEquals(Arrays.asList(0, 100, 130, 150, 200), JPosuvnikPredvoleb.hodnoty(PREDVOLBY, 130));
		Assert.assertEquals(Arrays.asList(0, 100, 150, 200, 230), JPosuvnikPredvoleb.hodnoty(PREDVOLBY, 230));
	}

	@Test
	public void hodnotaZustaneAZmeniSePosuvnikem() {
		final JPosuvnikPredvoleb p = new JPosuvnikPredvoleb(PREDVOLBY, 130, Integer::toString, h -> h + " %");
		Assert.assertEquals(130, p.getHodnota());
		p.getSlider().setValue(3);
		Assert.assertEquals(150, p.getHodnota());
		p.setHodnota(0);
		Assert.assertEquals(0, p.getHodnota());
	}
}
