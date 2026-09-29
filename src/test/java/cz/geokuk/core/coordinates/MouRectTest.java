package cz.geokuk.core.coordinates;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;

/** Obdélník v mouřadnicích; celý svět je 2^32 mouřadnic, takže int snadno přeteče. */
public class MouRectTest {

	@Test
	public void stredNepreteceUVelkychSouradnic() {
		// součet dvou velkých mouřadnic přeteče, aniž by bylo potřeba odzoomovat
		final Mou roh1 = new Mou(2_000_000_000, 2_000_000_000);
		final Mou roh2 = new Mou(2_100_000_000, 2_100_000_000);
		final MouRect obdelnik = new MouRect(roh1, roh2);

		Assert.assertEquals(new Mou(2_050_000_000, 2_050_000_000), obdelnik.getStred());
		Assert.assertEquals(new Mou(2_050_000_000, 2_050_000_000), obdelnik.sstre);
	}

	@Test
	public void rozmerObdelnikuPresCelySvetNepretece() {
		final MouRect obdelnik = new MouRect(new Mou(Integer.MIN_VALUE, Integer.MIN_VALUE), new Mou(Integer.MAX_VALUE, Integer.MAX_VALUE));

		Assert.assertEquals((1L << Coord.MOU_BITS) - 1, obdelnik.getMouWidth());
		Assert.assertEquals((1L << Coord.MOU_BITS) - 1, obdelnik.getMouHeight());
	}

	@Test
	public void zvetseniObdelnikuNepresahneOkrajSveta() {
		// zoom na keše zvětšuje výřez o pětinu; přes okraj světa by se přetočil na skoro prázdný
		final MouRect obdelnik = new MouRect(new Mou(-2_000_000_000, -2_000_000_000), new Mou(2_000_000_000, 2_000_000_000));

		obdelnik.resize(1.2);

		Assert.assertEquals(Integer.MIN_VALUE, obdelnik.getJz().xx);
		Assert.assertEquals(Integer.MIN_VALUE, obdelnik.getJz().yy);
		Assert.assertEquals(Integer.MAX_VALUE, obdelnik.getSv().xx);
		Assert.assertEquals(Integer.MAX_VALUE, obdelnik.getSv().yy);
	}
}
