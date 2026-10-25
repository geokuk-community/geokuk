package cz.geokuk.plugins.mapy.kachle.gui;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.Doporuceni;
import cz.geokuk.plugins.mapy.kachle.podklady.StavSouboruCache;

public class PripominkaOptimalizaceTest {

	private static final long TYDEN = PripominkaOptimalizace.ODLOZENI_BEZ_MS;

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

	private static final long DEN = java.util.concurrent.TimeUnit.DAYS.toMillis(1);

	private static StavSouboruCache stav(final long velikostMB, final double podil) {
		final long stranek = 1_000_000;
		return new StavSouboruCache(velikostMB << 20, 1024, stranek, Math.round(stranek * podil));
	}

	@Test
	public void delkyOdlozeniPodleTriPripadu() {
		final StavSouboruCache bezne = stav(1900, 0.46);
		Assert.assertEquals(3 * DEN, PripominkaOptimalizace.odlozeniMs(bezne, true));
		Assert.assertEquals(7 * DEN, PripominkaOptimalizace.odlozeniMs(bezne, false));
		final StavSouboruCache kriticky = stav(1900, 0.65);
		Assert.assertEquals(DEN, PripominkaOptimalizace.odlozeniMs(kriticky, true));
		Assert.assertEquals(DEN, PripominkaOptimalizace.odlozeniMs(kriticky, false));
	}

	@Test
	public void pragyKritickehoStavu() {
		Assert.assertFalse("přesně 60 %", PripominkaOptimalizace.kriticky(stav(1900, 0.60)));
		Assert.assertTrue(PripominkaOptimalizace.kriticky(stav(1900, 0.61)));
		Assert.assertFalse("přesně 200 MB", PripominkaOptimalizace.kriticky(stav(200, 0.9)));
		Assert.assertTrue(PripominkaOptimalizace.kriticky(stav(201, 0.9)));
		Assert.assertFalse(PripominkaOptimalizace.kriticky(null));
	}

	@Test
	public void zhorseniUKritickehoStavuSpustiOknoDrive() {
		final long odlozeno = 5000 + DEN;
		Assert.assertTrue(PripominkaOptimalizace.zobrazit(Doporuceni.DOPORUCIT, 0.76, 6000, odlozeno, 0.61));
	}

	@Test
	public void odpocetTrva30sAPozastaveniHoZastavi() {
		Assert.assertEquals(30, OknoDoporuceniOptimalizace.ODPOCET_S);
		final OknoDoporuceniOptimalizace.Odpocet o = new OknoDoporuceniOptimalizace.Odpocet(3);
		Assert.assertFalse(o.tik(false));
		Assert.assertEquals(2, o.zbyva());
		Assert.assertFalse(o.tik(true));
		Assert.assertFalse(o.tik(true));
		Assert.assertEquals("pozastaveno, nic neubývá", 2, o.zbyva());
	}

	@Test
	public void odpocetPokracujeOdZbyvajicihoCasuAVyprsi() {
		final OknoDoporuceniOptimalizace.Odpocet o = new OknoDoporuceniOptimalizace.Odpocet(3);
		o.tik(false);
		o.tik(true);
		Assert.assertFalse(o.tik(false));
		Assert.assertEquals(1, o.zbyva());
		Assert.assertTrue(o.tik(false));
	}

	@Test
	public void klikOdpocetZrusi() {
		final OknoDoporuceniOptimalizace.Odpocet o = new OknoDoporuceniOptimalizace.Odpocet(1);
		o.zrus();
		Assert.assertTrue(o.zruseno());
		Assert.assertFalse(o.tik(false));
	}

	@Test
	public void textyOdlozeni() {
		Assert.assertEquals("Zavře se za 30 s", OptimalizaceTexty.odpocet(30));
		Assert.assertTrue(OptimalizaceTexty.pripomenuti(false, true).contains("za 3 dny"));
		Assert.assertTrue(OptimalizaceTexty.pripomenuti(false, false).contains("za týden"));
		Assert.assertTrue(OptimalizaceTexty.pripomenuti(true, true).contains("zítra"));
		Assert.assertTrue(OptimalizaceTexty.pripomenuti(true, false).contains("zítra"));
	}
}
