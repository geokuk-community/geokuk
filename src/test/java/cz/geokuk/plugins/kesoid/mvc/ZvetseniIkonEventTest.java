package cz.geokuk.plugins.kesoid.mvc;

import org.junit.Assert;
import org.junit.Test;

public class ZvetseniIkonEventTest {

	@Test
	public void neplatneJeVychoziAOrizneSe() {
		Assert.assertEquals(1, new ZvetseniIkonEvent(ZvetseniIkonEvent.VYCHOZI).getNasobek(), 0);
		Assert.assertEquals(2.5, new ZvetseniIkonEvent(250).getNasobek(), 0);
		Assert.assertEquals(ZvetseniIkonEvent.VYCHOZI, ZvetseniIkonEvent.oriznout(0));
		Assert.assertEquals(ZvetseniIkonEvent.MIN, ZvetseniIkonEvent.oriznout(10));
		Assert.assertEquals(ZvetseniIkonEvent.MAX, ZvetseniIkonEvent.oriznout(900));
		Assert.assertEquals(230, ZvetseniIkonEvent.oriznout(230));
	}
}
