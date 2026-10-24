package cz.geokuk.plugins.mapy.kachle.gui;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.Doporuceni;

public class PripominkaOptimalizaceTest {

	private static final long TYDEN = PripominkaOptimalizace.ODLOZENI_MS;

	@Test
	public void bezDoporuceniSeNeukazuje() {
		Assert.assertFalse(PripominkaOptimalizace.zobrazit(Doporuceni.NENI, 0.9, 1000, 0, 0));
	}

	@Test
	public void neodlozenoSeUkaze() {
		Assert.assertTrue(PripominkaOptimalizace.zobrazit(Doporuceni.DOPORUCIT, 0.46, 1000, 0, 0));
		Assert.assertTrue(PripominkaOptimalizace.zobrazit(Doporuceni.MALO_MISTA, 0.46, 1000, 0, 0));
	}

	@Test
	public void odlozenoNaTydenSeNeukaze() {
		final long odlozeno = 5000 + TYDEN;
		Assert.assertFalse(PripominkaOptimalizace.zobrazit(Doporuceni.DOPORUCIT, 0.46, 5000 + TYDEN - 1, odlozeno, 0.46));
		Assert.assertFalse("zhoršení pod hranicí", PripominkaOptimalizace.zobrazit(Doporuceni.DOPORUCIT, 0.60, 6000, odlozeno, 0.46));
	}

	@Test
	public void poTydnuSeUkazeZnovu() {
		Assert.assertTrue(PripominkaOptimalizace.zobrazit(Doporuceni.DOPORUCIT, 0.46, 5000 + TYDEN, 5000 + TYDEN, 0.46));
	}

	@Test
	public void vyrazneZhorseniUkazeIPoOdlozeni() {
		Assert.assertTrue(PripominkaOptimalizace.zobrazit(Doporuceni.DOPORUCIT, 0.62, 6000, 5000 + TYDEN, 0.46));
	}

	@Test
	public void textyMinut() {
		Assert.assertEquals("1 minutu", OptimalizaceTexty.minut(1));
		Assert.assertEquals("3 minuty", OptimalizaceTexty.minut(3));
		Assert.assertEquals("7 minut", OptimalizaceTexty.minut(7));
	}
}
