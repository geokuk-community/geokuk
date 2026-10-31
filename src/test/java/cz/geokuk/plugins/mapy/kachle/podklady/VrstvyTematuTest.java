package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Téma s vrstvami (stylemenu) se kreslí s výběrem vrstev od autora tématu. */
public class VrstvyTematuTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final String TEMA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<rendertheme xmlns=\"http://mapsforge.org/renderTheme\" version=\"5\" map-background=\"#FFFFFF\">"
			+ "<stylemenu id=\"menu\" defaultvalue=\"zaklad\" defaultlang=\"cs\">"
			+ "<layer id=\"zapnuta\" enabled=\"true\"><name lang=\"en\" value=\"Blue\"/><name lang=\"cs\" value=\"Modrá\"/><cat id=\"modre\"/></layer>"
			+ "<layer id=\"vypnuta\" enabled=\"false\"><name lang=\"en\" value=\"Red\"/><cat id=\"cervene\"/></layer>"
			+ "<layer id=\"zaklad\" visible=\"true\"><name lang=\"cs\" value=\"Základ\"/><cat id=\"zakladni\"/><overlay id=\"zapnuta\"/><overlay id=\"vypnuta\"/></layer>"
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
		final BufferedImage img = vyrendruj(otevri());
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

	@Test
	public void zvolenyStylUzivatele() throws Exception {
		otevri().skonci();
		mapy.nastav(slozka, tema.sVrstvami(VolbaVrstev.AUTOR.seStylem("jiny", "zaklad")));
		final OfflineRenderer r = mapy.pouzij();
		final BufferedImage img = vyrendruj(r);
		Assert.assertTrue(r.getTema().otiskPouziteho, r.getTema().otiskPouziteho.endsWith("\nvrstvy:menu:jiny"));
		Assert.assertTrue("jiný styl kreslí zeleně", barvy(img).contains(0x00FF00));
		Assert.assertFalse(barvy(img).contains(0x0000FF));
	}

	@Test
	public void zapnutaVrstvaUzivatele() throws Exception {
		final String autor = klic(otevri());
		mapy.nastav(slozka, tema.sVrstvami(VolbaVrstev.AUTOR.sVrstvou("vypnuta", true, false)));
		final OfflineRenderer r = mapy.pouzij();
		final BufferedImage img = vyrendruj(r);
		Assert.assertTrue("zapnutá vrstva se kreslí přes modrou", barvy(img).contains(0xFF0000));
		Assert.assertNotEquals("jiné vrstvy = jiné dlaždice v cache", autor, r.getKlic());
		Assert.assertEquals("symboly patří k souboru tématu", OfflineRenderer.souborSymbolu(slozka, tema), OfflineRenderer.souborSymbolu(slozka, tema.sVrstvami(VolbaVrstev.AUTOR.sVrstvou("vypnuta", true, false))));
	}

	@Test
	public void nabidkaVrstevProMenu() throws Exception {
		vyrendruj(otevri());
		final VrstvyTematu v = mapy.getVrstvyTematu();
		Assert.assertNotNull(v);
		Assert.assertTrue(v.getTema().stejnyZdroj(tema));
		Assert.assertEquals("zaklad", v.getVychoziStyl());
		Assert.assertEquals(Arrays.asList("Základ", "jiny"), nazvy(v.getStyly()));
		Assert.assertEquals("cs, jinak en", Arrays.asList("Modrá", "Red"), nazvy(v.getVrstvy("zaklad")));
		Assert.assertTrue(v.getVrstvy("zaklad").get(0).isZapnutaAutorem());
		Assert.assertFalse(v.getVrstvy("zaklad").get(1).isZapnutaAutorem());
		Assert.assertEquals(Collections.emptyList(), v.getVrstvy("jiny"));
		Assert.assertEquals("neznámý styl = styl autora", "zaklad", v.zvolenyStyl(VolbaVrstev.AUTOR.seStylem("neni", "zaklad")));
	}

	@Test
	public void temaBezVrstevNemaNabidku() throws Exception {
		final File slozka2 = tmp.newFolder("jina");
		OfflineMapyTest.zkopirujMapu(slozka2, "kukov.map");
		mapy = new OfflineMapy(() -> {});
		mapy.nastav(slozka2, TemaOfflineMapy.VYCHOZI);
		vyrendruj(mapy.pouzij());
		Assert.assertNull(mapy.getVrstvyTematu());
	}

	@Test
	public void volbaVTextu() {
		final VolbaVrstev v = VolbaVrstev.AUTOR.seStylem("cyklo", "vychozi").sVrstvou("a", false, true).sVrstvou("b", true, false);
		Assert.assertEquals("cyklo|-a,+b", v.naText());
		Assert.assertEquals(v, VolbaVrstev.zTextu(v.naText()));
		Assert.assertTrue("stav autora není odchylka", v.sVrstvou("a", true, true).sVrstvou("b", false, false).seStylem("vychozi", "vychozi").isAutor());
		Assert.assertEquals(VolbaVrstev.AUTOR, VolbaVrstev.zTextu(null));
		Assert.assertEquals(VolbaVrstev.AUTOR, VolbaVrstev.zTextu("nesmysl"));
		Assert.assertTrue(v.jeZapnuta("b", false));
		Assert.assertFalse(v.jeZapnuta("a", true));
		Assert.assertTrue(v.jeZapnuta("c", true));
	}

	private static List<String> nazvy(final List<VrstvyTematu.Polozka> polozky) {
		final List<String> n = new ArrayList<>();
		for (final VrstvyTematu.Polozka p : polozky) {
			n.add(p.getNazev());
		}
		return n;
	}

	private static Set<Integer> barvy(final BufferedImage img) {
		final Set<Integer> b = new HashSet<>();
		for (final int p : img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth())) {
			b.add(p & 0xFFFFFF);
		}
		return b;
	}

	private static String klic(final OfflineRenderer r) {
		r.skonci();
		return r.getKlic();
	}

	private static BufferedImage vyrendruj(final OfflineRenderer r) throws IOException {
		try {
			return r.vyrendruj(OfflineMapyTest.STRED_Z15);
		} finally {
			r.skonci();
		}
	}

	private File slozka;
	private TemaOfflineMapy tema;

	private OfflineRenderer otevri() throws IOException {
		slozka = tmp.newFolder("offline-mapy");
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final File xml = tmp.newFile("vrstvy.xml");
		Files.write(xml.toPath(), TEMA.getBytes(StandardCharsets.UTF_8));
		tema = TemaOfflineMapy.zeSouboru(xml, null);
		mapy = new OfflineMapy(() -> {});
		mapy.kontrolaSlozkyNs = 0;
		mapy.sledovatSlozku = false;
		mapy.nastav(slozka, tema);
		final OfflineRenderer r = mapy.pouzij();
		Assert.assertNull(mapy.getChybaTematu());
		return r;
	}
}
