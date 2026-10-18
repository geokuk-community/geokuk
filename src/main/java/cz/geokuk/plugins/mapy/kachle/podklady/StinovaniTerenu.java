package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

import org.mapsforge.map.layer.hills.DiffuseLightShadingAlgorithm;
import org.mapsforge.map.layer.hills.HgtFileInfo;

/**
 * Stínování terénu jako {@link DiffuseLightShadingAlgorithm}, ale při oddálení počítá z řidší mřížky výšek: bitmapa stínování má jen tolik bodů na stupeň, kolik má
 * dlaždice pixelů ve směru s menším počtem pixelů na stupeň (zeměpisná délka). HGT se čte najednou, ne po bajtech.
 */
class StinovaniTerenu extends DiffuseLightShadingAlgorithm {

	/** Kolikrát víc bodů na stupeň než pixelů dlaždice má bitmapa stínování. */
	private static final double PREVZORKOVANI = 1.0;

	/** Po kolika bodech HGT se bere výška: největší dělitel délky osy, se kterým má bitmapa aspoň tolik bodů na stupeň, kolik pixelů dlaždice. */
	static int krok(final int osa, final double pxNaStupen) {
		final double potreba = Math.max(1, pxNaStupen * PREVZORKOVANI);
		for (int k = (int) (osa / potreba); k > 1; k--) {
			if (osa % k == 0) {
				return k;
			}
		}
		return 1;
	}

	private static int krok(final HgtFileInfo info, final double pxPerLat, final double pxPerLon) {
		return krok(info.getAxisLen(), Math.min(pxPerLat, pxPerLon));
	}

	@Override
	public int getOutputAxisLen(final HgtFileInfo info, final int zoomLevel, final double pxPerLat, final double pxPerLon) {
		return info.getAxisLen() / krok(info, pxPerLat, pxPerLon);
	}

	@Override
	public long getCacheTagBin(final HgtFileInfo info, final int zoomLevel, final double pxPerLat, final double pxPerLon) {
		return krok(info, pxPerLat, pxPerLon);
	}

	@Override
	public RawShadingResult transformToByteBuffer(final HgtFileInfo info, final int padding, final int zoomLevel, final double pxPerLat, final double pxPerLon) {
		final int osa = info.getAxisLen();
		final int krok = krok(info, pxPerLat, pxPerLon);
		final int vystup = osa / krok;
		try {
			final byte[] hgt;
			try (InputStream in = info.getFile().asStream()) {
				hgt = in == null ? null : nacti(in, (osa + 1) * (osa + 1) * 2);
			}
			if (hgt == null) {
				final int sirka = vystup + 2 * padding;
				return new RawShadingResult(new byte[sirka * sirka], vystup, vystup, padding);
			}
			final byte[] mrizka = krok == 1 ? hgt : prevzorkuj(hgt, osa + 1, krok);
			return new RawShadingResult(convert(new Vstup(mrizka), vystup, vystup + 1, padding, zoomLevel, pxPerLat, pxPerLon, info), vystup, vystup, padding);
		} catch (final IOException e) {
			LOGGER.log(Level.SEVERE, e.toString(), e);
			return null;
		}
	}

	private static byte[] nacti(final InputStream in, final int delka) throws IOException {
		final byte[] v = new byte[delka];
		int n = 0;
		while (n < delka) {
			final int r = in.read(v, n, delka - n);
			if (r < 0) {
				break;
			}
			n += r;
		}
		return v;
	}

	/** Řidší mřížka výšek (big-endian jako HGT): každý bod je průměr platných výšek ve čtverci kolem něj. */
	static byte[] prevzorkuj(final byte[] hgt, final int radek, final int krok) {
		final int novy = (radek - 1) / krok + 1;
		final int pul = krok / 2;
		final byte[] v = new byte[novy * novy * 2];
		int o = 0;
		for (int ny = 0; ny < novy; ny++) {
			final int y0 = Math.max(0, ny * krok - pul);
			final int y1 = Math.min(radek - 1, ny * krok + pul);
			for (int nx = 0; nx < novy; nx++) {
				final int x0 = Math.max(0, nx * krok - pul);
				final int x1 = Math.min(radek - 1, nx * krok + pul);
				long soucet = 0;
				int pocet = 0;
				for (int y = y0; y <= y1; y++) {
					int i = (y * radek + x0) * 2;
					for (int x = x0; x <= x1; x++, i += 2) {
						final short h = (short) (hgt[i] << 8 | hgt[i + 1] & 0xff);
						if (h != Short.MIN_VALUE) {
							soucet += h;
							pocet++;
						}
					}
				}
				final short h = pocet == 0 ? Short.MIN_VALUE : (short) Math.round((double) soucet / pocet);
				v[o++] = (byte) (h >> 8);
				v[o++] = (byte) h;
			}
		}
		return v;
	}

	/** Čtení z pole bez zámků, {@link #convert} čte po bajtech. */
	private static final class Vstup extends InputStream {
		private final byte[] data;
		private int pozice;

		Vstup(final byte[] data) {
			this.data = data;
		}

		@Override
		public int read() {
			return pozice < data.length ? data[pozice++] & 0xff : -1;
		}
	}
}
