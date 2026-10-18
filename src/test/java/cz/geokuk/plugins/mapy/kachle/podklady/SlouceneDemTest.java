package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mapsforge.map.layer.hills.DemFile;
import org.mapsforge.map.layer.hills.DemFileFS;
import org.mapsforge.map.layer.hills.DemFolderFS;
import org.mapsforge.map.layer.hills.HgtFileInfo;
import org.mapsforge.map.layer.hills.ShadingAlgorithm.RawShadingResult;

public class SlouceneDemTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final int RADEK = 361;

	/** Kopcovitý terén 1° × 1°, osa 360 bodů. */
	private static short[] teren() {
		final short[] v = new short[RADEK * RADEK];
		for (int y = 0; y < RADEK; y++) {
			for (int x = 0; x < RADEK; x++) {
				v[y * RADEK + x] = (short) (400 + 300 * Math.sin(x / 17.0) * Math.cos(y / 23.0));
			}
		}
		return v;
	}

	/** Terén oříznutý „hranicí“ (šikmá čára): jedna strana prázdná, sousední země má prázdnou druhou stranu. */
	private static short[] orizni(final short[] v, final boolean zapad) {
		final short[] o = v.clone();
		for (int y = 0; y < RADEK; y++) {
			for (int x = 0; x < RADEK; x++) {
				final boolean naZapade = x < 150 + y / 3;
				if (naZapade != zapad) {
					o[y * RADEK + x] = SlouceneDem.PRAZDNA;
				}
			}
		}
		return o;
	}

	private static byte[] hgt(final short[] v) {
		final byte[] b = new byte[v.length * 2];
		for (int i = 0; i < v.length; i++) {
			b[2 * i] = (byte) (v[i] >> 8);
			b[2 * i + 1] = (byte) v[i];
		}
		return b;
	}

	private static void zip(final File zip, final String jmeno, final byte[] data) throws IOException {
		zip.getParentFile().mkdirs();
		try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
			z.putNextEntry(new ZipEntry(jmeno));
			z.write(data);
			z.closeEntry();
			z.putNextEntry(new ZipEntry("LICENSE.txt"));
			z.write("licence".getBytes("UTF-8"));
			z.closeEntry();
		}
	}

	private static byte[] precti(final DemFile f) throws IOException {
		try (InputStream in = f.asStream()) {
			final ByteArrayOutputStream o = new ByteArrayOutputStream();
			final byte[] buf = new byte[1000]; // liché dávky po bajtech i bloky
			int n;
			while ((n = in.read(buf, 0, 1 + o.size() % 999)) >= 0) {
				o.write(buf, 0, n);
			}
			return o.toByteArray();
		}
	}

	private List<DemFile> dlazdice(final File slozka) {
		final List<DemFile> v = new ArrayList<>();
		for (final DemFile f : new SlouceneDem(new DemFolderFS(slozka)).files()) {
			if (f.getName().toLowerCase().endsWith(".hgt")) {
				v.add(f);
			}
		}
		return v;
	}

	private static RawShadingResult stin(final DemFile f) {
		final HgtFileInfo info = new HgtFileInfo(f, 50, 14, 51, 15, f.getSize()) {
		};
		return new StinovaniTerenu().transformToByteBuffer(info, 1, 12, 400, 400);
	}

	@Test
	public void dveOriznuteZemeDajiCelyTerenBezDiry() throws IOException {
		final short[] cely = teren();
		final File slozka = tmp.newFolder("layers");
		zip(new File(slozka, "europe/czech-republic.dem.zip"), "N50E014.hgt", hgt(orizni(cely, true)));
		zip(new File(slozka, "europe/poland.dem.zip"), "n50e014.HGT", hgt(orizni(cely, false)));
		final List<DemFile> d = dlazdice(slozka);
		Assert.assertEquals(1, d.size());
		Assert.assertArrayEquals(hgt(cely), precti(d.get(0)));

		final File celySoubor = new File(tmp.getRoot(), "N50E014.hgt");
		Files.write(celySoubor.toPath(), hgt(cely));
		Assert.assertArrayEquals(stin(new DemFileFS(celySoubor)).bytes, stin(d.get(0)).bytes);
		// jen jedna země by na druhé straně hranice nestínovala
		final File jenCesko = new File(tmp.getRoot(), "jen/N50E014.hgt");
		jenCesko.getParentFile().mkdirs();
		Files.write(jenCesko.toPath(), hgt(orizni(cely, true)));
		Assert.assertFalse(java.util.Arrays.equals(stin(new DemFileFS(celySoubor)).bytes, stin(new DemFileFS(jenCesko)).bytes));
	}

	@Test
	public void jedenZdrojBezSkladani() throws IOException {
		final File slozka = tmp.newFolder("layers");
		zip(new File(slozka, "europe/czech-republic.dem.zip"), "N50E014.hgt", hgt(orizni(teren(), true)));
		final List<DemFile> d = dlazdice(slozka);
		Assert.assertEquals(1, d.size());
		Assert.assertFalse(d.get(0) instanceof SlouceneDem.SlozenaDlazdice);
	}

	@Test
	public void podrobnejsiSouborVyhraje() throws IOException {
		final File slozka = tmp.newFolder("layers");
		zip(new File(slozka, "a.dem.zip"), "N50E014.hgt", hgt(teren()));
		zip(new File(slozka, "b.dem.zip"), "N50E014.hgt", hgt(new short[121 * 121]));
		final List<DemFile> d = dlazdice(slozka);
		Assert.assertEquals(1, d.size());
		Assert.assertEquals(RADEK * RADEK * 2, d.get(0).getSize());
	}

	@Test
	public void ostatniSouboryProjdou() throws IOException {
		final File slozka = tmp.newFolder("layers");
		zip(new File(slozka, "a.dem.zip"), "N50E014.hgt", hgt(teren()));
		Files.write(new File(slozka, "N49E014.hgt").toPath(), hgt(teren()));
		int hgt = 0;
		int jine = 0;
		for (final DemFile f : new SlouceneDem(new DemFolderFS(slozka)).files()) {
			if (f.getName().toLowerCase().endsWith(".hgt")) {
				hgt++;
			} else {
				jine++;
			}
		}
		Assert.assertEquals(2, hgt);
		Assert.assertEquals(1, jine);
	}
}
