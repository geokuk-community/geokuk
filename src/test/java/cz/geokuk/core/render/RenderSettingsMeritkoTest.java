package cz.geokuk.core.render;

import org.junit.Assert;
import org.junit.Test;

/** Měřítkem se dělí, nesmyslná hodnota se nesmí uložit. */
public class RenderSettingsMeritkoTest {

	@Test
	public void nulaSeNeulozi() {
		final RenderSettings s = new RenderSettings();
		final int puvodni = s.getPapiroveMeritko();
		s.setPapiroveMeritko(0);
		Assert.assertEquals(puvodni, s.getPapiroveMeritko());
	}

	@Test
	public void zaporneMeritkoSeNeulozi() {
		final RenderSettings s = new RenderSettings();
		s.setPapiroveMeritko(25000);
		s.setPapiroveMeritko(-5);
		Assert.assertEquals(25000, s.getPapiroveMeritko());
	}

	@Test
	public void rozumneMeritkoProjde() {
		final RenderSettings s = new RenderSettings();
		s.setPapiroveMeritko(12345);
		Assert.assertEquals(12345, s.getPapiroveMeritko());
	}
}
