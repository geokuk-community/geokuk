package cz.geokuk.core.napoveda;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class StahnoutAktualizaciSwingWorkerTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File release;
	private File instalace;

	@Before
	public void setUp() throws Exception {
		release = tmp.newFolder("release");
		instalace = tmp.newFolder("instalace");
		vydej("geokuk.jar", "novy jar");
		vydej("start.jar", "novy start");
		zapis("java.properties", "minimalni=1.8\n");
	}

	private void vydej(final String jmeno, final String obsah) throws Exception {
		final byte[] data = obsah.getBytes(StandardCharsets.US_ASCII);
		Files.write(new File(release, jmeno).toPath(), data);
		final byte[] soucet = MessageDigest.getInstance("SHA-256").digest(data);
		zapis(jmeno + ".sha256", String.format("%064x", new BigInteger(1, soucet)) + "  " + jmeno + "\n");
	}

	private void zapis(final String jmeno, final String obsah) throws IOException {
		Files.write(new File(release, jmeno).toPath(), obsah.getBytes(StandardCharsets.US_ASCII));
	}

	private String obsah(final String jmeno) throws IOException {
		return new String(Files.readAllBytes(new File(instalace, jmeno).toPath()), StandardCharsets.US_ASCII);
	}

	private void instaluj(final String jmeno, final String obsah) throws IOException {
		Files.write(new File(instalace, jmeno).toPath(), obsah.getBytes(StandardCharsets.US_ASCII));
	}

	@Test
	public void prenosnaStahneJarProSpoustecANahradiStart() throws Exception {
		instaluj("geokuk.jar", "stary jar");
		instaluj("start.jar", "stary start");
		StahnoutAktualizaciSwingWorker.stahni(release.toURI().toString(), instalace);
		Assert.assertEquals("stary jar", obsah("geokuk.jar"));
		Assert.assertEquals("novy jar", obsah("geokuk.jar.new"));
		Assert.assertEquals("novy start", obsah("start.jar"));
		Assert.assertFalse(new File(instalace, "geokuk.jar.part").exists());
		Assert.assertFalse(new File(instalace, "start.jar.part").exists());
	}

	@Test
	public void bezSpoustceNahradiJarHned() throws Exception {
		instaluj("geokuk.jar", "stary jar");
		StahnoutAktualizaciSwingWorker.stahni(release.toURI().toString(), instalace);
		Assert.assertEquals("novy jar", obsah("geokuk.jar"));
		Assert.assertEquals("stary jar", obsah("geokuk.jar.bak"));
		Assert.assertFalse(new File(instalace, "start.jar").exists());
	}

	@Test
	public void spatnySoucetNicNeulozi() throws Exception {
		zapis("geokuk.jar.sha256", "0000  geokuk.jar\n");
		try {
			StahnoutAktualizaciSwingWorker.stahni(release.toURI().toString(), instalace);
			Assert.fail();
		} catch (final IOException e) {
			Assert.assertEquals(0, instalace.list().length);
		}
	}

	@Test
	public void novaJavaNicNestahne() throws Exception {
		instaluj("start.jar", "stary start");
		zapis("java.properties", "minimalni=999\n");
		try {
			StahnoutAktualizaciSwingWorker.stahni(release.toURI().toString(), instalace);
			Assert.fail();
		} catch (final StahnoutAktualizaciSwingWorker.YNovaJava e) {
			Assert.assertEquals(1, instalace.list().length);
		}
	}

	@Test
	public void dostatecnaJavaStahne() throws Exception {
		zapis("java.properties", "doporucena=999\nminimalni=1.8\n");
		StahnoutAktualizaciSwingWorker.stahni(release.toURI().toString(), instalace);
		Assert.assertEquals("novy jar", obsah("geokuk.jar"));
	}

	@Test
	public void bezJavaPropertiesNicNeinstaluje() throws Exception {
		instaluj("start.jar", "stary start");
		Files.delete(new File(release, "java.properties").toPath());
		try {
			StahnoutAktualizaciSwingWorker.stahni(release.toURI().toString(), instalace);
			Assert.fail();
		} catch (final IOException e) {
			Assert.assertArrayEquals(new String[] { "start.jar" }, instalace.list());
		}
	}

	@Test
	public void poStazeniNezustanouDocasneSoubory() throws Exception {
		instaluj("start.jar", "stary start");
		StahnoutAktualizaciSwingWorker.stahni(release.toURI().toString(), instalace);
		for (final String jmeno : instalace.list()) {
			Assert.assertFalse(jmeno, jmeno.endsWith(".part"));
		}
	}

	@Test
	public void souberneStazeniNejdeSpustit() {
		StahnoutAktualizaciSwingWorker.skoncilo();
		Assert.assertTrue(StahnoutAktualizaciSwingWorker.zacni());
		Assert.assertFalse(StahnoutAktualizaciSwingWorker.zacni());
		StahnoutAktualizaciSwingWorker.skoncilo();
		Assert.assertTrue(StahnoutAktualizaciSwingWorker.zacni());
		StahnoutAktualizaciSwingWorker.skoncilo();
	}

	@Test
	public void soucetSouboruNaDisku() throws Exception {
		final File f = new File(instalace, "x");
		Files.write(f.toPath(), "novy jar".getBytes(StandardCharsets.US_ASCII));
		final byte[] soucet = MessageDigest.getInstance("SHA-256").digest("novy jar".getBytes(StandardCharsets.US_ASCII));
		Assert.assertEquals(String.format("%064x", new BigInteger(1, soucet)), StahnoutAktualizaciSwingWorker.soucet(f.toPath()));
	}
}
