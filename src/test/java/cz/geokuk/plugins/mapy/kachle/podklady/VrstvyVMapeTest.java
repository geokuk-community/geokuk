package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/** Vrstvy ze složky vrstev se kreslí jedním průchodem se základní mapou, DEM dlaždice dávají stínování terénu; do pokrytí podrobnou mapou se nepočítají. */
public class VrstvyVMapeTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File slozka;
	private OfflineMapy mapy;

	@Before
	public void setUp() throws Exception {
		slozka = tmp.newFolder("offline-mapy");
		mapy = new OfflineMapy(() -> {});
		mapy.kontrolaSlozkyNs = 0;
		mapy.nastav(slozka, TemaOfflineMapy.VYCHOZI);
	}

	@After
	public void tearDown() {
		mapy.zavri();
	}

	private File vrstvy() {
		final File v = new File(slozka, OfflineMapy.SLOZKA_VRSTEV);
		v.mkdirs();
		return v;
	}

	private String klic() throws IOException {
		final OfflineRenderer r = mapy.pouzij();
		r.skonci();
		return r.getKlic();
	}

	private int pocetMap() throws IOException {
		final OfflineRenderer r = mapy.pouzij();
		try {
			return r.getPocetMap();
		} finally {
			r.skonci();
		}
	}

	private BufferedImage vyrendruj() throws IOException {
		final OfflineRenderer r = mapy.pouzij();
		try {
			return r.vyrendruj(OfflineMapyTest.STRED_Z15);
		} finally {
			r.skonci();
		}
	}

	/** DEM dlaždice 3″ (1201 × 1201, int16 big endian) se svahem k východu; pokrývá Prahu. */
	private void napisDem(final File kam) throws IOException {
		final int n = 1201;
		final ByteBuffer b = ByteBuffer.allocate(n * n * 2);
		for (int r = 0; r < n; r++) {
			for (int c = 0; c < n; c++) {
				b.putShort((short) (200 + c / 2 + (r % 7)));
			}
		}
		Files.write(kam.toPath(), b.array());
	}

	/** Vrstva v podsložce změní klíč dlaždic (cache se nepoužije ze staré podoby) a nepočítá se jako další mapa. */
	@Test
	public void vrstvaSeKresliSeZakladnimaAPocetMapSeNemeni() throws Exception {
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final String bez = klic();
		Assert.assertEquals(1, pocetMap());
		OfflineMapyTest.zkopirujMapu(vrstvy(), "kukov_trasy.map");
		mapy.zkontrolujSlozku();
		mapy.zkontrolujSlozku();
		final String s = klic();
		Assert.assertNotEquals("klíč dlaždic zahrnuje vrstvy", bez, s);
		Assert.assertEquals("vrstva není základní mapa", 1, pocetMap());
		Assert.assertNotNull(vyrendruj());
		Assert.assertTrue(new File(vrstvy(), "kukov_trasy.map").delete());
		mapy.zkontrolujSlozku();
		mapy.zkontrolujSlozku();
		Assert.assertEquals("bez vrstvy je klíč zpět", bez, klic());
	}

	/** Samotné vrstvy bez základní mapy nejsou mapa: dál platí „ve složce nejsou offline mapy“. */
	@Test
	public void samotneVrstvyNejsouMapa() throws Exception {
		OfflineMapyTest.zkopirujMapu(vrstvy(), "kukov_trasy.map");
		try {
			mapy.pouzij();
			Assert.fail("bez základní mapy se nekreslí");
		} catch (final IOException e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("nejsou offline mapy"));
		}
	}

	/** Překryv tras (samostatný renderer) vrstvy nekreslí: vrstvy kreslí základní renderer. */
	@Test
	public void prekrytTrasVrstvyNepouziva() throws Exception {
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final OfflineMapy trasy = new OfflineMapy(() -> {}, true);
		try {
			trasy.kontrolaSlozkyNs = 0;
			trasy.nastav(slozka, TemaOfflineMapy.TRASY);
			final OfflineRenderer r0 = trasy.pouzij();
			r0.skonci();
			final String bez = r0.getKlic();
			OfflineMapyTest.zkopirujMapu(vrstvy(), "kukov_trasy.map");
			trasy.zkontrolujSlozku();
			trasy.zkontrolujSlozku();
			final OfflineRenderer r1 = trasy.pouzij();
			r1.skonci();
			Assert.assertEquals(bez, r1.getKlic());
		} finally {
			trasy.zavri();
		}
	}

	/** DEM dlaždice ve složce vrstev dají stínování: dlaždice vypadá jinak a má jiný klíč; bez nich zase původní. */
	@Test(timeout = 60000)
	public void demDavaStinovani() throws Exception {
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final String bez = klic();
		final BufferedImage puvodni = vyrendruj();
		napisDem(new File(vrstvy(), "N50E014.hgt"));
		mapy.zkontrolujSlozku();
		mapy.zkontrolujSlozku();
		Assert.assertNotEquals(bez, klic());
		final BufferedImage stinovana = vyrendruj();
		boolean jine = false;
		for (int y = 0; y < puvodni.getHeight() && !jine; y++) {
			for (int x = 0; x < puvodni.getWidth(); x++) {
				if (puvodni.getRGB(x, y) != stinovana.getRGB(x, y)) {
					jine = true;
					break;
				}
			}
		}
		Assert.assertTrue("stínování mění dlaždici", jine);
		Assert.assertTrue(new File(vrstvy(), "N50E014.hgt").delete());
		mapy.zkontrolujSlozku();
		mapy.zkontrolujSlozku();
		Assert.assertEquals(bez, klic());
	}

	/** Dlaždice ve formátu vrstvy DEM (zip s HGT uvnitř) se stínují stejně jako holý soubor. */
	@Test(timeout = 60000)
	public void zazipovanaDemDavaStinovani() throws Exception {
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final BufferedImage puvodni = vyrendruj();
		final File hgt = new File(tmp.getRoot(), "N50E014.hgt");
		napisDem(hgt);
		try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(new File(vrstvy(), "czech_republic_hgt.zip")))) {
			zip.putNextEntry(new java.util.zip.ZipEntry("N50E014.hgt"));
			zip.write(Files.readAllBytes(hgt.toPath()));
			zip.closeEntry();
		}
		mapy.zkontrolujSlozku();
		mapy.zkontrolujSlozku();
		final BufferedImage stinovana = vyrendruj();
		boolean jine = false;
		for (int y = 0; y < puvodni.getHeight() && !jine; y++) {
			for (int x = 0; x < puvodni.getWidth(); x++) {
				if (puvodni.getRGB(x, y) != stinovana.getRGB(x, y)) {
					jine = true;
					break;
				}
			}
		}
		Assert.assertTrue("stínování ze zipu mění dlaždici", jine);
	}
}
