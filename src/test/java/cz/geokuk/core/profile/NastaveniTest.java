package cz.geokuk.core.profile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.prefs.Preferences;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.SouborovePreferences;

public class NastaveniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void zapsaneNastaveniSeNacte() throws Exception {
		final File soubor = new File(tmp.getRoot(), "data/nastaveni.xml");
		final SouborovePreferences koren = Nastaveni.otevri(soubor, null, false);
		final Preferences uzel = koren.node("geokuk/current/vseobecne");
		uzel.put("text", "Žluťoučký kůň <&> \"x\"");
		uzel.putInt("cislo", 42);
		koren.node("geokuk/current/prazdny");
		koren.flush();
		final SouborovePreferences nacteny = Nastaveni.otevri(soubor, null, false);
		Assert.assertEquals("Žluťoučký kůň <&> \"x\"", nacteny.node("geokuk/current/vseobecne").get("text", null));
		Assert.assertEquals(42, nacteny.node("geokuk/current/vseobecne").getInt("cislo", 0));
		Assert.assertTrue(nacteny.nodeExists("geokuk/current"));
	}

	@Test
	public void odstranenyUzelSeNeulozi() throws Exception {
		final File soubor = new File(tmp.getRoot(), "nastaveni.xml");
		final SouborovePreferences koren = Nastaveni.otevri(soubor, null, false);
		koren.node("geokuk/a").put("k", "v");
		koren.node("geokuk/a").removeNode();
		koren.flush();
		Assert.assertFalse(Nastaveni.otevri(soubor, null, false).nodeExists("geokuk/a"));
	}

	@Test
	public void prevezmeStaryExportVedleProgramu() throws Exception {
		final File stary = tmp.newFile("geokuk-preferences.xml");
		Files.write(stary.toPath(), ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
				+ "<!DOCTYPE preferences SYSTEM \"http://java.sun.com/dtd/preferences.dtd\">\n"
				+ "<preferences EXTERNAL_XML_VERSION=\"1.0\"><root type=\"user\"><map/><node name=\"geokuk\"><map><entry key=\"lastModified\" value=\"1\"/></map>"
				+ "<node name=\"current\"><map/><node name=\"vseobecne\"><map><entry key=\"nick\" value=\"Kačer\"/></map></node></node></node></root></preferences>")
						.getBytes(StandardCharsets.UTF_8));
		final File soubor = new File(tmp.getRoot(), "data/nastaveni.xml");
		final SouborovePreferences koren = Nastaveni.otevri(soubor, stary, false);
		Assert.assertEquals("Kačer", koren.node("geokuk/current/vseobecne").get("nick", null));
		Assert.assertTrue("převzaté nastavení se hned uloží", soubor.isFile());
	}

	@Test
	public void vadnySouborSeOdlozi() throws Exception {
		final File soubor = tmp.newFile("nastaveni.xml");
		Files.write(soubor.toPath(), "<preferences><root ".getBytes(StandardCharsets.UTF_8));
		final SouborovePreferences koren = Nastaveni.otevri(soubor, null, false);
		Assert.assertNotNull("uživatel se to musí dozvědět", Nastaveni.prevzitVarovani());
		Assert.assertTrue(new File(soubor.getPath() + ".vadne").isFile());
		Assert.assertEquals(0, koren.childrenNames().length);
	}

	@Test
	public void prazdnySouborPlatiJakoChybejiciBezVarovani() throws Exception {
		final File soubor = tmp.newFile("nastaveni.xml");
		final SouborovePreferences koren = Nastaveni.otevri(soubor, null, false);
		Assert.assertNull(Nastaveni.prevzitVarovani());
		Assert.assertFalse(new File(soubor.getPath() + ".vadne").exists());
		Assert.assertEquals(0, koren.childrenNames().length);
		Assert.assertTrue("nové nastavení se zapíše", soubor.length() > 0);
	}

	@Test
	public void docasneSouboryPoPaduSeUklidi() throws Exception {
		final File soubor = tmp.newFile("nastaveni.xml");
		final File stary = tmp.newFile("nastaveni.xml.123.tmp");
		final File cerstvy = tmp.newFile("nastaveni.xml.456.tmp");
		final File cizi = tmp.newFile("jiny.xml.789.tmp");
		final File vadne = tmp.newFile("nastaveni.xml.vadne");
		Assert.assertTrue(stary.setLastModified(System.currentTimeMillis() - 3_600_000));
		Assert.assertTrue(cizi.setLastModified(System.currentTimeMillis() - 3_600_000));
		Nastaveni.otevri(soubor, null, false);
		Assert.assertFalse(stary.exists());
		Assert.assertTrue("čerstvý může zapisovat jiný běh", cerstvy.exists());
		Assert.assertTrue(cizi.exists());
		Assert.assertTrue(vadne.exists());
	}

	@Test
	public void vadnySouborKteryNejdeOdlozitSeNeprepise() throws Exception {
		final File soubor = tmp.newFile("nastaveni.xml");
		final byte[] puvodni = "<preferences><root ".getBytes(StandardCharsets.UTF_8);
		Files.write(soubor.toPath(), puvodni);
		// Neprázdná složka .vadne: soubor se nepodaří přejmenovat.
		final File vadne = tmp.newFolder("nastaveni.xml.vadne");
		new File(vadne, "x").createNewFile();
		final SouborovePreferences koren = Nastaveni.otevri(soubor, null, false);
		final String varovani = Nastaveni.prevzitVarovani();
		Assert.assertTrue(varovani.contains("beze změny"));
		Assert.assertTrue("uživatel musí vědět, že se změny neuloží", varovani.contains("neuloží"));
		koren.node("geokuk").put("a", "1");
		koren.ulozHned();
		Assert.assertArrayEquals(puvodni, Files.readAllBytes(soubor.toPath()));
	}

	/** Nastavení verze 6.0.0: cesty míří do původní složky dat, převezme se jen to ostatní. */
	@Test
	public void cestyStarsiVerzeSeNeprevezmou() throws Exception {
		final File stary = tmp.newFile("geokuk-preferences.xml");
		Files.write(stary.toPath(), ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
				+ "<!DOCTYPE preferences SYSTEM \"http://java.sun.com/dtd/preferences.dtd\">\n"
				+ "<preferences EXTERNAL_XML_VERSION=\"1.0\"><root type=\"user\"><map/><node name=\"geokuk\"><map/><node name=\"current\"><map/>"
				+ "<node name=\"vseobecne\"><map><entry key=\"nick\" value=\"Kačer\"/></map></node>"
				+ "<node name=\"umisteniSouboru\"><map><entry key=\"kesDir\" value=\"C:\\Users\\Kacer\\geokuk\"/>"
				+ "<entry key=\"kesDir_active\" value=\"true\"/><entry key=\"kmzDir\" value=\"C:\\Users\\Kacer\\geokuk\\kmz\"/></map></node>"
				+ "<node name=\"vylet\"><map><entry key=\"aktualniSoubor\" value=\"C:\\Users\\Kacer\\geokuk\\cesty\\a.gpx\"/>"
				+ "<entry key=\"jeOtevrenyVylet\" value=\"true\"/></map></node>"
				+ "</node></node></root></preferences>").getBytes(StandardCharsets.UTF_8));
		final SouborovePreferences koren = Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), stary, false);
		Assert.assertEquals("Kačer", koren.node("geokuk/current/vseobecne").get("nick", null));
		Assert.assertFalse(koren.nodeExists("geokuk/current/umisteniSouboru"));
		Assert.assertNull(koren.node("geokuk/current/vylet").get("aktualniSoubor", null));
		Assert.assertTrue(koren.node("geokuk/current/vylet").getBoolean("jeOtevrenyVylet", false));
	}

	/** Stejně se převezme nastavení z registru. */
	@Test
	public void zRegistruSeCestyNeprevezmou() throws Exception {
		final SouborovePreferences registr = SouborovePreferences.prazdne(new File(tmp.getRoot(), "registr.xml"));
		registr.node("current/umisteniSouboru").put("kesDir", "C:\\Users\\Kacer\\geokuk");
		registr.node("current/vzhled").put("lookAndFeel", "Metal");
		final SouborovePreferences nove = SouborovePreferences.prazdne(new File(tmp.getRoot(), "nastaveni.xml"));
		Nastaveni.prevezmi(nove, registr);
		Assert.assertEquals("Metal", nove.node("geokuk/current/vzhled").get("lookAndFeel", null));
		Assert.assertFalse(nove.nodeExists("geokuk/current/umisteniSouboru"));
	}

	/** Soubor drží jiný program (antivirus): starší odložený soubor zůstane. */
	@Test
	public void drzenySouborNesmazeStarsiOdlozeny() throws Exception {
		Assume.assumeTrue("otevřený soubor nejde přejmenovat jen ve Windows", System.getProperty("os.name").startsWith("Windows"));
		final File soubor = tmp.newFile("nastaveni.xml");
		Files.write(soubor.toPath(), "<preferences><root ".getBytes(StandardCharsets.UTF_8));
		final File vadne = new File(soubor.getPath() + ".vadne");
		Files.write(vadne.toPath(), "starší".getBytes(StandardCharsets.UTF_8));
		try (java.io.FileInputStream drzi = new java.io.FileInputStream(soubor)) {
			Nastaveni.otevri(soubor, null, false);
		}
		Assert.assertTrue(Nastaveni.prevzitVarovani().contains("neuloží"));
		Assert.assertEquals("starší", new String(Files.readAllBytes(vadne.toPath()), StandardCharsets.UTF_8));
	}
}
