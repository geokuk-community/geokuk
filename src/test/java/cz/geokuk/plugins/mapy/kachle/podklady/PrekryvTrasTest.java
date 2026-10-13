package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/**
 * Překryv Turistické a cyklotrasy: syntetická mapa {@code trasy.map} s pěšinou, po které vede červená a modrá trasa (pořadí 1 a 2), cyklotrasou a silnicí
 * bez trasy.
 */
public class PrekryvTrasTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Na pěšině s oběma trasami. */
	static final KaLoc PESINA_Z16 = KaLoc.ofJZ(new Wgs(49.997, 14.402).toMou(), 16);
	/** Jen silnice, žádná trasa. */
	private static final KaLoc SILNICE_Z17 = KaLoc.ofJZ(new Wgs(50.000, 14.414).toMou(), 17);

	private File slozka;
	private OfflineMapy trasy;
	private OfflineMapy podklad;

	@Before
	public void setUp() throws IOException {
		slozka = tmp.newFolder("offline-mapy");
		zkopirujMapu(slozka);
		trasy = new OfflineMapy(() -> {}, true);
		trasy.nastav(slozka, TemaOfflineMapy.TRASY);
		podklad = new OfflineMapy(() -> {});
		podklad.nastav(slozka, TemaOfflineMapy.VYCHOZI);
	}

	@After
	public void tearDown() {
		trasy.zavri();
		podklad.zavri();
	}

	static void zkopirujMapu(final File slozka) throws IOException {
		try (InputStream in = PrekryvTrasTest.class.getResourceAsStream("/offline-mapy-trasy/trasy.map")) {
			Files.copy(in, new File(slozka, "trasy.map").toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static BufferedImage vyrendruj(final OfflineMapy mapy, final KaLoc loc) throws IOException {
		final OfflineRenderer r = mapy.pouzij();
		try {
			return r.vyrendruj(loc);
		} finally {
			r.skonci();
		}
	}

	private static String klic(final OfflineMapy mapy) throws IOException {
		final OfflineRenderer r = mapy.pouzij();
		r.skonci();
		return r.getKlic();
	}

	/** Počet pixelů s danou barvou (krytí aspoň 200). */
	private static int pocet(final BufferedImage img, final int r, final int g, final int b) {
		int n = 0;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				final int p = img.getRGB(x, y);
				if (p >>> 24 >= 200 && Math.abs((p >> 16 & 0xff) - r) < 40 && Math.abs((p >> 8 & 0xff) - g) < 40 && Math.abs((p & 0xff) - b) < 40) {
					n++;
				}
			}
		}
		return n;
	}

	private static int pruhlednych(final BufferedImage img) {
		int n = 0;
		for (final int p : img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth())) {
			if (p >>> 24 == 0) {
				n++;
			}
		}
		return n;
	}

	@Test(timeout = 30000)
	public void trasyBarvouZnackyNaPruhlednemPozadi() throws IOException {
		final BufferedImage img = vyrendruj(trasy, PESINA_Z16);
		Assert.assertTrue("průhledné pozadí", img.getColorModel().hasAlpha());
		Assert.assertTrue("většina dlaždice průhledná: " + pruhlednych(img), pruhlednych(img) > 7L * img.getWidth() * img.getHeight() / 10);
		Assert.assertTrue("červená trasa", pocet(img, 0xE0, 0x00, 0x00) > 50);
		Assert.assertTrue("modrá trasa vedle červené", pocet(img, 0x10, 0x60, 0xE0) > 50);
	}

	@Test(timeout = 30000)
	public void silniceBezTrasySeNekresli() throws IOException {
		Assert.assertTrue("podklad silnici kreslí", pruhlednych(vyrendruj(podklad, SILNICE_Z17)) == 0);
		final BufferedImage img = vyrendruj(trasy, SILNICE_Z17);
		Assert.assertEquals("překryv bez tras je celý průhledný", (long) img.getWidth() * img.getHeight(), pruhlednych(img));
	}

	/** Úklid cache podkladu maže jen klíče s písmenem o, dlaždice překryvu mají vlastní písmeno. */
	@Test(timeout = 30000)
	public void prekryvMaVlastniKlic() throws IOException {
		final String klicTras = klic(trasy);
		final String klicPodkladu = klic(podklad);
		Assert.assertTrue(klicTras, klicTras.matches("t[0-9a-f]{8}"));
		Assert.assertTrue(klicPodkladu, klicPodkladu.matches("o[0-9a-f]{8}"));
		Assert.assertTrue(UklidOfflineCache.KLIC.matcher(klicTras).matches());
	}

	@Test
	public void temaTras() {
		Assert.assertTrue(TemaOfflineMapy.TRASY.isVestavene());
		Assert.assertFalse(TemaOfflineMapy.TRASY.isPaws());
		Assert.assertEquals("Turistické a cyklotrasy", TemaOfflineMapy.TRASY.getNazev());
		Assert.assertTrue(TemaOfflineMapy.TRASY.otiskSouboru(), TemaOfflineMapy.TRASY.otiskSouboru().matches("zdroj:trasy\\.xml:[0-9a-f]+"));
		Assert.assertTrue(TemaOfflineMapy.zeSouboru(new File("paws_5.zip"), null).isPaws());
		Assert.assertFalse(TemaOfflineMapy.zeSouboru(new File("Elevate.xml"), null).isPaws());
		Assert.assertFalse(TemaOfflineMapy.VYCHOZI.isPaws());
	}
}
