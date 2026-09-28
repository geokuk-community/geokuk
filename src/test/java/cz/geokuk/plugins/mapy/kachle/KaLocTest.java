package cz.geokuk.plugins.mapy.kachle;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

public class KaLocTest {

	@Test
	public void test0() {

		final Mou mou1 = new Mou(0, 0);
		final KaLoc kaloc = KaLoc.ofJZ(mou1, 4);
		Assert.assertEquals(0, kaloc.getSignedX());
		Assert.assertEquals(0, kaloc.getSignedY());
		Assert.assertEquals(8, kaloc.getFromSzUnsignedX());
		Assert.assertEquals(7, kaloc.getFromSzUnsignedY());
	}

	@Test
	public void testJZroh() {

		final Mou mou1 = new Mou(0xFEA00000, 0x05200000);
		final KaLoc kaloc = KaLoc.ofJZ(mou1, 12);
		final Mou mou2 = kaloc.getMouJZ();
		Assert.assertEquals(mou1, mou2);
	}

	@Test
	public void testSZroh() {

		final Mou mou1 = new Mou(0x23800000, 0x28100000);
		final KaLoc kaloc = KaLoc.ofSZ(mou1, 12);
		final Mou mou2 = kaloc.getMouSZ();
		Assert.assertEquals(mou1, mou2);

	}

	/** Dlaždice obsahující bod musí mít číslo podle schématu XYZ, které používají mapové servery. */
	@Test
	public void cislaDlazdicPodleOsm() {
		final Object[][] pripady = {
				{ 50.0755, 14.4378, new int[][] { { 1, 1, 0 }, { 8, 138, 86 }, { 13, 4424, 2775 }, { 16, 35396, 22204 }, { 19, 283170, 177638 } } },
				{ -33.8688, 151.2093, new int[][] { { 1, 1, 1 }, { 8, 235, 153 }, { 13, 7536, 4915 }, { 16, 60294, 39327 } } },
				{ 40.7128, -74.006, new int[][] { { 1, 0, 0 }, { 8, 75, 96 }, { 13, 2411, 3080 }, { 16, 19295, 24640 } } } };
		for (final Object[] p : pripady) {
			final Wgs wgs = new Wgs((Double) p[0], (Double) p[1]);
			for (final int[] zxy : (int[][]) p[2]) {
				final KaLoc kaloc = KaLoc.ofJZ(wgs.toMou(), zxy[0]);
				Assert.assertEquals(wgs + " z" + zxy[0] + " x", zxy[1], kaloc.getFromSzUnsignedX());
				Assert.assertEquals(wgs + " z" + zxy[0] + " y", zxy[2], kaloc.getFromSzUnsignedY());
			}
		}
	}

	@Test
	public void sousedniDlazdice() {
		final KaLoc a = KaLoc.ofJZ(new Wgs(50.0755, 14.4378).toMou(), 13);
		final int strana = 1 << Coord.MOU_BITS - 13;
		final KaLoc vpravo = KaLoc.ofJZ(new Mou(a.getMouJZ().xx + strana, a.getMouJZ().yy), 13);
		final KaLoc nahoru = KaLoc.ofJZ(new Mou(a.getMouJZ().xx, a.getMouJZ().yy + strana), 13);
		Assert.assertEquals(a.getFromSzUnsignedX() + 1, vpravo.getFromSzUnsignedX());
		Assert.assertEquals(a.getFromSzUnsignedY(), vpravo.getFromSzUnsignedY());
		Assert.assertEquals("na sever je y menší", a.getFromSzUnsignedY() - 1, nahoru.getFromSzUnsignedY());
	}
}
