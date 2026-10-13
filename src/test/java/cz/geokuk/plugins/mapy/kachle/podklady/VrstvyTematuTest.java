package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Téma s vrstvami (stylemenu) se kreslí s výběrem vrstev od autora tématu. */
public class VrstvyTematuTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final String TEMA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<rendertheme xmlns=\"http://mapsforge.org/renderTheme\" version=\"5\" map-background=\"#FFFFFF\">"
			+ "<stylemenu id=\"menu\" defaultvalue=\"zaklad\" defaultlang=\"cs\">"
			+ "<layer id=\"zapnuta\" enabled=\"true\"><cat id=\"modre\"/></layer>"
			+ "<layer id=\"vypnuta\" enabled=\"false\"><cat id=\"cervene\"/></layer>"
			+ "<layer id=\"zaklad\" visible=\"true\"><cat id=\"zakladni\"/><overlay id=\"zapnuta\"/><overlay id=\"vypnuta\"/></layer>"
			+ "<layer id=\"jiny\" visible=\"true\"><cat id=\"jine\"/></layer>"
			+ "</stylemenu>"
			+ "<rule cat=\"zakladni\" e=\"way\" k=\"highway\" v=\"*\"><line stroke=\"#000000\" stroke-width=\"4\"/></rule>"
			+ "<rule cat=\"modre\" e=\"way\" k=\"building\" v=\"*\"><area fill=\"#0000FF\"/></rule>"
			+ "<rule cat=\"cervene\" e=\"way\" k=\"building\" v=\"*\"><area fill=\"#FF0000\"/></rule>"
			+ "<rule cat=\"jine\" e=\"way\" k=\"building\" v=\"*\"><area fill=\"#00FF00\"/></rule>"
			+ "</rendertheme>";

	private OfflineMapy mapy;

	@After
	public void tearDown() {
		if (mapy != null) {
			mapy.zavri();
		}
	}

	@Test
	public void kresliJenVychoziVrstvyAutora() throws Exception {
		final OfflineRenderer r = otevri();
		final BufferedImage img;
		try {
			img = r.vyrendruj(OfflineMapyTest.STRED_Z15);
		} finally {
			r.skonci();
		}
		boolean modra = false;
		boolean cerna = false;
		for (final int p : img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth())) {
			modra |= (p & 0xFFFFFF) == 0x0000FF;
			cerna |= (p & 0xFFFFFF) == 0;
			final int cervena = p >> 16 & 0xFF;
			final int zelena = p >> 8 & 0xFF;
			Assert.assertTrue("jen bílá, modrá, černá a jejich přechody; vypnutá vrstva ani jiný styl se nekreslí: " + Integer.toHexString(p), cervena == zelena && (p & 0xFF) >= cervena);
		}
		Assert.assertTrue("zapnutá vrstva se kreslí", modra);
		Assert.assertTrue("kategorie stylu se kreslí", cerna);
	}

	@Test
	public void vybraneVrstvyJsouVKliciCache() throws Exception {
		final OfflineRenderer r = otevri();
		r.skonci();
		Assert.assertTrue(r.getTema().otiskPouziteho, r.getTema().otiskPouziteho.endsWith("\nvrstvy:menu:zaklad,zapnuta"));
	}

	@Test
	public void vestaveneTemaSVrstvami() throws Exception {
		final OfflineRenderer.NacteneTema tema = OfflineRenderer.nactiTema(TemaOfflineMapy.zTextu("BIKER"));
		try {
			Assert.assertNull(tema.chyba);
			Assert.assertTrue(tema.otiskPouziteho, tema.otiskPouziteho.contains("\nvrstvy:"));
		} finally {
			tema.uvolni();
		}
	}

	@Test
	public void temaBezVrstevMaKlicJakoDriv() throws Exception {
		final OfflineRenderer.NacteneTema tema = OfflineRenderer.nactiTema(TemaOfflineMapy.VYCHOZI);
		try {
			Assert.assertEquals(TemaOfflineMapy.VYCHOZI.otisk(), tema.otiskPouziteho);
		} finally {
			tema.uvolni();
		}
	}

	private OfflineRenderer otevri() throws IOException {
		final File slozka = tmp.newFolder("offline-mapy");
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final File xml = tmp.newFile("vrstvy.xml");
		Files.write(xml.toPath(), TEMA.getBytes(StandardCharsets.UTF_8));
		mapy = new OfflineMapy(() -> {});
		mapy.kontrolaSlozkyNs = 0;
		mapy.nastav(slozka, TemaOfflineMapy.zeSouboru(xml, null));
		final OfflineRenderer r = mapy.pouzij();
		Assert.assertNull(mapy.getChybaTematu());
		return r;
	}
}
