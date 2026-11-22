package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.util.file.Filex;

/** Hledání datových složek jiných programů v prostředí poskládaném v dočasné složce; nepotřebuje Windows. */
public class DetekceZdrojuTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File domov;
	private final Map<String, String> promenne = new HashMap<>();

	@Before
	public void priprav() throws Exception {
		domov = tmp.newFolder("domov");
		promenne.put("APPDATA", new File(domov, "AppData/Roaming").getPath());
		promenne.put("ProgramFiles", new File(tmp.getRoot(), "pf").getPath());
		promenne.put("ProgramFiles(x86)", new File(tmp.getRoot(), "pf86").getPath());
		promenne.put("SystemDrive", new File(tmp.getRoot(), "c").getPath());
	}

	private DetekceZdroju.Prostredi windows() {
		return new DetekceZdroju.Prostredi("Windows 11", domov, promenne);
	}

	private File soubor(final String cesta, final String obsah) throws Exception {
		final File f = new File(tmp.getRoot(), cesta);
		Files.createDirectories(f.getParentFile().toPath());
		Files.write(f.toPath(), obsah.getBytes(StandardCharsets.UTF_8));
		return f;
	}

	private static File kanon(final File f) {
		return Filex.canonize(f.getAbsoluteFile());
	}

	// ---------------------------------------------------------------- GeoGet

	@Test
	public void geogetZInstalatoruVAppData() throws Exception {
		final File db = soubor("domov/AppData/Roaming/GeoGet/geoget.db3", "x");
		soubor("domov/AppData/Roaming/GeoGet/Vylet.db3", "x");
		soubor("domov/AppData/Roaming/GeoGet/geoget.ini", "[geoget]\r\nlastDB=geoget\r\nlastDBmode=1\r\n");
		final DetekceZdroju.Nalez n = DetekceZdroju.geoget(windows());
		Assert.assertEquals(kanon(db.getParentFile()), n.getSlozka());
		Assert.assertEquals(kanon(db), n.getPosledniDatabaze());
	}

	@Test
	public void geogetbootIniMaPrednostPredAppData() throws Exception {
		soubor("domov/AppData/Roaming/GeoGet/geoget.db3", "x");
		final File data = new File(tmp.getRoot(), "jinde/data");
		soubor("jinde/data/geoget.db3", "x");
		soubor("pf86/GeoGet/geogetboot.ini", "[boot]\r\ndatadir=" + data.getAbsolutePath() + "\r\n");
		Assert.assertEquals(kanon(data), DetekceZdroju.geoget(windows()).getSlozka());
	}

	@Test
	public void relativniDatadirJeVuciSlozceProgramu() throws Exception {
		soubor("c/GeoGet/geogetboot.ini", "[boot]\r\ndatadir=.\\moje-data\r\n");
		final File db = soubor("c/GeoGet/moje-data/geoget.db3", "x");
		Assert.assertEquals(kanon(db.getParentFile()), DetekceZdroju.geoget(windows()).getSlozka());
	}

	@Test
	public void prenosnaInstalaceMaDataVeSlozceProgramu() throws Exception {
		final File db = soubor("c/GeoGet/data/geoget.db3", "x");
		final DetekceZdroju.Nalez n = DetekceZdroju.geoget(windows());
		Assert.assertEquals(kanon(db.getParentFile()), n.getSlozka());
		Assert.assertNull("bez geoget.ini poslední databáze není", n.getPosledniDatabaze());
	}

	@Test
	public void slozkaJenSeZalohamiNeniDatovaSlozka() throws Exception {
		soubor("domov/AppData/Roaming/GeoGet/geoget.20260101.db3", "x");
		soubor("domov/AppData/Roaming/GeoGet/geoget.ini", "[geoget]\r\nlastDB=geoget\r\n");
		Assert.assertNull(DetekceZdroju.geoget(windows()));
	}

	@Test
	public void neexistujiciPosledniDatabazeNeniVybrana() throws Exception {
		soubor("domov/AppData/Roaming/GeoGet/geoget.db3", "x");
		soubor("domov/AppData/Roaming/GeoGet/geoget.ini", "[geoget]\r\nlastDB=smazana\r\nlastDBmode=1\r\n");
		final DetekceZdroju.Nalez n = DetekceZdroju.geoget(windows());
		Assert.assertNotNull(n);
		Assert.assertNull(n.getPosledniDatabaze());
	}

	@Test
	public void posledniDatabazeSDiakritikou() throws Exception {
		org.junit.Assume.assumeTrue("souborový systém umí jména s diakritikou", java.nio.charset.Charset.forName(System.getProperty("sun.jnu.encoding", "UTF-8")).newEncoder().canEncode("Výlet"));
		final File db = soubor("domov/AppData/Roaming/GeoGet/Výlet.db3", "x");
		soubor("domov/AppData/Roaming/GeoGet/geoget.ini", "\uFEFF[geoget]\r\nlastDB=Výlet\r\nlastDBmode=1\r\n");
		Assert.assertEquals(kanon(db), DetekceZdroju.geoget(windows()).getPosledniDatabaze());
	}

	@Test
	public void geogetVeVychozimRezimuOteviraDatabaziGeoget() throws Exception {
		final File db = soubor("c/GeoGet/data/geoget.db3", "x");
		soubor("c/GeoGet/data/PL.db3", "x");
		soubor("c/GeoGet/geogetboot.ini", "[boot]\r\ndatadir=.\\data\r\n");
		soubor("c/GeoGet/data/geoget.ini", "[geoget]\r\nlastDB=PL\r\nlastDBmode=0\r\n");
		Assert.assertEquals(kanon(db), DetekceZdroju.geoget(windows()).getPosledniDatabaze());
		soubor("c/GeoGet/data/geoget.ini", "[geoget]\r\nlastDB=PL\r\n");
		Assert.assertEquals("bez lastDBmode výchozí databáze", kanon(db), DetekceZdroju.geoget(windows()).getPosledniDatabaze());
		soubor("c/GeoGet/data/geoget.ini", "[geoget]\r\nlastDB=PL\r\nlastDBmode=1\r\n");
		Assert.assertEquals(kanon(new File(db.getParentFile(), "PL.db3")), DetekceZdroju.geoget(windows()).getPosledniDatabaze());
	}

	@Test
	public void lastDBSCestouSeNebere() throws Exception {
		soubor("domov/AppData/Roaming/GeoGet/geoget.db3", "x");
		soubor("domov/AppData/jinde.db3", "x");
		for (final String jmeno : new String[] { "../../jinde", "..\\..\\jinde", ".." }) {
			soubor("domov/AppData/Roaming/GeoGet/geoget.ini", "[geoget]\r\nlastDB=" + jmeno + "\r\nlastDBmode=1\r\n");
			Assert.assertNull(jmeno, DetekceZdroju.geoget(windows()).getPosledniDatabaze());
		}
	}

	@Test
	public void geogetMimoWindowsNehleda() throws Exception {
		soubor("domov/AppData/Roaming/GeoGet/geoget.db3", "x");
		Assert.assertNull(DetekceZdroju.geoget(new DetekceZdroju.Prostredi("Linux", domov, promenne)));
	}

	// ---------------------------------------------------------------- GSAK

	@Test
	public void gsakVAppData() throws Exception {
		final File db = soubor("domov/AppData/Roaming/gsak/data/Default/sqlite.db3", "x");
		final DetekceZdroju.Nalez n = DetekceZdroju.gsak(windows());
		Assert.assertEquals(kanon(db.getParentFile().getParentFile()), n.getSlozka());
	}

	@Test
	public void gsakPosledniDatabazeZGsakIni() throws Exception {
		soubor("domov/AppData/Roaming/gsak/data/Default/sqlite.db3", "x");
		final File db = soubor("domov/AppData/Roaming/gsak/data/GeoKukTest/sqlite.db3", "x");
		tmp.newFolder("domov", "AppData", "Roaming", "gsak", "data", "GrabbedImages");
		final File ini = new File(domov, "AppData/Roaming/gsak/gsak.ini");
		Files.write(ini.toPath(), "[TfmMain]\r\nlblX.Caption=Referenční\r\n[TfmOffline]\r\nlblDbase.Caption=GeoKukTest\r\n".getBytes(cz.geokuk.util.file.TextovySoubor.CP1250));
		Assert.assertEquals(kanon(db), DetekceZdroju.gsak(windows()).getPosledniDatabaze());
		Files.write(ini.toPath(), "[TfmOffline]\r\nlblDbase.Caption=GrabbedImages\r\n".getBytes(StandardCharsets.US_ASCII));
		Assert.assertNull("podsložka bez sqlite.db3 není databáze", DetekceZdroju.gsak(windows()).getPosledniDatabaze());
		Files.write(ini.toPath(), "[TfmOffline]\r\nlblDbase.Caption=..\\..\r\n".getBytes(StandardCharsets.US_ASCII));
		Assert.assertNull(DetekceZdroju.gsak(windows()).getPosledniDatabaze());
	}

	@Test
	public void gsakBezPromenneAppDataVDomove() throws Exception {
		promenne.remove("APPDATA");
		final File db = soubor("domov/AppData/Roaming/gsak/data/Default/sqlite.db3", "x");
		Assert.assertEquals(kanon(db.getParentFile().getParentFile()), DetekceZdroju.gsak(windows()).getSlozka());
	}

	@Test
	public void gsakPresmerovanyProfil() throws Exception {
		promenne.put("APPDATA", new File(tmp.getRoot(), "server/profil").getPath());
		soubor("domov/AppData/Roaming/gsak/data/Default/sqlite.db3", "x");
		final File db = soubor("server/profil/gsak/data/Default/sqlite.db3", "x");
		Assert.assertEquals(kanon(db.getParentFile().getParentFile()), DetekceZdroju.gsak(windows()).getSlozka());
	}

	@Test
	public void prazdnaSlozkaGsakuNeniNalezena() throws Exception {
		tmp.newFolder("domov", "AppData", "Roaming", "gsak", "data", "Default");
		Assert.assertNull(DetekceZdroju.gsak(windows()));
	}

	// ---------------------------------------------------------------- OpenSAK

	@Test
	public void opensakPodleBootstrapuAAktivniDatabaze() throws Exception {
		final File instalace = new File(tmp.getRoot(), "jinde/opensak");
		final File db = soubor("externi/Muj vylet.db", "x");
		soubor("cfg/opensak/bootstrap.json", "{\"install_dir\": \"" + json(instalace.getAbsolutePath()) + "\"}");
		soubor("jinde/opensak/opensak.json", "{\"display.theme\": \"dark\", \"databases.active\": \"" + json(db.getAbsolutePath()) + "\"}");
		promenne.put("XDG_CONFIG_HOME", new File(tmp.getRoot(), "cfg").getPath());
		final DetekceZdroju.Nalez n = DetekceZdroju.opensak(new DetekceZdroju.Prostredi("Linux", domov, promenne));
		Assert.assertEquals(kanon(db.getParentFile()), n.getSlozka());
		Assert.assertEquals(kanon(db), n.getPosledniDatabaze());
	}

	@Test
	public void opensakZMicrosoftStoreVDokumentech() throws Exception {
		final File db = soubor("domov/Documents/opensak/Default.db", "x");
		soubor("domov/Documents/opensak/opensak.json", "{\"databases.active\": \"" + json(db.getAbsolutePath()) + "\"}");
		final DetekceZdroju.Nalez n = DetekceZdroju.opensak(windows());
		Assert.assertEquals(kanon(db), n.getPosledniDatabaze());
	}

	/** Instalace z Microsoft Store: bootstrap.json je ve virtualizované složce balíčku a může obsahovat proměnnou prostředí. */
	@Test
	public void opensakZMicrosoftStorePresBalicek() throws Exception {
		final File db = soubor("jinde/opensak/Default.db", "x");
		promenne.put("LOCALAPPDATA", new File(domov, "AppData/Local").getPath());
		promenne.put("JINDE", new File(tmp.getRoot(), "jinde").getPath());
		soubor("domov/AppData/Local/Packages/AgreeDK.OpenSAK_8wekyb3d8bbwe/LocalCache/Roaming/opensak/bootstrap.json", "{\"install_dir\": \"%JINDE%" + json(File.separator) + "opensak\"}");
		soubor("jinde/opensak/opensak.json", "{\"databases.active\": \"" + json(db.getAbsolutePath()) + "\"}");
		Assert.assertEquals(kanon(db), DetekceZdroju.opensak(windows()).getPosledniDatabaze());
	}

	@Test
	public void opensakBezAktivniDatabazeJenSlozka() throws Exception {
		soubor("domov/.local/share/opensak/Default.db", "x");
		soubor("domov/.local/share/opensak/opensak.json", "{\"databases.active\": \"/neni/tam.db\"}");
		final DetekceZdroju.Nalez n = DetekceZdroju.opensak(new DetekceZdroju.Prostredi("Linux", domov, new HashMap<>()));
		Assert.assertEquals(kanon(new File(domov, ".local/share/opensak")), n.getSlozka());
		Assert.assertNull(n.getPosledniDatabaze());
	}

	@Test
	public void opensakNaMacu() throws Exception {
		soubor("domov/Library/Application Support/opensak/Default.db", "x");
		Assert.assertNotNull(DetekceZdroju.opensak(new DetekceZdroju.Prostredi("Mac OS X", domov, new HashMap<>())));
	}

	@Test
	public void bezOpensakuNicNenajde() {
		Assert.assertNull(DetekceZdroju.opensak(new DetekceZdroju.Prostredi("Linux", domov, new HashMap<>())));
	}

	@Test
	public void textZJsonuSEscape() throws Exception {
		final File f = soubor("a.json", "{\"databases.active\" : \"C:\\\\Users\\\\Kačer\\\\V\\u00fdlet.db\", \"jine\": 1}");
		Assert.assertEquals("C:\\Users\\Kačer\\Výlet.db", DetekceZdroju.jsonText(f, "databases.active"));
		Assert.assertNull(DetekceZdroju.jsonText(f, "jine"));
		Assert.assertNull(DetekceZdroju.jsonText(f, "chybi"));
	}

	private static String json(final String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
