package cz.geokuk.smoke;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.*;

/**
 * Ručně spouštěný smoke test celého programu nad falešným dlaždicovým serverem. Potřebuje displej (xvfb):
 *
 * <pre>
 * xvfb-run -a -s "-screen 0 1400x900x24" ./mvnw -B -P smoke verify
 * </pre>
 */
public class SmokeIT {

	private static final File KOREN = new File("target/smoke");

	private FalesnyDlazdicovyServer server;
	private int pocetWpt;

	@Before
	public void spustServer() throws IOException {
		server = new FalesnyDlazdicovyServer();
	}

	@After
	public void zastavServer() {
		if (server != null) {
			server.close();
		}
	}

	@Test
	public void mapaSeNacteZeServeruAPakZCache() throws Exception {
		final File adresar = pripravAdresar("mapa");

		final Properties prvni = spust(adresar, "prvni", "meritka,posun");
		zkontrolujBezChyb(adresar, prvni);
		assertEquals("Načtené waypointy", String.valueOf(pocetWpt), prvni.getProperty("kese.wpt"));
		final Map<String, Integer> pozadavky = server.getPozadavky();
		final List<String> vicekrat = pozadavky.entrySet().stream().filter(e -> e.getValue() > 1).map(e -> e.getKey() + " " + e.getValue() + "x").collect(Collectors.toList());
		assertTrue("Tytéž dlaždice se stahovaly opakovaně: " + vicekrat, vicekrat.isEmpty());
		assertTrue("Ze serveru přišlo jen " + pozadavky.size() + " dlaždic", pozadavky.size() > 100);
		assertEquals("Downloadlé dlaždice", pozadavky.size(), pocitadlo(prvni, "Downloadlé dlaždice"));
		assertEquals(0, pocitadlo(prvni, "ka24 DISK cache #chyb čtení"));
		assertEquals(0, pocitadlo(prvni, "ka33 WEB #chyb"));
		final long ulozeno = pocitadlo(prvni, "ka42 disk write #dlaždic");
		assertEquals("Na disk se má uložit, co se načetlo", pocitadlo(prvni, "ka32 WEB #načtených"), ulozeno);
		// Dlaždice stažené až po zrušení požadavku se neukládají, ale nesmí jich být víc než pár.
		final long neulozeno = pozadavky.size() - ulozeno;
		assertTrue("Neuloženo " + neulozeno + " z " + pozadavky.size(), neulozeno <= pozadavky.size() / 20);

		final Map<String, Integer> predDruhym = server.getPozadavky();
		final Properties druhy = spust(adresar, "druhy", "meritka,posun");
		zkontrolujBezChyb(adresar, druhy);
		final List<String> stazenoZnovu = server.getPozadavky().entrySet().stream().filter(e -> !e.getValue().equals(predDruhym.get(e.getKey()))).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		assertTrue("Druhý běh nad stejnou cache stáhl " + stazenoZnovu.size() + " dlaždic, neuloženo bylo jen " + neulozeno + ": " + stazenoZnovu, stazenoZnovu.size() <= neulozeno);
		assertEquals(0, pocitadlo(druhy, "ka24 DISK cache #chyb čtení"));
		assertEquals(0, pocitadlo(druhy, "ka33 WEB #chyb"));
		assertTrue(pocitadlo(druhy, "ka22 DISK cache #zásahů") > 100);
	}

	@Test
	public void zlobivyServer() throws Exception {
		final File adresar = pripravAdresar("zlobivy");
		server.setZlobi(true);
		final Properties zprava = spust(adresar, "zlobivy", "meritka,posun");
		// Výpisy chyb v excrep tu jsou v pořádku, jde o to, že program nespadne, nezamrzne a nezahltí server.
		zkontrolujBezChyb(adresar, zprava, false);
		final Map<String, Integer> pozadavky = server.getPozadavky();
		final List<String> dokola = pozadavky.entrySet().stream().filter(e -> e.getKey().startsWith("/"))
				.filter(e -> e.getValue() > (FalesnyDlazdicovyServer.zlobeni(e.getKey()) == null ? 1 : 3)).map(e -> e.getKey() + " " + e.getValue() + "x " + FalesnyDlazdicovyServer.zlobeni(e.getKey()))
				.collect(Collectors.toList());
		assertTrue("Dlaždice se stahovaly dokola: " + dokola, dokola.isEmpty());
		assertTrue("ka33 WEB #chyb má zlobení zachytit", pocitadlo(zprava, "ka33 WEB #chyb") > 0);
	}

	@Test
	public void vsechnyPolozkyMenu() throws Exception {
		final File adresar = pripravAdresar("menu");
		final Properties zprava = spust(adresar, "menu", "menu");
		zkontrolujBezChyb(adresar, zprava);
		assertTrue(zprava.stringPropertyNames().stream().filter(k -> k.startsWith("menu.")).count() > 50);
	}

	private File pripravAdresar(final String jmeno) throws IOException {
		final File adresar = new File(KOREN, jmeno).getAbsoluteFile();
		smaz(adresar);
		final File pracovni = new File(adresar, "pracovni");
		new File(adresar, "home/geokuk").mkdirs();
		pocetWpt = SyntetickeKese.zapis(new File(adresar, "home/geokuk/kese.gpx"), 3000, 50.08, 14.42, 0.05);
		new File(adresar, "tmp").mkdirs();
		pracovni.mkdirs();
		final String mapy = "smoke.nazev=" + SmokeScenar.MAPA + "\n" //
				+ "smoke.url=" + server.getUrl() + "\n" //
				+ "smoke.max=18\n" //
				+ "smoke.hromadne=ano\n";
		Files.write(new File(pracovni, "uzivatelske-mapy.properties").toPath(), mapy.getBytes(StandardCharsets.UTF_8));
		Files.copy(new File(System.getProperty("smoke.jar")).toPath(), new File(pracovni, "geokuk.jar").toPath());
		return adresar;
	}

	private Properties spust(final File adresar, final String beh, final String kroky) throws Exception {
		final File zprava = new File(adresar, beh + ".properties");
		final List<String> prikaz = new ArrayList<>();
		prikaz.add(new File(System.getProperty("java.home"), "bin/java").getPath());
		prikaz.add("-Xmx768m");
		prikaz.add("-Duser.home=" + new File(adresar, "home"));
		prikaz.add("-Djava.io.tmpdir=" + new File(adresar, "tmp"));
		prikaz.add("-Djava.util.prefs.userRoot=" + new File(adresar, "prefs"));
		// Vestavěné mapy (Mapy.cz po http) jdou přes falešný server jako proxy.
		prikaz.add("-Dhttp.proxyHost=127.0.0.1");
		prikaz.add("-Dhttp.proxyPort=" + server.getPort());
		prikaz.add("-Dhttp.nonProxyHosts=localhost|127.*");
		prikaz.add("-Dfile.encoding=UTF-8");
		// Program běží ze sestaveného jaru v pracovním adresáři, jen tak si vedle sebe najde uživatelské mapy.
		prikaz.add("-cp");
		prikaz.add(new File(adresar, "pracovni/geokuk.jar") + File.pathSeparator + System.getProperty("smoke.testClasses"));
		prikaz.add(SmokeScenar.class.getName());
		prikaz.add(zprava.getPath());
		prikaz.add(new File(adresar, beh + ".png").getPath());
		prikaz.add(kroky);
		final Process p = new ProcessBuilder(prikaz).directory(new File(adresar, "pracovni")).redirectErrorStream(true).redirectOutput(new File(adresar, beh + ".log")).start();
		if (!p.waitFor(10, TimeUnit.MINUTES)) {
			p.destroyForcibly();
			fail("Scénář " + beh + " nedoběhl do 10 minut, viz " + new File(adresar, beh + ".log"));
		}
		assertTrue("Scénář " + beh + " nezapsal zprávu, viz " + new File(adresar, beh + ".log"), zprava.isFile());
		final Properties vysledek = new Properties();
		try (Reader r = new InputStreamReader(new FileInputStream(zprava), StandardCharsets.UTF_8)) {
			vysledek.load(r);
		}
		return vysledek;
	}

	private static void zkontrolujBezChyb(final File adresar, final Properties zprava) {
		zkontrolujBezChyb(adresar, zprava, true);
	}

	private static void zkontrolujBezChyb(final File adresar, final Properties zprava, final boolean bezVypisu) {
		final List<String> problemy = new ArrayList<>();
		zprava.stringPropertyNames().stream().filter(k -> k.startsWith("chyba.") || k.startsWith("nezachycena.")).sorted().forEach(k -> problemy.add(k + ": " + zprava.getProperty(k)));
		final long edt = Long.parseLong(zprava.getProperty("edt.nejdelsiMs", "0"));
		if (edt > 3000) {
			problemy.add("Událost na EDT trvala " + edt + " ms: " + zprava.getProperty("edt.pomala.0"));
		}
		final File excrep = new File(adresar, "tmp/geokuk/excrep");
		final String[] vypisy = excrep.list();
		if (bezVypisu && vypisy != null && vypisy.length > 0) {
			problemy.add("Výpisy chyb v " + excrep + ": " + Arrays.toString(vypisy));
		}
		assertTrue(String.join("\n", problemy), problemy.isEmpty());
	}

	private static long pocitadlo(final Properties zprava, final String jmeno) {
		final String hodnota = zprava.getProperty("pocitadlo." + jmeno);
		assertNotNull("Chybí počítadlo " + jmeno, hodnota);
		return Long.parseLong(hodnota);
	}

	private static void smaz(final File f) {
		final File[] deti = f.listFiles();
		if (deti != null) {
			for (final File d : deti) {
				smaz(d);
			}
		}
		f.delete();
	}
}
