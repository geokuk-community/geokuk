package cz.geokuk.core.profile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.function.Supplier;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.SouborovePreferences;

public class NastaveniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Before
	@After
	public void zahodVarovani() {
		Nastaveni.prevzitVarovani();
	}

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
		Assert.assertEquals("soubor", koren.node(Nastaveni.ZNACKA_PREVODU).get("zdroj", null));
		Assert.assertEquals("ok", koren.node(Nastaveni.ZNACKA_PREVODU).get("vysledek", null));
		Assert.assertNull(Nastaveni.prevzitVarovani());
	}

	/** Starší nastavení nejde přečíst: uživatel dostane varování a značka zabrání opakování stejného selhání. */
	@Test
	public void nectitelnyStarySouborVarujeAZapiseZnacku() throws Exception {
		final File stary = tmp.newFile("geokuk-preferences.xml");
		Files.write(stary.toPath(), "<preferences><root ".getBytes(StandardCharsets.UTF_8));
		final File soubor = new File(tmp.getRoot(), "data/nastaveni.xml");
		Nastaveni.otevri(soubor, stary, false);
		Assert.assertEquals(Nastaveni.VAROVANI_PREVODU, Nastaveni.prevzitVarovani());
		final SouborovePreferences ulozene = SouborovePreferences.nacti(soubor);
		Assert.assertEquals("chyba", ulozene.node(Nastaveni.ZNACKA_PREVODU).get("vysledek", null));
		Nastaveni.otevri(soubor, stary, false);
		Assert.assertNull("podruhé se převod nezkouší", Nastaveni.prevzitVarovani());
	}

	/** Chyba uprostřed čtení registru: částečně převzaté nastavení se neuloží. */
	@Test
	public void chybaUprostredRegistruNeuloziCastecnyStav() throws Exception {
		final File soubor = new File(tmp.getRoot(), "data/nastaveni.xml");
		final SouborovePreferences koren = Nastaveni.otevri(soubor, null, PadajiciPreferences::new, null);
		Assert.assertEquals(Nastaveni.VAROVANI_PREVODU, Nastaveni.prevzitVarovani());
		final SouborovePreferences ulozene = SouborovePreferences.nacti(soubor);
		Assert.assertNull("nic z částečně přečteného zdroje", ulozene.node("geokuk").get("lastModified", null));
		Assert.assertFalse(ulozene.nodeExists("geokuk/current"));
		Assert.assertEquals("chyba", ulozene.node(Nastaveni.ZNACKA_PREVODU).get("vysledek", null));
		Assert.assertEquals("zadny", koren.node(Nastaveni.ZNACKA_PREVODU).get("zdroj", null));
	}

	/** Zahozený rozpracovaný převod nesmí odloženým zápisem přepsat uložené výchozí nastavení. */
	@Test
	public void chybaUprostredRegistruNeprepiseSouborOdlozenymZapisem() throws Exception {
		final File soubor = new File(tmp.getRoot(), "data/nastaveni.xml");
		Nastaveni.otevri(soubor, null, PadajiciPreferences::new, null);
		Assert.assertEquals(Nastaveni.VAROVANI_PREVODU, Nastaveni.prevzitVarovani());
		pockejNaOdlozeneZapisy();
		final SouborovePreferences ulozene = SouborovePreferences.nacti(soubor);
		Assert.assertNull("nic z částečně přečteného zdroje", ulozene.node("geokuk").get("lastModified", null));
		Assert.assertEquals("chyba", ulozene.node(Nastaveni.ZNACKA_PREVODU).get("vysledek", null));
	}

	/** Java Preferences se otevřou, jen když se z nich opravdu převádí; ve Windows by jinak vznikl klíč v registru. */
	@Test
	public void registrSeBezPrevoduNeotevre() throws Exception {
		final int[] otevreno = { 0 };
		final Supplier<Preferences> registr = () -> {
			otevreno[0]++;
			return SouborovePreferences.vPameti();
		};
		final File soubor = new File(tmp.getRoot(), "data/nastaveni.xml");
		Nastaveni.otevri(soubor, null, false);
		Nastaveni.otevri(soubor, null, registr, null);
		Assert.assertEquals("nastavení existuje", 0, otevreno[0]);

		final File stary = tmp.newFile("geokuk-preferences.xml");
		Files.write(stary.toPath(), ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
				+ "<preferences EXTERNAL_XML_VERSION=\"1.0\"><root type=\"user\"><map/><node name=\"geokuk\"><map><entry key=\"lastModified\" value=\"1\"/></map>"
				+ "</node></root></preferences>").getBytes(StandardCharsets.UTF_8));
		Nastaveni.otevri(new File(tmp.getRoot(), "b/nastaveni.xml"), stary, registr, null);
		Assert.assertEquals("převzato ze souboru", 0, otevreno[0]);

		Nastaveni.otevri(new File(tmp.getRoot(), "c/nastaveni.xml"), null, registr, null);
		Assert.assertEquals("bez souboru se převádí z registru", 1, otevreno[0]);
	}

	private static void pockejNaOdlozeneZapisy() throws InterruptedException {
		final long konec = System.currentTimeMillis() + 15_000;
		while (SouborovePreferences.naplanovanychZapisu() > 0) {
			Assert.assertTrue("odložené zápisy nastavení neproběhly", System.currentTimeMillis() < konec);
			Thread.sleep(50);
		}
	}

	/** Úspěšný převod z registru zapíše značku bez cest. */
	@Test
	public void prevodZRegistruZapiseZnacku() throws Exception {
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("geokuk/current/vzhled").put("lookAndFeel", "Metal");
		final SouborovePreferences koren = Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), null, () -> registr, null);
		Assert.assertEquals("Metal", koren.node("geokuk/current/vzhled").get("lookAndFeel", null));
		final Preferences znacka = koren.node(Nastaveni.ZNACKA_PREVODU);
		Assert.assertEquals("registr", znacka.get("zdroj", null));
		Assert.assertEquals("ok", znacka.get("vysledek", null));
		Assert.assertEquals(1, znacka.getInt("verze", 0));
		Assert.assertNull(Nastaveni.prevzitVarovani());
	}

	/** Bez staršího nastavení se značka nezapisuje. */
	@Test
	public void bezStarsihoNastaveniNeniZnacka() throws Exception {
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		final SouborovePreferences koren = Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), null, () -> registr, null);
		Assert.assertFalse(koren.nodeExists(Nastaveni.ZNACKA_PREVODU));
		Assert.assertNull(Nastaveni.prevzitVarovani());
	}

	/** Dlouhá hodnota rozdělená do pokračovacích klíčů a zapsaný null se převezmou beze změny. */
	@Test
	public void pokracovaciKliceANullSePrevezmou() throws Exception {
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		final Preferences stare = registr.node("geokuk/current/kesoid");
		stare.put("blokovaneZdroje", "a");
		stare.put("blokovaneZdroje;cont0", "b");
		stare.put("blokovaneZdroje;cont1", "c");
		stare.put("nic", "<{<[NULL]>}>");
		final Preferences nove = Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), null, () -> registr, null).node("geokuk/current/kesoid");
		Assert.assertEquals("a", nove.get("blokovaneZdroje", null));
		Assert.assertEquals("b", nove.get("blokovaneZdroje;cont0", null));
		Assert.assertEquals("c", nove.get("blokovaneZdroje;cont1", null));
		Assert.assertEquals("<{<[NULL]>}>", nove.get("nic", null));
	}

	/** Převod zdroj jen čte: soubor ani strom registru se nezmění, nevznikne v nich nový uzel. */
	@Test
	public void zdrojPrevoduZustaneBezeZmeny() throws Exception {
		final File stary = tmp.newFile("geokuk-preferences.xml");
		final byte[] obsahStareho = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
				+ "<!DOCTYPE preferences SYSTEM \"http://java.sun.com/dtd/preferences.dtd\">\n"
				+ "<preferences EXTERNAL_XML_VERSION=\"1.0\"><root type=\"user\"><map/><node name=\"geokuk\"><map/>"
				+ "<node name=\"current\"><map/><node name=\"umisteniSouboru\"><map><entry key=\"kesDir\" value=\"C:\\Users\\Kacer\\geokuk\"/></map></node>"
				+ "</node></node></root></preferences>").getBytes(StandardCharsets.UTF_8);
		Files.write(stary.toPath(), obsahStareho);
		Assert.assertTrue(stary.setLastModified(1_000_000_000_000L));
		Nastaveni.otevri(new File(tmp.getRoot(), "a/nastaveni.xml"), stary, false);
		Assert.assertArrayEquals(obsahStareho, Files.readAllBytes(stary.toPath()));
		Assert.assertEquals(1_000_000_000_000L, stary.lastModified());

		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("geokuk/current/umisteniSouboru").put("kesDir", "C:\\Users\\Kacer\\geokuk");
		registr.node("geokuk/current/vylet").put("aktualniSoubor", "a.gpx");
		final String pred = export(registr);
		Nastaveni.otevri(new File(tmp.getRoot(), "b/nastaveni.xml"), null, () -> registr, null);
		Assert.assertEquals(pred, export(registr));
	}

	private static String export(final Preferences p) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		p.exportSubtree(out);
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}

	/** Registr, jehož čtení selže uprostřed podstromu geokuk. */
	private static final class PadajiciPreferences extends AbstractPreferences {
		private final int hloubka;

		PadajiciPreferences() {
			this(null, "", 0);
		}

		private PadajiciPreferences(final PadajiciPreferences rodic, final String jmeno, final int hloubka) {
			super(rodic, jmeno);
			this.hloubka = hloubka;
		}

		@Override
		protected String[] keysSpi() throws BackingStoreException {
			if (hloubka == 2) {
				throw new BackingStoreException("čtení selhalo");
			}
			return hloubka == 1 ? new String[] { "lastModified" } : new String[0];
		}

		@Override
		protected String getSpi(final String klic) {
			return "1";
		}

		@Override
		protected String[] childrenNamesSpi() {
			return hloubka == 0 ? new String[] { "geokuk" } : hloubka == 1 ? new String[] { "current" } : new String[0];
		}

		@Override
		protected AbstractPreferences childSpi(final String jmeno) {
			return new PadajiciPreferences(this, jmeno, hloubka + 1);
		}

		@Override
		protected void putSpi(final String klic, final String hodnota) {
			throw new UnsupportedOperationException("zdroj se jen čte");
		}

		@Override
		protected void removeSpi(final String klic) {
			throw new UnsupportedOperationException("zdroj se jen čte");
		}

		@Override
		protected void removeNodeSpi() {
			throw new UnsupportedOperationException("zdroj se jen čte");
		}

		@Override
		protected void syncSpi() {}

		@Override
		protected void flushSpi() {}
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
		Files.write(new File(vadne, "x").toPath(), new byte[0]);
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

	/** Stejně se převezme nastavení z registru (uzel geokuk). */
	@Test
	public void zRegistruSeCestyNeprevezmou() throws Exception {
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("current/umisteniSouboru").put("kesDir", "C:\\Users\\Kacer\\geokuk");
		registr.node("current/vzhled").put("lookAndFeel", "Metal");
		final SouborovePreferences nove = SouborovePreferences.prazdne(new File(tmp.getRoot(), "nastaveni.xml"));
		Nastaveni.prevezmi(nove, registr, bezVychozichVyletu());
		Assert.assertEquals("Metal", nove.node("geokuk/current/vzhled").get("lookAndFeel", null));
		Assert.assertFalse(nove.nodeExists("geokuk/current/umisteniSouboru"));
	}

	/** Složky GeoGetu a GSAKu patří jiným programům, převezmou se i se zapnutím; ostatní cesty ne. */
	@Test
	public void slozkyGeogetuAGsakuSePrevezmou() throws Exception {
		final String geoget = soubor("geoget/data/geoget.db3", "x").getParent();
		final String gsak = soubor("gsak/data/Default/sqlite.db3", "x").getParentFile().getParent();
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		final Preferences stare = registr.node("current/umisteniSouboru");
		stare.put("geogetDataDir", geoget);
		stare.putBoolean("geogetDataDir_active", true);
		stare.putBoolean("geogetDataDir_relativeToProgram", false);
		stare.put("gsakDataDir", gsak);
		stare.putBoolean("gsakDataDir_active", true);
		stare.put("kesDir", tmp.getRoot().getAbsolutePath());
		stare.putBoolean("kesDir_active", true);
		final SouborovePreferences nove = SouborovePreferences.prazdne(new File(tmp.getRoot(), "data/nastaveni.xml"));
		Nastaveni.prevezmi(nove, registr, bezVychozichVyletu());
		final Preferences umisteni = nove.node("geokuk/current/umisteniSouboru");
		Assert.assertEquals(geoget, umisteni.get("geogetDataDir", null));
		Assert.assertTrue(umisteni.getBoolean("geogetDataDir_active", false));
		Assert.assertEquals(gsak, umisteni.get("gsakDataDir", null));
		Assert.assertTrue(umisteni.getBoolean("gsakDataDir_active", false));
		Assert.assertNull("cesty do původní složky dat se nepřebírají", umisteni.get("kesDir", null));
		Assert.assertNull(umisteni.get("kesDir_active", null));
	}

	/** Starší verze měla zapnutou výchozí složku GSAKu pro Windows i tam, kde GSAK není; nepřebírá se, složku najde detekce zdrojů. */
	@Test
	public void neexistujiciSlozkaSeNeprevezme() throws Exception {
		final String gsak = new File(tmp.getRoot(), "AppData/Roaming/gsak/data").getAbsolutePath();
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("current/umisteniSouboru").put("gsakDataDir", gsak);
		registr.node("current/umisteniSouboru").putBoolean("gsakDataDir_active", true);
		final SouborovePreferences nove = SouborovePreferences.prazdne(new File(tmp.getRoot(), "data/nastaveni.xml"));
		Nastaveni.prevezmi(nove, registr, bezVychozichVyletu());
		Assert.assertFalse(nove.nodeExists("geokuk/current/umisteniSouboru"));
	}

	/** Starší verze výchozí složku GSAKu sama vytvořila prázdnou; nepřebírá se. */
	@Test
	public void prazdnaSlozkaSeNeprevezme() throws Exception {
		final String gsak = tmp.newFolder("AppData", "Roaming", "gsak", "data").getAbsolutePath();
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("current/umisteniSouboru").put("gsakDataDir", gsak);
		registr.node("current/umisteniSouboru").putBoolean("gsakDataDir_active", true);
		final SouborovePreferences nove = SouborovePreferences.prazdne(new File(tmp.getRoot(), "data/nastaveni.xml"));
		Nastaveni.prevezmi(nove, registr, bezVychozichVyletu());
		Assert.assertFalse(nove.nodeExists("geokuk/current/umisteniSouboru"));
	}

	/** Relativní cesta se ve starší verzi počítala od jiné složky programu. */
	@Test
	public void relativniSlozkaGeogetuSeNeprevezme() throws Exception {
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("current/umisteniSouboru").put("geogetDataDir", "geoget/data");
		registr.node("current/umisteniSouboru").putBoolean("geogetDataDir_active", true);
		final SouborovePreferences nove = SouborovePreferences.prazdne(new File(tmp.getRoot(), "data/nastaveni.xml"));
		Nastaveni.prevezmi(nove, registr, bezVychozichVyletu());
		Assert.assertFalse(nove.nodeExists("geokuk/current/umisteniSouboru"));
	}

	/** Starší verze použila absolutní cestu i se zaškrtnutým „relativně k programu“. */
	@Test
	public void absolutniSlozkaSPriznakemRelativnePrevezme() throws Exception {
		final String geoget = soubor("geoget/data/geoget.db3", "x").getParent();
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("current/umisteniSouboru").put("geogetDataDir", geoget);
		registr.node("current/umisteniSouboru").putBoolean("geogetDataDir_active", true);
		registr.node("current/umisteniSouboru").putBoolean("geogetDataDir_relativeToProgram", true);
		final SouborovePreferences nove = SouborovePreferences.prazdne(new File(tmp.getRoot(), "data/nastaveni.xml"));
		Nastaveni.prevezmi(nove, registr, bezVychozichVyletu());
		Assert.assertEquals(geoget, nove.node("geokuk/current/umisteniSouboru").get("geogetDataDir", null));
		Assert.assertTrue(nove.node("geokuk/current/umisteniSouboru").getBoolean("geogetDataDir_active", false));
	}

	/** Síťová složka se při startu nezkoumá (odpojený disk by start zdržel); převezme se zapnutá, jak byla. */
	@Test
	public void sitovaSlozkaSeNezkoumaAPrevezmeSeJakByla() throws Exception {
		final String sitova = File.separatorChar == '\\' ? "\\\\server\\geoget\\data" : "//server/geoget/data";
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("current/umisteniSouboru").put("geogetDataDir", sitova);
		registr.node("current/umisteniSouboru").putBoolean("geogetDataDir_active", true);
		final SouborovePreferences nove = SouborovePreferences.prazdne(new File(tmp.getRoot(), "data/nastaveni.xml"));
		Nastaveni.prevezmi(nove, registr, bezVychozichVyletu());
		Assert.assertEquals(sitova, nove.node("geokuk/current/umisteniSouboru").get("geogetDataDir", null));
		Assert.assertTrue(nove.node("geokuk/current/umisteniSouboru").getBoolean("geogetDataDir_active", false));
	}

	/** Souhrn převodu ve značce jmenuje převzaté složky a výlety, bez cest. */
	@Test
	public void souhrnPrevoduVeZnacce() throws Exception {
		final String geoget = soubor("geoget/data/geoget.db3", "x").getParent();
		final File lovim = soubor("stara/lovim.ggt", "GC12345\n");
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		final Preferences stare = registr.node("geokuk/current/umisteniSouboru");
		stare.put("geogetDataDir", geoget);
		stare.put("gsakDataDir", new File(tmp.getRoot(), "gsak").getAbsolutePath());
		stare.put("anoGgtFile", lovim.getAbsolutePath());
		final Preferences znacka = Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), null, () -> registr, null).node(Nastaveni.ZNACKA_PREVODU);
		Assert.assertEquals("GeoGet; výlet lovim.ggt", znacka.get("prevzato", null));
		Assert.assertEquals("GSAK (složka neexistuje nebo je prázdná)", znacka.get("neprevzato", null));
	}

	/** Bez uzlu umístění ve zdroji v něm převod žádný uzel nezaloží; ve Windows by vznikl klíč v registru. */
	@Test
	public void registrBezUmisteniZustaneBezUzluUmisteni() throws Exception {
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("geokuk/current/vzhled").put("lookAndFeel", "Metal");
		Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), null, () -> registr, tmp.newFolder("domov"));
		Assert.assertFalse(registr.nodeExists("geokuk/current/umisteniSouboru"));
	}

	/** Souhrn jmenuje jen složky, které ve starším nastavení byly. */
	@Test
	public void souhrnJmenujeJenNastaveneSlozky() throws Exception {
		final String gsak = soubor("gsak/data/Default/sqlite.db3", "x").getParentFile().getParent();
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("geokuk/current/umisteniSouboru").put("gsakDataDir", gsak);
		final Preferences znacka = Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), null, () -> registr, null).node(Nastaveni.ZNACKA_PREVODU);
		Assert.assertEquals("GSAK", znacka.get("prevzato", null));
	}

	/** Výlet na síťové cestě se při startu nezkoumá ani nekopíruje, souhrn to uvede. */
	@Test
	public void vyletNaSitiSeNeprevezme() throws Exception {
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("geokuk/current/umisteniSouboru").put("anoGgtFile", "\\\\server\\geokuk\\lovim.ggt");
		registr.node("geokuk/current/umisteniSouboru").put("neGgtFile", "//server/geokuk/tedne.ggt");
		final Nastaveni.Kopie puvodni = Nastaveni.kopie;
		try {
			Nastaveni.kopie = (zdroj, out) -> {
				throw new AssertionError("síťový výlet se nekopíruje: " + zdroj);
			};
			final Preferences znacka = Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), null, () -> registr, null).node(Nastaveni.ZNACKA_PREVODU);
			Assert.assertEquals("výlet lovim.ggt (síťová cesta); výlet tedne.ggt (síťová cesta)", znacka.get("neprevzato", null));
		} finally {
			Nastaveni.kopie = puvodni;
		}
	}

	/** Výlety se kopírují ze zdroje, ze kterého se převzalo nastavení, a až po jeho úspěšném převzetí. */
	@Test
	public void vyletyZeStejnehoZdrojeJakoNastaveni() throws Exception {
		final File zeSouboru = soubor("soubor/lovim.ggt", "GCSOUBOR\n");
		final File zRegistru = soubor("registr/lovim.ggt", "GCREGISTR\n");
		final File stary = tmp.newFile("geokuk-preferences.xml");
		Files.write(stary.toPath(), ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
				+ "<preferences EXTERNAL_XML_VERSION=\"1.0\"><root type=\"user\"><map/><node name=\"geokuk\"><map/><node name=\"current\"><map/>"
				+ "<node name=\"umisteniSouboru\"><map><entry key=\"anoGgtFile\" value=\"" + zeSouboru.getAbsolutePath() + "\"/></map></node>"
				+ "</node></node></root></preferences>").getBytes(StandardCharsets.UTF_8));
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("geokuk/current/umisteniSouboru").put("anoGgtFile", zRegistru.getAbsolutePath());
		Nastaveni.otevri(new File(tmp.getRoot(), "a/nastaveni.xml"), stary, () -> registr, null);
		Assert.assertEquals("GCSOUBOR\n", obsah(new File(tmp.getRoot(), "a/vylety/lovim.ggt")));

		Files.write(stary.toPath(), "<preferences><root ".getBytes(StandardCharsets.UTF_8));
		final Preferences znacka = Nastaveni.otevri(new File(tmp.getRoot(), "b/nastaveni.xml"), stary, () -> registr, null).node(Nastaveni.ZNACKA_PREVODU);
		Assert.assertEquals("registr", znacka.get("zdroj", null));
		Assert.assertEquals("GCREGISTR\n", obsah(new File(tmp.getRoot(), "b/vylety/lovim.ggt")));
		Nastaveni.prevzitVarovani();

		Nastaveni.otevri(new File(tmp.getRoot(), "c/nastaveni.xml"), null, PadajiciPreferences::new, null);
		Assert.assertFalse("bez převzatého nastavení se výlety nekopírují", new File(tmp.getRoot(), "c/vylety").exists());
	}

	/** Kopie výletu přerušená uprostřed nenechá useknutý soubor; příští převod ho zkopíruje celý. */
	@Test
	public void prerusenaKopieVyletuNenechaUseknutySoubor() throws Exception {
		final File lovim = soubor("stara/lovim.ggt", "GC12345\nGC6789\n");
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("geokuk/current/umisteniSouboru").put("anoGgtFile", lovim.getAbsolutePath());
		final File cil = new File(tmp.getRoot(), "data/vylety/lovim.ggt");
		final Nastaveni.Kopie puvodni = Nastaveni.kopie;
		try {
			Nastaveni.kopie = (zdroj, out) -> {
				out.write("GC123".getBytes(StandardCharsets.US_ASCII));
				out.flush();
				throw new java.io.IOException("disk odpojen");
			};
			final Preferences znacka = Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), null, () -> registr, null).node(Nastaveni.ZNACKA_PREVODU);
			Assert.assertFalse("useknutý výlet by příští převod nepřepsal", cil.exists());
			Assert.assertEquals("výlet lovim.ggt", znacka.get("neprevzato", null));
		} finally {
			Nastaveni.kopie = puvodni;
		}
		Files.delete(new File(tmp.getRoot(), "data/nastaveni.xml").toPath());
		Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), null, () -> registr, null);
		Assert.assertEquals("GC12345\nGC6789\n", obsah(cil));
	}

	/** Výlety z nastaveného umístění se zkopírují do složky výletů, původní soubory zůstanou. */
	@Test
	public void vyletySeZkopirujiZNastavenehoUmisteni() throws Exception {
		final File lovim = soubor("stara/lovim.ggt", "GC12345\nGC6789\n");
		final File tedne = soubor("stara/tedne.ggt", "GCABCDE\n");
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("geokuk/current/umisteniSouboru").put("anoGgtFile", lovim.getAbsolutePath());
		registr.node("geokuk/current/umisteniSouboru").put("neGgtFile", tedne.getAbsolutePath());
		final File vylety = new File(tmp.getRoot(), "data/vylety");
		Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), null, () -> registr, null);
		Assert.assertEquals("GC12345\nGC6789\n", obsah(new File(vylety, "lovim.ggt")));
		Assert.assertEquals("GCABCDE\n", obsah(new File(vylety, "tedne.ggt")));
		Assert.assertTrue("původní soubor může dál používat starší verze nebo GeoGet", lovim.isFile());
	}

	/** Bez klíče platí výchozí umístění starší verze, složka geokuk v domovské složce. */
	@Test
	public void vyletySeZkopirujiZVychozihoUmisteniStarsiVerze() throws Exception {
		final File domov = tmp.newFolder("domov");
		soubor("domov/geokuk/lovim.ggt", "GC11111\n");
		final File stary = tmp.newFile("geokuk-preferences.xml");
		Files.write(stary.toPath(), ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
				+ "<!DOCTYPE preferences SYSTEM \"http://java.sun.com/dtd/preferences.dtd\">\n"
				+ "<preferences EXTERNAL_XML_VERSION=\"1.0\"><root type=\"user\"><map/><node name=\"geokuk\"><map/><node name=\"current\"><map/>"
				+ "<node name=\"vseobecne\"><map><entry key=\"nick\" value=\"Kačer\"/></map></node>"
				+ "</node></node></root></preferences>").getBytes(StandardCharsets.UTF_8));
		// Záložní cesta pro nastavení bez uzlu umisteniSouboru; starší verze ho při startu sama zapisuje (viz test níže).
		Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), stary, false, domov);
		Assert.assertEquals("GC11111\n", obsah(new File(tmp.getRoot(), "data/vylety/lovim.ggt")));
		Assert.assertFalse("chybějící soubor se nevytváří", new File(tmp.getRoot(), "data/vylety/tedne.ggt").exists());
	}

	/** Stav po prvním spuštění starší verze: uzel umístění je plný výchozích hodnot, výlety ve složce geokuk v domovské složce. */
	@Test
	public void vyletySeZkopirujiZVychozihoNastaveniStarsiVerze() throws Exception {
		final File lovim = soubor("domov/geokuk/lovim.ggt", "GC11111\n");
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		final Preferences stare = registr.node("geokuk/current/umisteniSouboru");
		stare.put("anoGgtFile", lovim.getAbsolutePath());
		stare.putBoolean("anoGgtFile_active", true);
		stare.putBoolean("anoGgtFile_relativeToProgram", false);
		stare.put("neGgtFile", new File(lovim.getParentFile(), "tedne.ggt").getAbsolutePath());
		stare.putBoolean("neGgtFile_active", true);
		stare.putBoolean("neGgtFile_relativeToProgram", false);
		stare.put("kesDir", lovim.getParent());
		stare.putBoolean("kesDir_active", true);
		final File vylety = new File(tmp.getRoot(), "data/vylety");
		final SouborovePreferences nove = Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), null, () -> registr, null);
		Assert.assertEquals("GC11111\n", obsah(new File(vylety, "lovim.ggt")));
		Assert.assertFalse(new File(vylety, "tedne.ggt").exists());
		Assert.assertFalse(nove.nodeExists("geokuk/current/umisteniSouboru"));
	}

	/** Výlet, který už ve složce výletů je, se nepřepíše; vypnutý se nepřebírá. */
	@Test
	public void existujiciVyletSeNeprepiseAVypnutySeNeprevezme() throws Exception {
		final File vylety = new File(tmp.getRoot(), "data/vylety");
		final File existujici = soubor("data/vylety/lovim.ggt", "GCNOVY\n");
		final SouborovePreferences registr = SouborovePreferences.vPameti();
		registr.node("geokuk/current/umisteniSouboru").put("anoGgtFile", soubor("stara/lovim.ggt", "GCSTARY\n").getAbsolutePath());
		registr.node("geokuk/current/umisteniSouboru").put("neGgtFile", soubor("stara/tedne.ggt", "GCX\n").getAbsolutePath());
		registr.node("geokuk/current/umisteniSouboru").putBoolean("neGgtFile_active", false);
		Nastaveni.otevri(new File(tmp.getRoot(), "data/nastaveni.xml"), null, () -> registr, null);
		Assert.assertEquals("GCNOVY\n", obsah(existujici));
		Assert.assertFalse(new File(vylety, "tedne.ggt").exists());
	}

	private Nastaveni.Prevod bezVychozichVyletu() {
		return new Nastaveni.Prevod(new File(tmp.getRoot(), "data/vylety"), null);
	}

	private File soubor(final String cesta, final String text) throws Exception {
		final File f = new File(tmp.getRoot(), cesta);
		Files.createDirectories(f.getParentFile().toPath());
		Files.write(f.toPath(), text.getBytes(StandardCharsets.US_ASCII));
		return f;
	}

	private static String obsah(final File f) throws Exception {
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.US_ASCII);
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
