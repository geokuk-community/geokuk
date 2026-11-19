package cz.geokuk.plugins.mapy.kachle.podklady;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.Doporuceni;

/** Hranice doporučení optimalizace: přesně na prahu se nedoporučuje, o kousek za ním ano. */
public class OptimalizaceDatabazePrahyTest {

	private static final long MB = 1L << 20;

	@Test
	public void souborPresnePodPrahemVelikostiSeNedoporucuje() {
		// Polovina stránek volných, ale soubor má přesně práh velikosti.
		final long stranek = OptimalizaceDatabaze.PRAH_VELIKOSTI / 1024;
		Assert.assertEquals(Doporuceni.NENI, OptimalizaceDatabaze.posud(new StavSouboruCache(OptimalizaceDatabaze.PRAH_VELIKOSTI, 1024, stranek, stranek / 2), StaraData.ZADNA, 100_000 * MB));
		Assert.assertEquals(Doporuceni.DOPORUCIT,
				OptimalizaceDatabaze.posud(new StavSouboruCache(OptimalizaceDatabaze.PRAH_VELIKOSTI + 1024, 1024, stranek + 1, (stranek + 1) / 2), StaraData.ZADNA, 100_000 * MB));
	}

	@Test
	public void presnyPrahVolnychStranekSeNedoporucuje() {
		final long stranek = 1000 * 1024;
		final long volnych = (long) (stranek * OptimalizaceDatabaze.PRAH_VOLNYCH);
		Assert.assertEquals(OptimalizaceDatabaze.PRAH_VOLNYCH, (double) volnych / stranek, 0);
		Assert.assertEquals(Doporuceni.NENI, OptimalizaceDatabaze.posud(new StavSouboruCache(1000 * MB, 1024, stranek, volnych), StaraData.ZADNA, 100_000 * MB));
		Assert.assertEquals(Doporuceni.DOPORUCIT, OptimalizaceDatabaze.posud(new StavSouboruCache(1000 * MB, 1024, stranek, volnych + 1), StaraData.ZADNA, 100_000 * MB));
	}

	@Test
	public void volneMistoPresneNaPotrebeStaci() {
		final StavSouboruCache stav = new StavSouboruCache(1000 * MB, 1024, 1000 * 1024, 500 * 1024);
		final long potreba = OptimalizaceDatabaze.potrebneMisto(stav);
		Assert.assertEquals(Doporuceni.DOPORUCIT, OptimalizaceDatabaze.posud(stav, StaraData.ZADNA, potreba));
		Assert.assertEquals(Doporuceni.MALO_MISTA, OptimalizaceDatabaze.posud(stav, StaraData.ZADNA, potreba - 1));
		Assert.assertEquals("nulové volné místo je málo, záporné je neznámé", Doporuceni.MALO_MISTA, OptimalizaceDatabaze.posud(stav, StaraData.ZADNA, 0));
		Assert.assertEquals(Doporuceni.DOPORUCIT, OptimalizaceDatabaze.posud(stav, StaraData.ZADNA, -1));
	}
}
