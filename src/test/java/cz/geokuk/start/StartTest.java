package cz.geokuk.start;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class StartTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private void zapis(final File f, final String obsah) throws Exception {
		Files.write(f.toPath(), obsah.getBytes(StandardCharsets.UTF_8));
	}

	private String cti(final File f) throws Exception {
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
	}

	@Test
	public void vymeniStazenyJar() throws Exception {
		final File d = tmp.getRoot();
		spustitelnyJar(new File(d, "geokuk.jar"));
		final byte[] stary = Files.readAllBytes(new File(d, "geokuk.jar").toPath());
		zapis(new File(d, "geokuk.jar.new"), "novy");
		Start.vymenJar(d);
		Assert.assertEquals("novy", cti(new File(d, "geokuk.jar")));
		Assert.assertArrayEquals(stary, Files.readAllBytes(new File(d, "geokuk.jar.bak").toPath()));
		Assert.assertFalse(new File(d, "geokuk.jar.new").exists());
	}

	@Test
	public void bezNovehoJaruNicNemeni() throws Exception {
		final File d = tmp.getRoot();
		zapis(new File(d, "geokuk.jar"), "stary");
		Start.vymenJar(d);
		Assert.assertEquals("stary", cti(new File(d, "geokuk.jar")));
		Assert.assertFalse(new File(d, "geokuk.jar.bak").exists());
	}

	@Test
	public void pametPolovinaRamMezi1A3GbAOd16GbRam4Gb() {
		final File nic = new File(tmp.getRoot(), "neni.xml");
		Assert.assertEquals(1024, Start.pametMb(nic, 1024));
		Assert.assertEquals(2048, Start.pametMb(nic, 4096));
		Assert.assertEquals(3072, Start.pametMb(nic, 6144));
		Assert.assertEquals(3072, Start.pametMb(nic, 8192));
		Assert.assertEquals(3072, Start.pametMb(nic, 12000));
		Assert.assertEquals(3072, Start.pametMb(nic, 15000));
		Assert.assertEquals(4096, Start.pametMb(nic, 16076));
		Assert.assertEquals(4096, Start.pametMb(nic, 16379));
		Assert.assertEquals(4096, Start.pametMb(nic, 24000));
		Assert.assertEquals(4096, Start.pametMb(nic, 32768));
	}

	@Test
	public void pametZNastaveni() throws Exception {
		final File n = tmp.newFile("nastaveni.xml");
		zapis(n, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE preferences SYSTEM \"http://java.sun.com/dtd/preferences.dtd\">"
				+ "<preferences><root type=\"user\"><map/><node name=\"geokuk\"><map/><node name=\"current\"><map/><node name=\"vseobecne\"><map>"
				+ "<entry key=\"pametMb\" value=\"6000\"/></map></node></node></node></root></preferences>");
		Assert.assertEquals(6000, Start.pametMb(n, 4096));
	}

	@Test
	public void poskozeneNastaveniNevadi() throws Exception {
		final File n = tmp.newFile("nastaveni.xml");
		zapis(n, "<preferences><root");
		Assert.assertEquals(2048, Start.pametMb(n, 4096));
	}

	@Test
	public void korenNadSlozkouProgram() throws Exception {
		final File program = tmp.newFolder("GeoKuk", "program");
		zapis(new File(program, "start.jar"), "start");
		Assert.assertEquals(program.getParentFile(), Start.koren(program));
	}

	@Test
	public void korenJeSlozkaJaruMimoPrenosnouVerzi() throws Exception {
		final File program = tmp.newFolder("program");
		Assert.assertEquals(program, Start.koren(program));
		final File jinde = tmp.newFolder("jinde");
		zapis(new File(jinde, "start.jar"), "start");
		Assert.assertEquals(jinde, Start.koren(jinde));
	}

	@Test
	public void cekaNaUkonceniBeziciInstance() throws Exception {
		final File zamek = new File(tmp.getRoot(), "data/" + Start.ZAMEK);
		final java.nio.channels.FileLock drzeny = Start.zamkni(zamek);
		Assert.assertNotNull(drzeny);
		Assert.assertNull("Druhá instance zámek nedostane", Start.zamkni(zamek));
		Assert.assertFalse(Start.pockejNaUkonceni(zamek, 500));
		drzeny.channel().close();
		Assert.assertTrue(Start.pockejNaUkonceni(zamek, 500));
	}

	@Test
	public void docasneSouboryDoData() throws Exception {
		final File data = tmp.newFolder("data");
		final List<String> prikaz = new ArrayList<>();
		Start.pridejDocasnouSlozku(prikaz, data);
		Assert.assertEquals(Collections.singletonList("-Djava.io.tmpdir=" + new File(data, "tmp").getPath()), prikaz);
		Assert.assertTrue(new File(data, "tmp").isDirectory());
	}

	@Test
	public void doNezapisovatelnychDatDocasneSouboryNe() throws Exception {
		final File data = tmp.newFile("data"); // soubor místo složky, do data/tmp nejde zapsat ani jako správce
		final List<String> prikaz = new ArrayList<>();
		Start.pridejDocasnouSlozku(prikaz, data);
		Assert.assertEquals(Collections.emptyList(), prikaz);
	}

	@Test
	public void zamekDrzenyInstanciJeZamceny() throws Exception {
		final File soubor = new File(tmp.newFolder("data-z"), Start.ZAMEK);
		final java.nio.channels.FileLock lock = Start.zamkni(soubor);
		Assert.assertNotNull(lock);
		try {
			Assert.assertTrue(Start.jeZamceno(soubor));
		} finally {
			lock.channel().close();
		}
		Assert.assertFalse(Start.jeZamceno(soubor));
	}

	@Test
	public void vNezapisovatelneSlozceNikdoNebezi() throws Exception {
		final File soubor = new File(tmp.newFile("data-soubor"), Start.ZAMEK); // soubor místo složky, nejde zapsat ani jako správce
		Assert.assertFalse(Start.jeZamceno(soubor));
		final long zacatek = System.currentTimeMillis();
		Assert.assertTrue(Start.pockejNaUkonceni(soubor, 5_000));
		Assert.assertTrue(System.currentTimeMillis() - zacatek < 2_000);
	}

	/** Drží zámek v jiném procesu, dokud se nezavře jeho standardní vstup. */
	public static class DruhaInstance {
		public static void main(final String[] args) throws IOException {
			if (Start.zamkni(new File(args[0])) == null) {
				System.exit(1);
			}
			System.out.println("zamceno");
			System.out.flush();
			while (System.in.read() >= 0) {
				// čeká na zavření vstupu
			}
		}
	}

	@Test
	public void zamekDrzenyJinymProcesemJeZamceny() throws Exception {
		final File soubor = new File(tmp.newFolder("data-p"), Start.ZAMEK);
		final Process p = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(), "-cp", System.getProperty("java.class.path"),
				DruhaInstance.class.getName(), soubor.getPath()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
		try {
			final BufferedReader vystup = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
			Assert.assertEquals("zamceno", vystup.readLine());
			Assert.assertTrue(Start.jeZamceno(soubor));
			Assert.assertNull("Druhá instance zámek nedostane", Start.zamkni(soubor));
			Assert.assertFalse(Start.pockejNaUkonceni(soubor, 500));
			p.getOutputStream().close();
			Assert.assertTrue(p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
			Assert.assertFalse(Start.jeZamceno(soubor));
		} finally {
			p.destroyForcibly();
		}
	}

	@Test
	public void kdyzVymenaSelzeSpustiStavajiciJar() throws Exception {
		final File d = tmp.newFolder();
		Files.write(new File(d, "geokuk.jar").toPath(), "stary".getBytes());
		Files.write(new File(d, "geokuk.jar.new").toPath(), "novy".getBytes());
		final File jar = Start.vyberJar(d, a -> {
			throw new java.nio.file.AccessDeniedException("geokuk.jar.new");
		});
		Assert.assertEquals(new File(d, "geokuk.jar"), jar);
	}

	@Test
	public void chybaVymenyJeVLogu() throws Exception {
		final File d = tmp.newFolder();
		Files.write(new File(d, "geokuk.jar").toPath(), "stary".getBytes());
		Start.vyberJar(d, a -> {
			throw new java.nio.file.AccessDeniedException("geokuk.jar.new");
		});
		final String log = new String(Files.readAllBytes(new File(d, "data/log/start.log").toPath()), java.nio.charset.StandardCharsets.UTF_8);
		Assert.assertTrue(log, log.contains("Výměna geokuk.jar selhala") && log.contains("geokuk.jar.new"));
	}

	@Test
	public void kdyzJarChybiSpustiBak() throws Exception {
		final File d = tmp.newFolder();
		Files.write(new File(d, "geokuk.jar.bak").toPath(), "predchozi".getBytes());
		Files.write(new File(d, "geokuk.jar.new").toPath(), "novy".getBytes());
		final File jar = Start.vyberJar(d, a -> {
			throw new java.nio.file.AccessDeniedException("geokuk.jar.new");
		});
		Assert.assertEquals(new File(d, "geokuk.jar.bak"), jar);
	}

	@Test
	public void bezJaruNeniCoSpustit() throws Exception {
		final File d = tmp.newFolder();
		Assert.assertNull(Start.vyberJar(d, Start::vymenJar));
	}

	@Test
	public void uspesnaVymenaSpustiNovy() throws Exception {
		final File d = tmp.newFolder();
		Files.write(new File(d, "geokuk.jar").toPath(), "stary".getBytes());
		Files.write(new File(d, "geokuk.jar.new").toPath(), "novy".getBytes());
		final File jar = Start.vyberJar(d, Start::vymenJar);
		Assert.assertEquals("novy", new String(Files.readAllBytes(jar.toPath())));
	}

	@Test
	public void poskozenyJarSpustiBak() throws Exception {
		final File d = tmp.newFolder();
		Files.write(new File(d, "geokuk.jar.new").toPath(), "useknutý".getBytes());
		spustitelnyJar(new File(d, "geokuk.jar"));
		Assert.assertEquals(new File(d, "geokuk.jar.bak"), Start.vyberJar(d, Start::vymenJar));
	}

	@Test
	public void spustitelnyJarSeSpusti() throws Exception {
		final File d = tmp.newFolder();
		spustitelnyJar(new File(d, "geokuk.jar"));
		spustitelnyJar(new File(d, "geokuk.jar.bak"));
		Assert.assertEquals(new File(d, "geokuk.jar"), Start.vyberJar(d, Start::vymenJar));
	}

	@Test
	public void poskozenyJarNeprepiseZalohu() throws Exception {
		final File d = tmp.newFolder();
		Files.write(new File(d, "geokuk.jar").toPath(), "useknutý".getBytes());
		spustitelnyJar(new File(d, "geokuk.jar.bak"));
		final byte[] zaloha = Files.readAllBytes(new File(d, "geokuk.jar.bak").toPath());
		spustitelnyJar(new File(d, "geokuk.jar.new"));
		Start.vymenJar(d);
		Assert.assertArrayEquals(zaloha, Files.readAllBytes(new File(d, "geokuk.jar.bak").toPath()));
		Assert.assertTrue(Start.jeSpustitelny(new File(d, "geokuk.jar")));
		Assert.assertFalse(new File(d, "geokuk.jar.new").exists());
	}

	@Test
	public void zalohaPoskozenehoJaruSeOhlasi() throws Exception {
		final File d = tmp.newFolder();
		Files.write(new File(d, "geokuk.jar").toPath(), "useknutý".getBytes());
		spustitelnyJar(new File(d, "geokuk.jar.bak"));
		final List<String> prikaz = new ArrayList<>();
		Start.pridejZalohu(prikaz, d, Start.vyberJar(d, Start::vymenJar));
		Assert.assertEquals(Collections.singletonList("-Dgeokuk.zaloha=poskozeny"), prikaz);
	}

	@Test
	public void zalohaChybejicihoJaruSeOhlasi() throws Exception {
		final File d = tmp.newFolder();
		spustitelnyJar(new File(d, "geokuk.jar.bak"));
		final List<String> prikaz = new ArrayList<>();
		Start.pridejZalohu(prikaz, d, Start.vyberJar(d, Start::vymenJar));
		Assert.assertEquals(Collections.singletonList("-Dgeokuk.zaloha=chybi"), prikaz);
	}

	@Test
	public void beznySpustNicNeohlasi() throws Exception {
		final File d = tmp.newFolder();
		spustitelnyJar(new File(d, "geokuk.jar"));
		spustitelnyJar(new File(d, "geokuk.jar.bak"));
		final List<String> prikaz = new ArrayList<>();
		Start.pridejZalohu(prikaz, d, Start.vyberJar(d, Start::vymenJar));
		Assert.assertTrue(prikaz.isEmpty());
	}

	private static void spustitelnyJar(final File f) throws Exception {
		final java.util.jar.Manifest m = new java.util.jar.Manifest();
		m.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
		m.getMainAttributes().put(java.util.jar.Attributes.Name.MAIN_CLASS, "cz.geokuk.Hlavni");
		try (java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(f), m)) {
			out.putNextEntry(new java.util.zip.ZipEntry("a.txt"));
			out.write(1);
		}
	}

	@Test
	public void vraceniPametiJenOdJavy12() {
		for (final String verze : new String[] { "1.8", "11", "nesmysl", "" }) {
			final List<String> prikaz = new ArrayList<>();
			Start.pridejVraceniPameti(prikaz, verze);
			Assert.assertEquals(verze, Collections.emptyList(), prikaz);
		}
		for (final String verze : new String[] { "12", "17", "21", "25" }) {
			final List<String> prikaz = new ArrayList<>();
			Start.pridejVraceniPameti(prikaz, verze);
			Assert.assertEquals(verze, Arrays.asList("-XX:G1PeriodicGCInterval=60000", "-XX:+ExplicitGCInvokesConcurrent", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30"), prikaz);
		}
	}

	/** Přepínače, které spouštěč přidá, Java, ve které testy běží, zná; neznámý -XX přepínač by program nespustil. */
	@Test
	public void javaPrepinaceVraceniPametiZna() throws Exception {
		final List<String> prikaz = new ArrayList<>();
		prikaz.add(new File(new File(System.getProperty("java.home"), "bin"), "java").getPath());
		Start.pridejVraceniPameti(prikaz, System.getProperty("java.specification.version"));
		Assume.assumeTrue("jen Java 12+", prikaz.size() > 1);
		prikaz.add("-version");
		final Process p = new ProcessBuilder(prikaz).redirectErrorStream(true).start();
		final StringBuilder vystup = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			for (String radek = r.readLine(); radek != null; radek = r.readLine()) {
				vystup.append(radek).append('\n');
			}
		}
		Assert.assertEquals(vystup.toString(), 0, p.waitFor());
	}
}
