package cz.geokuk.core.render;

import java.awt.Dimension;
import java.awt.Point;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.*;

import org.junit.*;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;

/** Soubor .map pro OziExplorer nad pevným výřezem: hlavička, rohy, kalibrační body a jejich souřadnice v obrázku. */
public class OziMapaSouborTest {

	private static final Coord COORD = new Coord(14, new Wgs(50.0875, 14.4214).toMou(), new Dimension(1824, 892), 0.0);

	private static String mapa(final Coord coord, final int kalibrBodu) throws Exception {
		final Method m = OziExplorerRenderSwingWorker.class.getDeclaredMethod("printOziMetafile", PrintWriter.class, String.class, int.class, int.class, Coord.class, int.class, List.class);
		m.setAccessible(true);
		final StringWriter sw = new StringWriter();
		try (PrintWriter p = new PrintWriter(sw)) {
			m.invoke(new OziExplorerRenderSwingWorker(EWhatRender.OZI_EXPLORER), p, "mapa.png", coord.getDim().width, coord.getDim().height, coord, kalibrBodu,
					new RenderModel().spocitejKalibracniBody(coord, kalibrBodu));
		}
		return sw.toString();
	}

	private static List<int[]> kalibracniBodyXy(final String mapa) {
		final List<int[]> body = new ArrayList<>();
		final Matcher m = Pattern.compile("Point\\d+,xy,(-?\\d+),(-?\\d+),in").matcher(mapa);
		while (m.find()) {
			body.add(new int[] { Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) });
		}
		return body;
	}

	@Test
	public void hlavickaARozmeryObrazku() throws Exception {
		final String text = mapa(COORD, 4);
		final List<String> radky = Arrays.asList(text.split("\\R"));
		Assert.assertEquals("OziExplorer Map Data File Version 2.2", radky.get(0));
		Assert.assertEquals("mapa.png", radky.get(1));
		Assert.assertTrue(radky.contains("MMPXY,1,0,0"));
		Assert.assertTrue(radky.contains("MMPXY,2,1824,0"));
		Assert.assertTrue(radky.contains("MMPXY,3,1824,892"));
		Assert.assertTrue(radky.contains("MMPXY,4,0,892"));
	}

	@Test
	public void rohyMapyOdpovidajiVyrezu() throws Exception {
		final List<String> radky = Arrays.asList(mapa(COORD, 4).split("\\R"));
		final Wgs sz = COORD.transform(new Point(0, 0)).toWgs();
		final Wgs jv = COORD.transform(new Point(1824, 892)).toWgs();
		Assert.assertTrue(radky.toString(), radky.contains(String.format(Locale.ENGLISH, "MMPLL,1,  %f,%f", sz.lon, sz.lat)));
		Assert.assertTrue(radky.contains(String.format(Locale.ENGLISH, "MMPLL,3,  %f,%f", jv.lon, jv.lat)));
		Assert.assertTrue("sever je nahoře", sz.lat > jv.lat);
		Assert.assertTrue("západ je vlevo", sz.lon < jv.lon);
	}

	@Test
	public void kalibracniBodyJsouRozlozeneVRozmeruObrazku() throws Exception {
		for (final int pocet : new int[] { 4, 9, 16 }) {
			final List<int[]> body = kalibracniBodyXy(mapa(COORD, pocet));
			Assert.assertEquals(pocet, body.size());
			final int strana = (int) Math.sqrt(pocet);
			final Set<Integer> xs = new TreeSet<>();
			final Set<Integer> ys = new TreeSet<>();
			for (final int[] b : body) {
				xs.add(b[0]);
				ys.add(b[1]);
				Assert.assertTrue(pocet + ": x v obrázku " + b[0], b[0] >= 0 && b[0] <= COORD.getWidth());
			}
			Assert.assertEquals(strana, xs.size());
			Assert.assertEquals(strana, ys.size());
			Assert.assertEquals(0, (int) ((TreeSet<Integer>) xs).first());
			Assert.assertEquals(COORD.getWidth(), (int) ((TreeSet<Integer>) xs).last());
		}
	}

	@Test
	public void obrazekMaRozmerVyrezuAMapaStejnyRozmerVHlavicce() throws Exception {
		final RenderParams p = new RenderParams();
		p.roord = COORD;
		final java.awt.image.BufferedImage png = new Rendrovadlo(new java.util.concurrent.CompletableFuture<>()).createImage(p);
		Assert.assertEquals(1824, png.getWidth());
		Assert.assertEquals(892, png.getHeight());
		Assert.assertEquals(java.awt.image.BufferedImage.TYPE_3BYTE_BGR, png.getType());
		p.pruhledne = true;
		Assert.assertEquals(java.awt.image.BufferedImage.TYPE_INT_ARGB, new Rendrovadlo(new java.util.concurrent.CompletableFuture<>()).createImage(p).getType());

		final java.io.File dir = java.nio.file.Files.createTempDirectory("ozi").toFile();
		final java.io.File obrazek = new java.io.File(dir, "mapa.png");
		final java.io.File soubor = new java.io.File(dir, "mapa.map");
		try {
			OziExplorerRenderSwingWorker.zapisVystupy(() -> png, "png", obrazek, soubor, pw -> pw.println(MMPXY_RADEK));
			final java.awt.image.BufferedImage nacteny = javax.imageio.ImageIO.read(obrazek);
			Assert.assertEquals(1824, nacteny.getWidth());
			Assert.assertEquals(892, nacteny.getHeight());
			Assert.assertTrue(soubor.isFile());
		} finally {
			obrazek.delete();
			soubor.delete();
			dir.delete();
		}
	}

	private static final String MMPXY_RADEK = "MMPXY,3,1824,892";

	@Test
	public void kalibracniBodyLeziVRozmeruObrazkuPresne() throws Exception {
		for (final int moumer : new int[] { 10, 12, 14, 16 }) {
			for (final Wgs stred : new Wgs[] { new Wgs(50.0875, 14.4214), new Wgs(49.2, 16.6), new Wgs(48.9, 12.3) }) {
				final Coord coord = new Coord(moumer, stred.toMou(), new Dimension(1824, 892), 0.0);
				final List<int[]> body = kalibracniBodyXy(mapa(coord, 4));
				Assert.assertEquals(4, body.size());
				for (final int[] p : body) {
					Assert.assertTrue("moumer " + moumer + " " + stred + ": bod " + p[0] + "," + p[1] + " mimo obrázek 1824×892", p[0] >= 0 && p[0] <= 1824 && p[1] >= 0 && p[1] <= 892);
				}
				Assert.assertArrayEquals(new int[] { 1824, 892 }, body.get(3));
			}
		}
	}
}
