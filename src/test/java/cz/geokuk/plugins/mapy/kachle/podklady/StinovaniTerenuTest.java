package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mapsforge.map.layer.hills.DemFileFS;
import org.mapsforge.map.layer.hills.DiffuseLightShadingAlgorithm;
import org.mapsforge.map.layer.hills.HgtFileInfo;
import org.mapsforge.map.layer.hills.ShadingAlgorithm.RawShadingResult;

public class StinovaniTerenuTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Kopcovitý terén 1° × 1° s osou 360 bodů (jako HGT, big-endian), s pár prázdnými body. */
	private HgtFileInfo teren() throws IOException {
		final int radek = 361;
		final short[] v = new short[radek * radek];
		final Random r = new Random(7);
		for (int y = 0; y < radek; y++) {
			for (int x = 0; x < radek; x++) {
				v[y * radek + x] = (short) (400 + 300 * Math.sin(x / 17.0) * Math.cos(y / 23.0) + r.nextInt(20));
			}
		}
		v[1000] = Short.MIN_VALUE;
		final File f = new File(tmp.getRoot(), "N50E014.hgt");
		Files.write(f.toPath(), hgt(v));
		return new HgtFileInfo(new DemFileFS(f), 50, 14, 51, 15, f.length()) {
		};
	}

	@Test
	public void podrobneJeStinovaniShodneSPlnymRozlisenim() throws IOException {
		final HgtFileInfo info = teren();
		// z11 a víc: na stupeň připadá víc pixelů dlaždice než bodů HGT
		for (final double px : new double[] { 400, 2264, 9000 }) {
			final RawShadingResult plne = new DiffuseLightShadingAlgorithm().transformToByteBuffer(info, 1, 11, px, px);
			final RawShadingResult nove = new StinovaniTerenu().transformToByteBuffer(info, 1, 11, px, px);
			Assert.assertEquals(plne.width, nove.width);
			Assert.assertEquals(plne.height, nove.height);
			Assert.assertArrayEquals(plne.bytes, nove.bytes);
		}
	}

	@Test
	public void oddalenePocitaZRidsiMrizky() throws IOException {
		final HgtFileInfo info = teren();
		final StinovaniTerenu s = new StinovaniTerenu();
		final RawShadingResult r = s.transformToByteBuffer(info, 1, 7, 90, 60);
		Assert.assertEquals(90, r.width);
		Assert.assertEquals(92 * 92, r.bytes.length);
		Assert.assertNotEquals(s.getCacheTag(info, 1, 7, 90, 60), s.getCacheTag(info, 1, 11, 400, 400));
	}

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
