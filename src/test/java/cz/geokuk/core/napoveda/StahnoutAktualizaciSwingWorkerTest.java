package cz.geokuk.core.napoveda;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
		final File jar = new File(release, "geokuk.jar");
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(jar))) {
			zip.putNextEntry(new ZipEntry("geokuk.cmd"));
			zip.write("@echo off\r\n".getBytes(StandardCharsets.US_ASCII));
			zip.closeEntry();
		}
		final byte[] soucet = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar.toPath()));
		zapis("geokuk.jar.sha256", String.format("%064x", new BigInteger(1, soucet)) + "  geokuk.jar\n");
	}

	private void zapis(final String jmeno, final String obsah) throws IOException {
		Files.write(new File(release, jmeno).toPath(), obsah.getBytes(StandardCharsets.US_ASCII));
	}

	@Test
	public void stahneJarASpoustec() throws Exception {
		StahnoutAktualizaciSwingWorker.stahni(release.toURI().toString(), instalace);
		Assert.assertArrayEquals(Files.readAllBytes(new File(release, "geokuk.jar").toPath()), Files.readAllBytes(new File(instalace, "geokuk.jar.new").toPath()));
		Assert.assertEquals("@echo off\r\n", new String(Files.readAllBytes(new File(instalace, "geokuk.cmd").toPath()), StandardCharsets.US_ASCII));
		Assert.assertFalse(new File(instalace, "geokuk.jar.part").exists());
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
	public void vytvoriChybejiciSpoustec() throws Exception {
		Files.write(new File(instalace, "geokuk.jar").toPath(), new byte[0]);
		StahnoutAktualizaciSwingWorker.vytvorSpoustecPokudChybi(instalace);
		final String spoustec = new String(Files.readAllBytes(new File(instalace, "geokuk.cmd").toPath()), StandardCharsets.UTF_8);
		Assert.assertTrue(spoustec.contains("geokuk.jar.new"));
	}

	@Test
	public void existujiciSpoustecNeprepise() throws Exception {
		Files.write(new File(instalace, "geokuk.jar").toPath(), new byte[0]);
		Files.write(new File(instalace, "geokuk.cmd").toPath(), "vlastni".getBytes(StandardCharsets.US_ASCII));
		StahnoutAktualizaciSwingWorker.vytvorSpoustecPokudChybi(instalace);
		Assert.assertEquals("vlastni", new String(Files.readAllBytes(new File(instalace, "geokuk.cmd").toPath()), StandardCharsets.US_ASCII));
	}

	@Test
	public void bezGeokukJarNevytvoriSpoustec() throws Exception {
		StahnoutAktualizaciSwingWorker.vytvorSpoustecPokudChybi(instalace);
		Assert.assertFalse(new File(instalace, "geokuk.cmd").exists());
	}
}
