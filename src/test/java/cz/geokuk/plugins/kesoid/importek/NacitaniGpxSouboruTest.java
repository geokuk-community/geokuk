package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mvc.*;
import cz.geokuk.util.file.KeFile;

/** Načítání GPX se keše: kódování a diakritika, zip s vadným záznamem, vadný soubor uprostřed, velký soubor. */
public class NacitaniGpxSouboruTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static class Model extends KesoidModel {
		Model() {
			final ProgressModel progress = new ProgressModel();
			progress.inject(udalost -> {});
			inject(progress);
			inject(new KesoidPluginManager());
		}

		@Override
		public void fire(final cz.geokuk.framework.Event0<?> udalost) {}

		@Override
		protected cz.geokuk.framework.MyPreferences currPrefe() {
			return cz.geokuk.framework.MyPreferences.current().node("test-nacitani-gpx-souboru");
		}

		@Override
		public GccomNick getGccomNick() {
			return new GccomNick("Ja", 42);
		}

		@Override
		public GsakParametryNacitani getGsakParametryNacitani() {
			return new GsakParametryNacitani();
		}

		@Override
		public boolean maSeNacist(final KeFile zdroj) {
			return true;
		}
	}

	private static final String NAZEV = "Žluťoučký kůň úpěl ďábelské ódy";

	private static String kes(final String kod) {
		return ImportKesiTest.kes(kod, "Geocache", "Traditional Cache", "Kačer", 7, true, false, "1.5", "").replace("Keš " + kod, NAZEV + " " + kod);
	}

	private static String hlava(final String kodovani) {
		return "<?xml version=\"1.0\" encoding=\"" + kodovani + "\"?>\n<gpx version=\"1.0\" xmlns=\"http://www.topografix.com/GPX/1/0\">\n";
	}

	private File zapis(final File f, final byte[] obsah) throws IOException {
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), obsah);
		return f;
	}

	private KesBag nacti(final File slozka) throws IOException {
		final MultiNacitac nacitac = new MultiNacitac(new Model());
		nacitac.setRootDirs(true, slozka, null, null, Collections.emptySet());
		return nacitac.nacti(null, new Genom());
	}

	private static Set<String> kody(final KesBag bag) {
		return bag.getKesoidy().stream().map(Kesoid::getIdentifier).collect(Collectors.toCollection(TreeSet::new));
	}

	private static Set<String> nazvy(final KesBag bag) {
		return bag.getKesoidy().stream().map(k -> k.getNazev().replaceFirst(" GC.*$", "")).collect(Collectors.toCollection(TreeSet::new));
	}

	private void kodovani(final String deklarace, final Charset charset, final boolean bom) throws Exception {
		final File slozka = tmp.newFolder();
		byte[] telo = (hlava(deklarace) + kes("GC1A") + "</gpx>").getBytes(charset);
		if (bom) {
			final byte[] s = new byte[telo.length + 3];
			System.arraycopy(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF }, 0, s, 0, 3);
			System.arraycopy(telo, 0, s, 3, telo.length);
			telo = s;
		}
		zapis(new File(slozka, "a.gpx"), telo);
		final KesBag bag = nacti(slozka);
		Assert.assertEquals(deklarace, Collections.singleton("GC1A"), kody(bag));
		Assert.assertEquals(deklarace, Collections.singleton(NAZEV), nazvy(bag));
	}

	@Test
	public void diakritikaVUtf8() throws Exception {
		kodovani("UTF-8", StandardCharsets.UTF_8, false);
	}

	@Test
	public void diakritikaVUtf8SeZnackou() throws Exception {
		kodovani("UTF-8", StandardCharsets.UTF_8, true);
	}

	@Test
	public void diakritikaVeWindows1250() throws Exception {
		kodovani("windows-1250", Charset.forName("windows-1250"), false);
	}

	@Test
	public void diakritikaVIso88592() throws Exception {
		kodovani("ISO-8859-2", Charset.forName("ISO-8859-2"), false);
	}

	@Test
	public void diakritikaVUtf16() throws Exception {
		kodovani("UTF-16", StandardCharsets.UTF_16, false);
	}

	@Test
	public void souborASlozkaSDiakritikouAMezerou() throws Exception {
		Assume.assumeTrue("souborový systém bez diakritiky v názvech", Charset.forName(System.getProperty("sun.jnu.encoding")).newEncoder().canEncode("žluťoučký"));
		final File slozka = tmp.newFolder("Moje keše");
		zapis(new File(slozka, "Příliš žluťoučký/kůň úpěl.gpx"), (hlava("UTF-8") + kes("GC2B") + "</gpx>").getBytes(StandardCharsets.UTF_8));
		Assert.assertEquals(Collections.singleton("GC2B"), kody(nacti(slozka)));
	}

	private static byte[] zip(final Map<String, byte[]> zaznamy) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			for (final Map.Entry<String, byte[]> e : zaznamy.entrySet()) {
				zip.putNextEntry(new ZipEntry(e.getKey()));
				zip.write(e.getValue());
				zip.closeEntry();
			}
		}
		return out.toByteArray();
	}

	private static byte[] gpx(final String... kody) {
		return (hlava("UTF-8") + Arrays.stream(kody).map(NacitaniGpxSouboruTest::kes).collect(Collectors.joining()) + "</gpx>").getBytes(StandardCharsets.UTF_8);
	}

	@Test
	public void zipSViceGpxVSlozkachADiakritikou() throws Exception {
		final File slozka = tmp.newFolder();
		final Map<String, byte[]> z = new LinkedHashMap<>();
		z.put("první.gpx", gpx("GC1A"));
		z.put("složka/druhá.gpx", gpx("GC2B", "GC3C"));
		z.put("prazdny.gpx", new byte[0]);
		z.put("poznamky.txt", "nic".getBytes(StandardCharsets.UTF_8));
		zapis(new File(slozka, "sada.zip"), zip(z));
		final KesBag bag = nacti(slozka);
		Assert.assertEquals(new TreeSet<>(Arrays.asList("GC1A", "GC2B", "GC3C")), kody(bag));
		Assert.assertEquals(Collections.singleton(NAZEV), nazvy(bag));
	}

	@Test
	public void zipSVadnymZaznamemUprostredNezahodiOstatni() throws Exception {
		final File slozka = tmp.newFolder();
		final byte[] vadny = gpx("GC9Z");
		final Map<String, byte[]> z = new LinkedHashMap<>();
		z.put("a.gpx", gpx("GC1A"));
		z.put("vadny.gpx", Arrays.copyOf(vadny, vadny.length / 2));
		z.put("c.gpx", gpx("GC3C"));
		zapis(new File(slozka, "sada.zip"), zip(z));
		final Set<String> kody = kody(nacti(slozka));
		Assert.assertTrue(kody.toString(), kody.contains("GC1A") && kody.contains("GC3C"));
	}

	@Test
	public void poskozenyZipNezastaviJinySoubor() throws Exception {
		final File slozka = tmp.newFolder();
		final byte[] dobry = zip(Collections.singletonMap("a.gpx", gpx("GC1A")));
		zapis(new File(slozka, "uriznuty.zip"), Arrays.copyOf(dobry, dobry.length - 30));
		zapis(new File(slozka, "b.gpx"), gpx("GC2B"));
		Assert.assertTrue(kody(nacti(slozka)).contains("GC2B"));
	}

	@Test
	public void gpxUriznutyUprostredZachovaKesePredChybou() throws Exception {
		final File slozka = tmp.newFolder();
		final byte[] cely = gpx("GC1A", "GC2B", "GC3C", "GC4D");
		final String text = new String(cely, StandardCharsets.UTF_8);
		final int poTretiKesi = text.indexOf("GC3C");
		zapis(new File(slozka, "a.gpx"), text.substring(0, poTretiKesi + 40).getBytes(StandardCharsets.UTF_8));
		final Set<String> kody = kody(nacti(slozka));
		Assert.assertTrue("keše před chybou se neztratí: " + kody, kody.containsAll(Arrays.asList("GC1A", "GC2B")));
		Assert.assertFalse("neúplná keš se nepřidá: " + kody, kody.contains("GC4D"));
	}

	@Test(timeout = 120000)
	public void velkySouborSeNacteProudoveAVsechnyKese() throws Exception {
		final File slozka = tmp.newFolder();
		final int pocet = 30000;
		final File f = new File(slozka, "velky.gpx");
		try (Writer w = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(f)), StandardCharsets.UTF_8)) {
			w.write(hlava("UTF-8"));
			for (int i = 0; i < pocet; i++) {
				w.write(kes(String.format("GC%05X", i)));
			}
			w.write("</gpx>");
		}
		Assert.assertTrue("soubor je opravdu velký", f.length() > 20_000_000L);
		Assert.assertEquals(pocet, nacti(slozka).getKesoidy().size());
	}
}
