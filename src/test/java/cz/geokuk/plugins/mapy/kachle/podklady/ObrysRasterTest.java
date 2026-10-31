package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.mapy.sprava.PolygonPokryti;

public class ObrysRasterTest {

	private static final String CTVEREC = "c\n1\n 10 10\n 12 10\n 12 12\n 10 12\nEND\nEND\n";

	private static PolygonPokryti ctverec() throws IOException {
		return PolygonPokryti.nacti(new java.io.StringReader(CTVEREC));
	}

	@Test
	public void rozsireniJeStejneJakoPuvodniNaNahodnychMrizkach() {
		final Random rnd = new Random(42);
		for (int pokus = 0; pokus < 300; pokus++) {
			final int radku = 1 + rnd.nextInt(40);
			final int sloupcu = 1 + rnd.nextInt(40);
			final boolean[][] zaklad = new boolean[radku][sloupcu];
			final int hustota = rnd.nextInt(4);
			for (int r = 0; r < radku; r++) {
				for (int s = 0; s < sloupcu; s++) {
					zaklad[r][s] = hustota == 0 ? rnd.nextInt(60) == 0 : hustota == 1 ? rnd.nextInt(10) == 0 : hustota == 2 ? rnd.nextBoolean() : false;
				}
			}
			final int n = rnd.nextInt(50);
			final boolean[][] ocekavane = new boolean[radku][sloupcu];
			final boolean[][] skutecne = new boolean[radku][sloupcu];
			ObrysRasterPuvodni.rozsir(zaklad, ocekavane, n, radku, sloupcu);
			ObrysRaster.rozsir(zaklad, skutecne, n, radku, sloupcu);
			Assert.assertTrue("pokus " + pokus + ", n " + n + ", " + radku + "×" + sloupcu, Arrays.deepEquals(ocekavane, skutecne));
		}
	}

	@Test
	public void rastrJeStejnyJakoPuvodniProVsechnyKatalogoveObrysy() throws Exception {
		final List<Path> obrysy = poly();
		Assert.assertFalse("v programu jsou obrysy map", obrysy.isEmpty());
		for (final Path p : obrysy) {
			final PolygonPokryti obrys;
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.US_ASCII)) {
				obrys = PolygonPokryti.nacti(r);
			}
			final int[][] ocekavane = new ObrysRasterPuvodni(obrys, OblastniKlic.BUFFER_OBRYSU).soucty;
			final int[][] skutecne = soucty(new ObrysRaster(obrys, OblastniKlic.BUFFER_OBRYSU));
			Assert.assertTrue(p.getFileName().toString(), Arrays.deepEquals(ocekavane, skutecne));
		}
	}

	@Test
	public void stejnyObrysSeVypocitaJednou() throws Exception {
		final PolygonPokryti obrys = ctverec();
		final ObrysRaster a = ObrysRaster.pro(obrys, OblastniKlic.BUFFER_OBRYSU);
		Assert.assertSame(a, ObrysRaster.pro(obrys, OblastniKlic.BUFFER_OBRYSU));
		Assert.assertNotSame("jiný buffer je jiný raster", a, ObrysRaster.pro(obrys, 0.3));
		Assert.assertNotSame("jiný obrys je jiný raster", a, ObrysRaster.pro(ctverec(), OblastniKlic.BUFFER_OBRYSU));
	}

	private static int[][] soucty(final ObrysRaster r) throws ReflectiveOperationException {
		final java.lang.reflect.Field f = ObrysRaster.class.getDeclaredField("soucty");
		f.setAccessible(true);
		return (int[][]) f.get(r);
	}

	private static List<Path> poly() throws Exception {
		final Path koren = Paths.get(new File("src/main/resources").toURI());
		try (Stream<Path> s = Files.walk(koren)) {
			return s.filter(p -> p.getFileName().toString().endsWith(".poly")).collect(Collectors.toList());
		}
	}
}
