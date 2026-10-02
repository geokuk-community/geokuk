package cz.geokuk.start;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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
		zapis(new File(d, "geokuk.jar"), "stary");
		zapis(new File(d, "geokuk.jar.new"), "novy");
		Start.vymenJar(d);
		Assert.assertEquals("novy", cti(new File(d, "geokuk.jar")));
		Assert.assertEquals("stary", cti(new File(d, "geokuk.jar.bak")));
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
	public void pametPolovinaRamMezi1A3Gb() {
		final File nic = new File(tmp.getRoot(), "neni.xml");
		Assert.assertEquals(1024, Start.pametMb(nic, 1024));
		Assert.assertEquals(2048, Start.pametMb(nic, 4096));
		Assert.assertEquals(3072, Start.pametMb(nic, 32768));
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
}
