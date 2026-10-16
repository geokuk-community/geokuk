package cz.geokuk.plugins.mapy.sprava.gui;

import org.junit.Assert;
import org.junit.Test;

public class OdhadStahovaniTest {

	private static final long MB = 1_000_000;

	@Test
	public void odhadZRychlostiOdPrvnihoPozorovani() {
		final OdhadStahovani o = new OdhadStahovani();
		Assert.assertEquals("první vzorek", -1, o.zbyva("cz", 100 * MB, 600 * MB, 0));
		Assert.assertEquals("krátké pozorování", -1, o.zbyva("cz", 102 * MB, 600 * MB, 1000));
		// 10 MB/s, zbývá 400 MB
		Assert.assertEquals(40, o.zbyva("cz", 200 * MB, 600 * MB, 10_000));
		o.zbyva("x", 5, 600 * MB, 0);
		Assert.assertEquals("nic se nestáhlo", -1, o.zbyva("x", 5, 600 * MB, 10_000));
	}

	@Test
	public void novaMapaZacinaZnovu() {
		final OdhadStahovani o = new OdhadStahovani();
		o.zbyva("cz", 0, 600 * MB, 0);
		Assert.assertTrue(o.zbyva("cz", 30 * MB, 600 * MB, 10_000) > 0);
		o.zapomenKrome(java.util.Collections.emptySet());
		Assert.assertEquals(-1, o.zbyva("cz", 30 * MB, 600 * MB, 11_000));
	}

	@Test
	public void textCasu() {
		Assert.assertEquals("asi 5 s", OdhadStahovani.text(1));
		Assert.assertEquals("asi 40 s", OdhadStahovani.text(38));
		Assert.assertEquals("asi 3 min", OdhadStahovani.text(170));
		Assert.assertEquals("asi 1 h", OdhadStahovani.text(3600));
		Assert.assertEquals("asi 1 h 5 min", OdhadStahovani.text(3900));
	}

	/** Stahování od začátku (soubor se změnil) začíná odhad znovu, ne z rozdílu proti starému vzorku. */
	@Test
	public void restartOdNulyZacinaOdhadZnovu() {
		final OdhadStahovani o = new OdhadStahovani();
		o.zbyva("cz", 200 * MB, 600 * MB, 0);
		Assert.assertTrue(o.zbyva("cz", 300 * MB, 600 * MB, 10_000) > 0);
		Assert.assertEquals("začalo se od nuly", -1, o.zbyva("cz", 5 * MB, 600 * MB, 11_000));
		Assert.assertEquals(50, o.zbyva("cz", 105 * MB, 600 * MB, 21_000));
	}
}
