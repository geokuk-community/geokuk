package cz.geokuk.plugins.mapy.kachle.podklady;

import org.junit.Assert;
import org.junit.Test;

public class StinovaniTerenuTest {

	@Test
	public void krokStaciNaPixelyDlazdice() {
		Assert.assertEquals(6, StinovaniTerenu.krok(3600, 566)); // z9, 50° s. š.
		Assert.assertEquals(3, StinovaniTerenu.krok(3600, 1132)); // z10
		Assert.assertEquals(1, StinovaniTerenu.krok(3600, 2264)); // z11
		Assert.assertEquals(1, StinovaniTerenu.krok(1200, 2000)); // 3" HGT podrobně
		for (int px = 1; px < 4000; px += 37) {
			final int k = StinovaniTerenu.krok(3600, px);
			Assert.assertEquals(0, 3600 % k);
			Assert.assertTrue(k == 1 || 3600 / k >= px);
		}
	}

	private static byte[] hgt(final short[] vysky) {
		final byte[] b = new byte[vysky.length * 2];
		for (int i = 0; i < vysky.length; i++) {
			b[2 * i] = (byte) (vysky[i] >> 8);
			b[2 * i + 1] = (byte) vysky[i];
		}
		return b;
	}

	private static short vyska(final byte[] b, final int i) {
		return (short) (b[2 * i] << 8 | b[2 * i + 1] & 0xff);
	}

	@Test
	public void prevzorkovaniPrumerujeAVynechaPrazdne() {
		// mřížka 5 × 5 (osa 4), krok 2 → 3 × 3
		final short[] v = new short[25];
		for (int i = 0; i < 25; i++) {
			v[i] = (short) (i * 10);
		}
		v[12] = Short.MIN_VALUE; // prázdný bod uprostřed se nepočítá
		final byte[] r = StinovaniTerenu.prevzorkuj(hgt(v), 5, 2);
		Assert.assertEquals(18, r.length);
		Assert.assertEquals(30, vyska(r, 0)); // body 0, 1, 5, 6
		Assert.assertEquals(120, vyska(r, 4)); // okolí středu bez prázdného
		Assert.assertEquals(-2000, vyska(StinovaniTerenu.prevzorkuj(hgt(new short[] { -2000, -2000, -2000, -2000 }), 2, 1), 0));
	}

	@Test
	public void bezPlatnychVysekZustanePrazdne() {
		final short[] v = new short[9];
		java.util.Arrays.fill(v, Short.MIN_VALUE);
		Assert.assertEquals(Short.MIN_VALUE, vyska(StinovaniTerenu.prevzorkuj(hgt(v), 3, 2), 0));
	}
}
