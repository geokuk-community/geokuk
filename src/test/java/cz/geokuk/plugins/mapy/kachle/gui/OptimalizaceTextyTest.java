package cz.geokuk.plugins.mapy.kachle.gui;

import java.util.Locale;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class OptimalizaceTextyTest {

	private Locale puvodni;

	@Before
	public void nastavAnglickeLocale() {
		puvodni = Locale.getDefault();
		Locale.setDefault(Locale.US);
	}

	@After
	public void vratLocale() {
		Locale.setDefault(puvodni);
	}

	@Test
	public void velikostVGBMaCeskouCarku() {
		Assert.assertEquals("1,9 GB", OptimalizaceTexty.velikost((long) (1.9 * (1L << 30))));
		Assert.assertEquals("1,1 GB", OptimalizaceTexty.velikost((long) (1.1 * (1L << 30))));
	}

	@Test
	public void velikostVMBBezDesetin() {
		Assert.assertEquals("512 MB", OptimalizaceTexty.velikost(512L << 20));
		Assert.assertEquals("1 MB", OptimalizaceTexty.velikost(10));
	}
}
