package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Vykreslení dlaždic ze souboru .map: syntetická mapa {@code kukov.map} (vymyšlené město u 50° s. š., 14,4° v. d.). */
public class OfflineMapyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	static final KaLoc STRED_Z15 = KaLoc.ofJZ(new Wgs(50.003, 14.405).toMou(), 15);
	private static final KaLoc MIMO_Z15 = KaLoc.ofJZ(new Wgs(48.0, 17.0).toMou(), 15);

	private final AtomicInteger zmen = new AtomicInteger();
	private File slozka;
	private OfflineMapy mapy;

	@Before
	public void setUp() throws Exception {
		slozka = tmp.newFolder("offline-mapy");
		mapy = new OfflineMapy(zmen::incrementAndGet);
		mapy.kontrolaSlozkyNs = 0;
		mapy.nastav(slozka, TemaOfflineMapy.VYCHOZI);
	}

	@After
	public void tearDown() {
		mapy.zavri();
	}

	static void zkopirujMapu(final File slozka, final String jmeno) throws IOException {
		try (InputStream in = OfflineMapyTest.class.getResourceAsStream("/offline-mapy/kukov.map")) {
			Files.copy(in, new File(slozka, jmeno).toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private BufferedImage vyrendruj(final KaLoc loc) throws IOException {
		final OfflineRenderer r = mapy.pouzij();
		try {
			Assert.assertTrue(r.pokryva(loc));
			return r.vyrendruj(loc);
		} finally {
			r.skonci();
		}
	}

	private String klic() throws IOException {
		final OfflineRenderer r = mapy.pouzij();
		r.skonci();
		return r.getKlic();
	}

	private static int[] pixely(final BufferedImage img) {
		return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
	}

	private static int barev(final int[] pixely) {
		final Set<Integer> barvy = new HashSet<>();
		for (final int p : pixely) {
			barvy.add(p);
		}
		return barvy.size();
	}

	@Test
	public void bezMapyJeChybaSeSlozkou() {
		try {
			mapy.pouzij();
			Assert.fail();
		} catch (final IOException e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains(slozka.toString()));
			Assert.assertTrue(e.getMessage(), e.getMessage().contains(".map"));
		}
	}

	@Test
	public void vykresliDlazdiciZMapy() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		final BufferedImage img = vyrendruj(STRED_Z15);
		Assert.assertEquals(256, img.getWidth());
		Assert.assertEquals(256, img.getHeight());
		Assert.assertTrue("silnice, domy, voda a popisky", barev(pixely(img)) > 10);
		final byte[] data = new Ukladanec(new Ka(STRED_Z15, EKaType.OFFLINE_MF), "o00000000", img, null).dataKUlozeni();
		final BufferedImage png = ImageIO.read(new ByteArrayInputStream(data));
		Assert.assertArrayEquals(pixely(img), png.getRGB(0, 0, 256, 256, null, 0, 256));
	}

	@Test
	public void mapaPridanaZaBehuSePouzije() throws Exception {
		try {
			mapy.pouzij();
			Assert.fail();
		} catch (final IOException e) {
			// zatím bez mapy
		}
		zkopirujMapu(slozka, "kukov.MAP");
		Assert.assertNotNull(vyrendruj(STRED_Z15));
		Assert.assertEquals("dlaždice s chybou se musí vykreslit znovu", 1, zmen.get());
	}

	/** První otevření nic nezneplatňuje: ohlášení změny by zahodilo dlaždice, které se právě vykreslují. */
	@Test
	public void prvniOtevreniNehlasiZmenu() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		Assert.assertNotNull(vyrendruj(STRED_Z15));
		Assert.assertEquals(0, zmen.get());
		zkopirujMapu(slozka, "druha.map");
		Assert.assertNotNull(vyrendruj(STRED_Z15));
		Assert.assertEquals(1, zmen.get());
		mapy.nastav(slozka, TemaOfflineMapy.zTextu("OSMARENDER"));
		Assert.assertNotNull(vyrendruj(STRED_Z15));
		Assert.assertEquals(2, zmen.get());
	}

	@Test
	public void mimoMapuNepokryva() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		final OfflineRenderer r = mapy.pouzij();
		try {
			Assert.assertFalse(r.pokryva(MIMO_Z15));
			Assert.assertTrue(r.pokryva(STRED_Z15));
		} finally {
			r.skonci();
		}
	}

	/** Popisky přes hranice dlaždic nesmí záviset na tom, v jakém pořadí se sousední dlaždice vykreslí. */
	@Test
	public void vykresleniNezavisiNaPoradi() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		final int[] poprve = pixely(vyrendruj(STRED_Z15));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				vyrendruj(KaLoc.ofJZ(new Wgs(50.003 + dy * 0.005, 14.405 + dx * 0.009).toMou(), 15));
			}
		}
		Assert.assertArrayEquals(poprve, pixely(vyrendruj(STRED_Z15)));
	}

	@Test
	public void klicJeStabilniAKratky() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		final String klic = klic();
		Assert.assertTrue(klic, klic.matches("o[0-9a-f]{8}"));
		mapy.zavri();
		Assert.assertEquals(klic, klic());
	}

	@Test
	public void klicSeMeniSMapouATematem() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		final String puvodni = klic();
		final File mapa = new File(slozka, "kukov.map");
		Assert.assertTrue(mapa.setLastModified(mapa.lastModified() - 60_000));
		Assert.assertEquals("stejná mapa zkopírovaná znovu", puvodni, klic());
		zkopirujMapu(slozka, "druha.map");
		final String dveMapy = klic();
		Assert.assertNotEquals(puvodni, dveMapy);
		mapy.nastav(slozka, TemaOfflineMapy.zTextu("OSMARENDER"));
		Assert.assertNotEquals(dveMapy, klic());
	}

	/** Načtení velkého tématu trvá sekundy, nová mapa ve složce ho nesmí načítat znovu. */
	@Test
	public void temaSeNacitaJenJednou() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		OfflineRenderer r = mapy.pouzij();
		r.skonci();
		final OfflineRenderer.NacteneTema tema = r.getTema();
		zkopirujMapu(slozka, "druha.map");
		r = mapy.pouzij();
		r.skonci();
		Assert.assertSame(tema, r.getTema());
		mapy.nastav(slozka, TemaOfflineMapy.zTextu("OSMARENDER"));
		r = mapy.pouzij();
		r.skonci();
		Assert.assertNotSame(tema, r.getTema());
	}

	@Test
	public void dveStejneMapyNaraz() throws Exception {
		zkopirujMapu(slozka, "a.map");
		zkopirujMapu(slozka, "b.map");
		Assert.assertTrue(barev(pixely(vyrendruj(STRED_Z15))) > 10);
	}

	@Test
	public void poskozenaMapaJeChyba() throws Exception {
		Files.write(new File(slozka, "vadna.map").toPath(), "toto není mapa".getBytes(StandardCharsets.UTF_8));
		try {
			mapy.pouzij();
			Assert.fail();
		} catch (final IOException e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("vadna.map"));
		}
	}

	@Test
	public void chybejiciTemaKresliVychozim() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		final String vychozi = klic();
		mapy.nastav(slozka, TemaOfflineMapy.zeSouboru(new File(slozka, "neni.zip"), null));
		final String klic = klic();
		Assert.assertNotNull(mapy.getChybaTematu());
		Assert.assertEquals(vychozi, klic);
		Assert.assertTrue(barev(pixely(vyrendruj(STRED_Z15))) > 10);
	}

	/** Téma ze zipu se čte přímo, soubor se nerozbaluje ani nemění. */
	@Test
	public void temaZeZipu() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		final File zip = new File(tmp.getRoot(), "cervene.zip");
		try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
			out.putNextEntry(new ZipEntry("cervene/cervene.xml"));
			out.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rendertheme xmlns=\"http://mapsforge.org/renderTheme\" version=\"5\" map-background=\"#FF0000\">"
					+ "<rule e=\"way\" k=\"building\" v=\"*\"><area fill=\"#0000FF\"/></rule></rendertheme>").getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		final long velikost = zip.length();
		final long cas = zip.lastModified();
		mapy.nastav(slozka, TemaOfflineMapy.zeSouboru(zip, null));
		final int[] pixely = pixely(vyrendruj(STRED_Z15));
		Assert.assertNull(mapy.getChybaTematu());
		final Set<Integer> barvy = new HashSet<>();
		for (final int p : pixely) {
			barvy.add(p & 0xFFFFFF);
			Assert.assertEquals("jen červená, modrá a jejich vyhlazené přechody", 0, p & 0x00FF00);
		}
		Assert.assertTrue(barvy.contains(0xFF0000));
		Assert.assertTrue(barvy.contains(0x0000FF));
		Assert.assertEquals(velikost, zip.length());
		Assert.assertEquals(cas, zip.lastModified());
	}

	private static void zip(final File zip, final String... polozky) throws IOException {
		try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
			for (final String p : polozky) {
				out.putNextEntry(new ZipEntry(p));
				out.closeEntry();
			}
		}
	}

	@Test
	public void temataVeSlozce() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		zip(new File(slozka, "paws_5.zip"), "paws_5.xml", "symbols/a.svg");
		zip(new File(slozka, "dve.zip"), "b.xml", "a.xml");
		zip(new File(slozka, "bez.zip"), "readme.txt");
		Files.write(new File(slozka, "moje.xml").toPath(), new byte[0]);
		Files.write(new File(slozka, "vadny.zip").toPath(), "není zip".getBytes(StandardCharsets.UTF_8));
		final List<String> nazvy = new ArrayList<>();
		for (final TemaOfflineMapy t : TemaOfflineMapy.temataVeSlozce(slozka)) {
			nazvy.add(t.getNazev());
		}
		Assert.assertEquals(Arrays.asList("dve.zip – a.xml", "dve.zip – b.xml", "moje.xml", "paws_5.zip"), nazvy);
		Assert.assertEquals(Collections.emptyList(), TemaOfflineMapy.temataVeSlozce(new File(slozka, "neni")));
	}

	/** Vykreslené symboly tématu se ukládají na disk a při dalším načtení tématu se použijí; dlaždice vypadá stejně. */
	@Test
	public void symbolyTematuZDisku() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		final File zip = new File(tmp.getRoot(), "pivo.zip");
		try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
			out.putNextEntry(new ZipEntry("pivo.xml"));
			out.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rendertheme xmlns=\"http://mapsforge.org/renderTheme\" version=\"5\" map-background=\"#FFFFFF\">"
					+ "<rule e=\"node\" k=\"amenity\" v=\"pub\"><symbol src=\"file:pivo.svg\" symbol-width=\"40\"/></rule></rendertheme>").getBytes(StandardCharsets.UTF_8));
			out.putNextEntry(new ZipEntry("pivo.svg"));
			out.write(("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"20\" height=\"20\"><circle cx=\"10\" cy=\"10\" r=\"8\" fill=\"#C08000\"/></svg>").getBytes(StandardCharsets.UTF_8));
		}
		final File symboly = tmp.newFolder("symboly");
		final TemaOfflineMapy tema = TemaOfflineMapy.zeSouboru(zip, null);
		mapy.setSlozkaSymbolu(symboly);
		mapy.nastav(slozka, tema);
		final KaLoc hospoda = KaLoc.ofJZ(new Wgs(50.003, 14.403).toMou(), 15);
		final int[] poprve = pixely(vyrendruj(hospoda));
		Assert.assertTrue("symbol je na dlaždici", barev(poprve) > 2);
		final File soubor = OfflineRenderer.souborSymbolu(symboly, tema);
		Assert.assertTrue(soubor.isFile());
		Assert.assertEquals(1, new GrafikaOfflineMapy(soubor).pocetSymbolu());

		mapy.zavri();
		final OfflineMapy znovu = new OfflineMapy(() -> {});
		znovu.kontrolaSlozkyNs = 0;
		znovu.setSlozkaSymbolu(symboly);
		znovu.nastav(slozka, tema);
		final OfflineRenderer r = znovu.pouzij();
		try {
			Assert.assertArrayEquals(poprve, pixely(r.vyrendruj(hospoda)));
		} finally {
			r.skonci();
			znovu.zavri();
		}
	}

	/** Na displeji s měřítkem má dlaždice víc pixelů a vlastní místo v cache. */
	@Test
	public void dlazdiceProMeritkoDispleje() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		final String klic100 = klic();
		Assert.assertEquals(256, vyrendruj(STRED_Z15).getWidth());
		mapy.nastavMeritko(1.5);
		final BufferedImage img150 = vyrendruj(STRED_Z15);
		Assert.assertEquals(384, img150.getWidth());
		Assert.assertEquals(384, img150.getHeight());
		final String klic150 = klic();
		Assert.assertNotEquals(klic100, klic150);
		Assert.assertEquals("změna měřítka zneplatní dlaždice v paměti", 1, zmen.get());
		mapy.nastavMeritko(2);
		Assert.assertEquals(512, vyrendruj(STRED_Z15).getWidth());
		mapy.nastavMeritko(1.5);
		Assert.assertEquals(klic150, klic());
	}

	@Test
	public void meritkoNaCtvrtiny() {
		Assert.assertEquals(1, OfflineMapy.zaokrouhliMeritko(1.0), 0);
		Assert.assertEquals(1.25, OfflineMapy.zaokrouhliMeritko(1.24), 0);
		Assert.assertEquals(1.5, OfflineMapy.zaokrouhliMeritko(1.5), 0);
		Assert.assertEquals(1, OfflineMapy.zaokrouhliMeritko(0.5), 0);
		Assert.assertEquals(3, OfflineMapy.zaokrouhliMeritko(4), 0);
		Assert.assertEquals(1, OfflineMapy.zaokrouhliMeritko(Double.NaN), 0);
	}

	@Test
	public void temaZTextuAZpet() {
		Assert.assertSame(TemaOfflineMapy.VYCHOZI, TemaOfflineMapy.zTextu(""));
		Assert.assertSame(TemaOfflineMapy.VYCHOZI, TemaOfflineMapy.zTextu(null));
		Assert.assertEquals("OSMARENDER", TemaOfflineMapy.zTextu("OSMARENDER").naText());
		final TemaOfflineMapy zip = TemaOfflineMapy.zeSouboru(new File("x", "paws_5.zip"), "paws_5.xml");
		Assert.assertEquals(zip, TemaOfflineMapy.zTextu(zip.naText()));
		Assert.assertEquals(new File("x", "paws_5.zip"), TemaOfflineMapy.zTextu(zip.naText()).getSoubor());
	}

	@Test
	public void zavreniPockaNaRozdelaneVykresleni() throws Exception {
		zkopirujMapu(slozka, "kukov.map");
		final OfflineRenderer r = mapy.pouzij();
		mapy.zavri();
		Assert.assertFalse(r.isZavreny());
		Assert.assertNotNull(r.vyrendruj(STRED_Z15));
		r.skonci();
		Assert.assertTrue(r.isZavreny());
		Assert.assertFalse(r.zacni());
	}
}
