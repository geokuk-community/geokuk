package cz.geokuk.plugins.mapy.kachle.gui;

import java.util.Locale;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.plugins.mapy.kachle.podklady.StaraData;
import cz.geokuk.plugins.mapy.kachle.podklady.StavSouboruCache;

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

	@Test
	public void nazevJeOptimalizaceCacheDlazdic() {
		Assert.assertEquals("Optimalizace cache dlaždic", OptimalizaceTexty.NAZEV);
	}

	private static final long MB = 1L << 20;

	@Test
	public void doporuceniUvedeStareDlazdiceAPocitaMistoBezNich() {
		final StavSouboruCache stav = new StavSouboruCache(2000 * MB, 1024, 2000 * 1024, 200 * 1024);
		final String text = OptimalizaceTexty.doporuceni(stav, new StaraData(10, 600 * MB, "x"), false, 100L << 30);
		Assert.assertTrue(text, text.contains("V cache je 600 MB dlaždic starých verzí tématu; optimalizace je odstraní a zmenší soubor."));
		Assert.assertFalse("fragmentace 10 % se neuvádí", text.contains("fragmentovaná"));
		final String bezStarych = "potřebuje " + OptimalizaceTexty.velikost(cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.potrebneMisto(stav.bezStarych(600 * MB)));
		final String sNimi = "potřebuje " + OptimalizaceTexty.velikost(cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.potrebneMisto(stav));
		Assert.assertNotEquals(bezStarych, sNimi);
		Assert.assertTrue(text, text.contains(bezStarych));
	}

	@Test
	public void doporuceniBezStarychDlazdicZustavaBeze() {
		final StavSouboruCache stav = new StavSouboruCache(1000 * MB, 1024, 1000 * 1024, 400 * 1024);
		final String text = OptimalizaceTexty.doporuceni(stav, StaraData.ZADNA, false, 100L << 30);
		Assert.assertTrue(text, text.startsWith("Cache dlaždic je fragmentovaná: 40 % volného místa z 1000 MB."));
		Assert.assertFalse(text, text.contains("starých verzí"));
	}

	@Test
	public void prehledMaRadekStarychDlazdicAZnamePoOptimalizaci() {
		final StavSouboruCache stav = new StavSouboruCache(1000 * MB, 1024, 1000 * 1024, 100 * 1024);
		String[][] radky = OptimalizaceTexty.prehled(stav, new StaraData(5, 300 * MB, "x"), 50L << 30);
		Assert.assertEquals("Dlaždice starých verzí tématu", radky[2][0]);
		Assert.assertEquals("300 MB", radky[2][1]);
		Assert.assertEquals("asi 600 MB", radky[3][1]);
		radky = OptimalizaceTexty.prehled(stav, null, 50L << 30);
		Assert.assertEquals("zjišťuje se…", radky[2][1]);
		Assert.assertEquals("asi 900 MB", radky[3][1]);
	}

	@Test
	public void prubehUkazeMegabajtyACasKdyzUzJeZNeceho()  {
		Assert.assertEquals("Zpracováno 2000 z 10000 dlaždic, zkopírováno 50 MB, odstraněno 10 MB. Zbývá asi 4 minuty.",
				OptimalizaceTexty.prubeh(2000, 10000, 50 * MB, 10 * MB, 60_000));
		Assert.assertEquals("v prvních vteřinách se čas neodhaduje", "Zpracováno 2000 z 10000 dlaždic, zkopírováno 50 MB.", OptimalizaceTexty.prubeh(2000, 10000, 50 * MB, 0, 4_999));
		Assert.assertEquals("na konci nic nezbývá", "Zpracováno 10000 z 10000 dlaždic, zkopírováno 500 MB.", OptimalizaceTexty.prubeh(10000, 10000, 500 * MB, 0, 60_000));
	}
}
