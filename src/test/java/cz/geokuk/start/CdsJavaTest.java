package cz.geokuk.start;

import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Ověření proti skutečné Javě: poškozený archiv se nesmí použít ani shodit start. */
public class CdsJavaTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File javaExe;
	private File archiv;
	private File classpath;
	private File logSlozka;

	@Before
	public void setUp() throws Exception {
		assumeTrue("Dynamický archiv umí Java od verze 19", Cds.jeJavaDostatecneNova(System.getProperty("java.specification.version")));
		assumeTrue("Java bez základního CDS archivu", Cds.maZakladniArchiv(new File(System.getProperty("java.home"))));
		final File bin = new File(System.getProperty("java.home"), "bin");
		javaExe = new File(bin, System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java");
		// Archiv se staví jen nad jary, složky v classpath Java odmítne.
		final List<String> jary = new ArrayList<>();
		for (final String c : System.getProperty("java.class.path").split(File.pathSeparator)) {
			final String jmeno = new File(c).getName();
			if (jmeno.startsWith("junit-") && jmeno.endsWith(".jar") || jmeno.startsWith("hamcrest-core") && jmeno.endsWith(".jar")) {
				jary.add(c);
			}
		}
		assumeTrue("Chybí jary JUnitu v classpath", jary.size() == 2);
		classpath = new File(String.join(File.pathSeparator, jary));
		logSlozka = new File(tmp.getRoot(), "log");
		archiv = new File(tmp.getRoot(), "archiv.jsa");
		final List<String> prikaz = new ArrayList<>();
		prikaz.add(javaExe.getPath());
		prikaz.add(Cds.KONTROLA);
		prikaz.add("-XX:ArchiveClassesAtExit=" + archiv.getPath());
		prikaz.add("-cp");
		prikaz.add(classpath.getPath());
		prikaz.add("org.junit.runner.JUnitCore");
		final Process p = new ProcessBuilder(prikaz).redirectErrorStream(true).redirectOutput(new File(tmp.getRoot(), "dump.log")).start();
		assumeTrue("Archiv se nevytvořil včas", p.waitFor(120, TimeUnit.SECONDS));
		assumeTrue("Archiv se nevytvořil", archiv.isFile() && archiv.length() > 100_000);
	}

	private boolean overit(final File a) {
		return Cds.overeniJavou(javaExe, classpath, tmp.getRoot(), logSlozka).overit(a);
	}

	@Test(timeout = 120000)
	public void dobryArchivProjde() {
		assertTrue(overit(archiv));
	}

	@Test(timeout = 120000)
	public void poOvereniNezustanouSouboryVLogu() {
		overit(archiv);
		final String[] zbytky = logSlozka.list();
		assertTrue("V logu zůstalo: " + java.util.Arrays.toString(zbytky), zbytky == null || zbytky.length == 0);
	}

	@Test(timeout = 120000)
	public void archivSNulamiNaKonciNeprojdeAleNespadne() throws IOException {
		final long delka = archiv.length();
		try (RandomAccessFile r = new RandomAccessFile(archiv, "rw")) {
			final int nuly = (int) (delka / 4);
			r.seek(delka - nuly);
			r.write(new byte[nuly]);
		}
		assertEquals(delka, archiv.length());
		assertFalse("Poškozený archiv nesmí projít ověřením", overit(archiv));
		final File[] hs = logSlozka.listFiles((d, n) -> n.startsWith("hs_err"));
		assertTrue("Java nesmí spadnout: " + (hs == null ? "" : java.util.Arrays.toString(hs)), hs == null || hs.length == 0);
	}

	@Test(timeout = 120000)
	public void oriznutyArchivNeprojde() throws IOException {
		try (RandomAccessFile r = new RandomAccessFile(archiv, "rw")) {
			r.setLength(r.length() / 2);
		}
		assertFalse(overit(archiv));
	}

	@Test(timeout = 120000)
	public void ciziSouborNeprojde() throws IOException {
		Files.write(archiv.toPath(), "ahoj".getBytes("UTF-8"));
		assertFalse(overit(archiv));
	}
}
