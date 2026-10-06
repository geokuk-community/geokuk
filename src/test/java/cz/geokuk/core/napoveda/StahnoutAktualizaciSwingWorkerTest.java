package cz.geokuk.core.napoveda;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributeView;
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
		Assert.assertFalse("záloha spouštěče je jen na dobu výměny", new File(instalace, "start.jar.bak").exists());
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
	public void bezSpoustceNahradiJarZKterehoProgramBezi() throws Exception {
		instaluj("geokuk-6.2.0.jar", "stary jar");
		StahnoutAktualizaciSwingWorker.stahni(release.toURI().toString(), instalace, "geokuk-6.2.0.jar");
		Assert.assertEquals("novy jar", obsah("geokuk-6.2.0.jar"));
		Assert.assertEquals("stary jar", obsah("geokuk-6.2.0.jar.bak"));
		Assert.assertFalse(new File(instalace, "geokuk.jar").exists());
	}

	@Test
	public void jmenoJaruZKterehoProgramBezi() throws Exception {
		final File jinde = tmp.newFolder("jinde");
		instaluj("geokuk-6.2.0.jar", "stary jar");
		Files.write(new File(jinde, "geokuk-6.2.0.jar").toPath(), new byte[0]);
		Assert.assertEquals("geokuk-6.2.0.jar", StahnoutAktualizaciSwingWorker.jmenoJaru(new File(instalace, "geokuk-6.2.0.jar"), instalace));
		Assert.assertEquals("geokuk.jar", StahnoutAktualizaciSwingWorker.jmenoJaru(new File(jinde, "geokuk-6.2.0.jar"), instalace));
		Assert.assertEquals("geokuk.jar", StahnoutAktualizaciSwingWorker.jmenoJaru(instalace, instalace.getParentFile()));
		Assert.assertEquals("geokuk.jar", StahnoutAktualizaciSwingWorker.jmenoJaru(null, instalace));
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

	@Test
	public void stazenyJarMaBeznaPrava() throws Exception {
		Assume.assumeNotNull(Files.getFileAttributeView(instalace.toPath(), PosixFileAttributeView.class));
		instaluj("start.jar", "stary start");
		instaluj("bezny", "x");
		StahnoutAktualizaciSwingWorker.stahni(release.toURI().toString(), instalace);
		final Object bezna = Files.getPosixFilePermissions(new File(instalace, "bezny").toPath());
		Assert.assertEquals(bezna, Files.getPosixFilePermissions(new File(instalace, "geokuk.jar.new").toPath()));
		Assert.assertEquals(bezna, Files.getPosixFilePermissions(new File(instalace, "start.jar").toPath()));
	}

	@Test
	public void nesouhlasPredPresunemNechaProgram() throws Exception {
		instaluj("geokuk.jar", "stary jar");
		instaluj("stazeny.part", "podvrzeny");
		final StahnoutAktualizaciSwingWorker.Stazeny stazeny = new StahnoutAktualizaciSwingWorker.Stazeny(new File(instalace, "stazeny.part").toPath(),
				StahnoutAktualizaciSwingWorker.soucet(new File(release, "geokuk.jar").toPath()));
		try {
			StahnoutAktualizaciSwingWorker.presun(stazeny, new File(instalace, "geokuk.jar"));
			Assert.fail();
		} catch (final IOException e) {
			Assert.assertEquals("stary jar", obsah("geokuk.jar"));
			Assert.assertFalse(new File(instalace, "stazeny.part").exists());
		}
	}

	/** Po přesunu se součet liší (disk, antivir): původní soubor se vrátí ze zálohy. */
	@Test
	public void nesouhlasPoPresunuVratiPuvodniSoubor() throws Exception {
		instaluj("geokuk.jar", "stary jar");
		instaluj("stazeny.part", "novy jar");
		final StahnoutAktualizaciSwingWorker.Stazeny stazeny = new StahnoutAktualizaciSwingWorker.Stazeny(new File(instalace, "stazeny.part").toPath(),
				StahnoutAktualizaciSwingWorker.soucet(new File(release, "geokuk.jar").toPath()));
		try {
			StahnoutAktualizaciSwingWorker.presun(stazeny, new File(instalace, "geokuk.jar"), new File(instalace, "geokuk.jar.bak"),
					(odkud, kam) -> Files.write(kam, "poskozeny".getBytes(StandardCharsets.US_ASCII)), true);
			Assert.fail();
		} catch (final IOException e) {
			Assert.assertEquals("stary jar", obsah("geokuk.jar"));
			Assert.assertEquals("stary jar", obsah("geokuk.jar.bak"));
		}
	}

	/** Při nesouhlasu u spouštěče se vrátí i původní start.jar a záloha se neuchovává. */
	@Test
	public void nesouhlasPoPresunuVratiStartJar() throws Exception {
		instaluj("start.jar", "stary start");
		instaluj("stazeny.part", "novy start");
		final StahnoutAktualizaciSwingWorker.Stazeny stazeny = new StahnoutAktualizaciSwingWorker.Stazeny(new File(instalace, "stazeny.part").toPath(),
				StahnoutAktualizaciSwingWorker.soucet(new File(release, "start.jar").toPath()));
		try {
			StahnoutAktualizaciSwingWorker.presun(stazeny, new File(instalace, "start.jar"), new File(instalace, "start.jar.bak"),
					(odkud, kam) -> Files.write(kam, "poskozeny".getBytes(StandardCharsets.US_ASCII)), false);
			Assert.fail();
		} catch (final IOException e) {
			Assert.assertEquals("stary start", obsah("start.jar"));
			Assert.assertFalse(new File(instalace, "start.jar.bak").exists());
		}
	}

	/** Nová verze, kterou už čeká geokuk.jar.new, se nestahuje znovu. */
	@Test
	public void uzStazenaVerzeSeNestahujeZnovu() throws Exception {
		instaluj("start.jar", "start");
		Assert.assertFalse(StahnoutAktualizaciSwingWorker.uzStazena(instalace, "9.9.9"));
		final java.util.jar.Manifest manifest = new java.util.jar.Manifest();
		manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().putValue("Geokuk-Version", "9.9.9");
		try (java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(new FileOutputStream(new File(instalace, "geokuk.jar.new")), manifest)) {
			out.flush();
		}
		Assert.assertTrue(StahnoutAktualizaciSwingWorker.uzStazena(instalace, "9.9.9"));
		Assert.assertFalse("jiná verze", StahnoutAktualizaciSwingWorker.uzStazena(instalace, "9.9.10"));
		instaluj("geokuk.jar.new", "neni jar");
		Assert.assertFalse("poškozený soubor", StahnoutAktualizaciSwingWorker.uzStazena(instalace, "9.9.9"));
	}

	@Test
	public void starePartSeUklidi() throws Exception {
		instaluj("geokuk.jar.123.part", "stary");
		instaluj("start.jar.456.part", "cerstvy");
		new File(instalace, "geokuk.jar.123.part").setLastModified(System.currentTimeMillis() - 2L * 60 * 60 * 1000);
		StahnoutAktualizaciSwingWorker.stahni(release.toURI().toString(), instalace);
		Assert.assertFalse(new File(instalace, "geokuk.jar.123.part").exists());
		Assert.assertTrue(new File(instalace, "start.jar.456.part").exists());
	}

	@Test
	public void zamekSeUvolniIPriChybe() throws Exception {
		StahnoutAktualizaciSwingWorker.skoncilo();
		Assert.assertTrue(StahnoutAktualizaciSwingWorker.zacni());
		final StahnoutAktualizaciSwingWorker w = new StahnoutAktualizaciSwingWorker("0") {
			@Override
			void ukazVysledek() {
				throw new IllegalStateException();
			}
		};
		try {
			w.donex();
			Assert.fail();
		} catch (final IllegalStateException e) {
			Assert.assertTrue(StahnoutAktualizaciSwingWorker.zacni());
			StahnoutAktualizaciSwingWorker.skoncilo();
		}
	}
}
