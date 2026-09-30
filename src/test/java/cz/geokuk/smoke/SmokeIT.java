package cz.geokuk.smoke;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
	private int proxyPort;
	private String vlastnosti;

	@Before
	public void spustServer() throws IOException {
		server = new FalesnyDlazdicovyServer();
		proxyPort = server.getPort();
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
		zkontrolujBezChyb(adresar, zprava);
		final Map<String, Integer> pozadavky = server.getPozadavky();
		final List<String> dokola = pozadavky.entrySet().stream().filter(e -> e.getKey().startsWith("/"))
				.filter(e -> e.getValue() > (FalesnyDlazdicovyServer.zlobeni(e.getKey()) == null ? 1 : 3)).map(e -> e.getKey() + " " + e.getValue() + "x " + FalesnyDlazdicovyServer.zlobeni(e.getKey()))
				.collect(Collectors.toList());
		assertTrue("Dlaždice se stahovaly dokola: " + dokola, dokola.isEmpty());
		assertTrue("ka33 WEB #chyb má zlobení zachytit", pocitadlo(zprava, "ka33 WEB #chyb") > 0);
	}

	@Test
	public void bezSite() throws Exception {
		final File adresar = pripravAdresar("bezsite");
		// Uživatelská mapa i proxy pro Mapy.cz míří na zavřený port, jako když notebook nemá síť.
		final int zavreny;
		try (java.net.ServerSocket s = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
			zavreny = s.getLocalPort();
		}
		final File mapy = new File(adresar, "pracovni/uzivatelske-mapy.properties");
		Files.write(mapy.toPath(), new String(Files.readAllBytes(mapy.toPath()), StandardCharsets.UTF_8).replace(":" + server.getPort() + "/", ":" + zavreny + "/").getBytes(StandardCharsets.UTF_8));
		proxyPort = zavreny;
		final Properties zprava = spust(adresar, "bezsite", "meritka,posun");
		zkontrolujBezChyb(adresar, zprava);
		assertTrue("ka33 WEB #chyb má výpadek zachytit", pocitadlo(zprava, "ka33 WEB #chyb") > 0);
		final long oken = zprava.stringPropertyNames().stream().filter(k -> k.startsWith("okno.")).count();
		assertTrue("Bez sítě zůstalo otevřených " + oken + " oken: " + zprava, oken <= 2);
	}

	@Test
	public void poskozeneSouboryPriStartu() throws Exception {
		final File adresar = pripravAdresar("poskozene");
		final byte[] smeti = new byte[20_000];
		new Random(2).nextBytes(smeti);
		final File cache = new File(adresar, "home/geokuk/prchave/kachle/tiles.sqlite");
		cache.getParentFile().mkdirs();
		Files.write(cache.toPath(), smeti);
		Files.write(new File(adresar, "home/geokuk/lovim.ggt").toPath(), Arrays.copyOf(smeti, 3000));
		Files.write(new File(adresar, "home/geokuk/tedne.ggt").toPath(), "GC1\nnesmysl;;;\n\u0000\n".getBytes(StandardCharsets.UTF_8));
		Files.write(new File(adresar, "pracovni/geokuk-preferences.xml").toPath(), "<?xml version=\"1.0\"?><preferences><useknute".getBytes(StandardCharsets.UTF_8));
		Files.write(new File(adresar, "pracovni/uzivatelske-mapy.properties").toPath(), "rozbita.url=http://127.0.0.1/\n".getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND);

		final Properties zprava = spust(adresar, "poskozene", "meritka");
		zkontrolujBezChyb(adresar, zprava, false);
		assertTrue("Poškozená cache se má odložit", new File(cache.getPath() + ".vadna").isFile());
		assertTrue("Mapa se má i tak načíst", pocitadlo(zprava, "ka32 WEB #načtených") > 50);
		assertTrue("Nová cache se má při ukončení dopsat", cache.length() > 100_000);
		assertEquals("Načtené waypointy", String.valueOf(pocetWpt), zprava.getProperty("kese.wpt"));
	}

	@Test
	public void netrpelivyUzivatel() throws Exception {
		final File adresar = pripravAdresar("netrpelivy");
		final Properties zprava = spust(adresar, "netrpelivy", "zbesile,okno");
		zkontrolujBezChyb(adresar, zprava);
		assertEquals(0, pocitadlo(zprava, "ka33 WEB #chyb"));
		assertEquals(0, pocitadlo(zprava, "ka24 DISK cache #chyb čtení"));
		final long stazeno = pocitadlo(zprava, "Downloadlé dlaždice");
		final Map<String, Integer> pozadavky = server.getPozadavky();
		assertEquals("Každá stažená dlaždice se má uložit, i když byl požadavek mezitím zrušen", pozadavky.size(), dlazdicVCache(adresar));
		// Znovu se stáhne jen to, co se ještě nestihlo zapsat.
		final long vicekrat = pozadavky.values().stream().filter(p -> p > 1).count();
		assertTrue("Opakovaně staženo " + vicekrat + " z " + pozadavky.size(), vicekrat <= pozadavky.size() / 5);
		assertTrue(stazeno >= pozadavky.size());
	}

	/** Uživatel spustí Geokuk dvakrát (dvojklik dvakrát), obě instance sdílí cache dlaždic i datovou složku. */
	@Test
	public void dveInstanceNajednou() throws Exception {
		final File adresar = pripravAdresar("dve");
		final java.util.concurrent.ExecutorService vlakna = java.util.concurrent.Executors.newFixedThreadPool(2);
		try {
			final java.util.concurrent.Future<Properties> prvni = vlakna.submit(() -> spust(adresar, "prvni", "meritka,posun"));
			final java.util.concurrent.Future<Properties> druha = vlakna.submit(() -> spust(adresar, "druha", "meritka,posun"));
			for (final Properties zprava : Arrays.asList(prvni.get(), druha.get())) {
				zkontrolujBezChyb(adresar, zprava, false);
				assertEquals("Načtené waypointy", String.valueOf(pocetWpt), zprava.getProperty("kese.wpt"));
				assertEquals(0, pocitadlo(zprava, "ka33 WEB #chyb"));
			}
		} finally {
			vlakna.shutdown();
		}
		// Po obou běhech musí být cache zdravá a použitelná.
		final Properties treti = spust(adresar, "treti", "meritka,posun");
		zkontrolujBezChyb(adresar, treti, false);
		assertEquals(0, pocitadlo(treti, "ka24 DISK cache #chyb čtení"));
		assertFalse("Cache se nesmí odložit jako vadná", new File(adresar, "home/geokuk/prchave/kachle/tiles.sqlite.vadna").exists());
		assertTrue("Třetí běh bere dlaždice z cache", pocitadlo(treti, "ka22 DISK cache #zásahů") > 100);
	}

	@Test
	public void zmeneneProstredi() throws Exception {
		final File adresar = pripravAdresar("prostredi");
		vlastnosti = "-Dsmoke.zmeneneProstredi=true";
		final Properties zprava = spust(adresar, "prostredi", "meritka,posun");
		zkontrolujBezChyb(adresar, zprava, false);
		assertTrue("Mapa se má načíst i bez cache", pocitadlo(zprava, "ka32 WEB #načtených") > 100);
		assertEquals("Bez datové složky nejsou keše", "0", zprava.getProperty("kese.wpt"));
		assertEquals("Do nedostupné cache se nic nezapíše", 0, pocitadlo(zprava, "ka42 disk write #dlaždic"));
		assertTrue("Uživatel se má dozvědět, že cache nejde použít: " + zprava,
				zprava.stringPropertyNames().stream().filter(k -> k.startsWith("okno.")).anyMatch(k -> zprava.getProperty(k).contains("Cache dlaždic ve složce")));
	}

	@Test
	public void neporadnaDataVDatoveSlozce() throws Exception {
		final File adresar = pripravAdresar("data");
		final File data = new File(adresar, "home/geokuk");
		int dobre = pocetWpt;
		dobre += SyntetickeKese.zapis(new File(data, "druhe.gpx"), 10_000, 5, 50.1, 14.4, 0.05);
		// Kopie téhož souboru, keše se nemají načíst dvakrát.
		Files.copy(new File(data, "druhe.gpx").toPath(), new File(data, "druhe - kopie.gpx").toPath());
		try (OutputStream os = new FileOutputStream(new File(data, "bom.gpx"))) {
			os.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
			dobre += SyntetickeKese.zapis(os, 11_000, 5, 50.1, 14.4, 0.05);
		}
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(new File(data, "pq.zip")))) {
			zip.putNextEntry(new ZipEntry("12345.gpx"));
			dobre += SyntetickeKese.zapis(zip, 12_000, 5, 50.1, 14.4, 0.05);
			zip.closeEntry();
		}
		final ByteArrayOutputStream cely = new ByteArrayOutputStream();
		SyntetickeKese.zapis(cely, 13_000, 20, 50.1, 14.4, 0.05);
		Files.write(new File(data, "useknuty.gpx").toPath(), Arrays.copyOf(cely.toByteArray(), cely.size() / 2));
		Files.write(new File(data, "prazdny.gpx").toPath(), new byte[0]);
		final byte[] smeti = new byte[5000];
		new Random(1).nextBytes(smeti);
		Files.write(new File(data, "smeti.gpx").toPath(), smeti);
		Files.write(new File(data, "stranka.gpx").toPath(), "<!DOCTYPE html><html><body>Přihlaste se k Wi-Fi</body></html>".getBytes(StandardCharsets.UTF_8));
		Files.write(new File(data, "rozbity.zip").toPath(), Arrays.copyOf(smeti, 100));

		final Properties zprava = spust(adresar, "data", "meritka");
		zkontrolujBezChyb(adresar, zprava, false);
		final long nacteno = Long.parseLong(zprava.getProperty("kese.wpt"));
		// Z useknutého souboru se může načíst začátek.
		assertTrue("Načteno " + nacteno + " waypointů, z dobrých souborů jich je " + dobre, nacteno >= dobre && nacteno < dobre + 60);
	}

	@Test
	public void vsechnyPolozkyMenu() throws Exception {
		final File adresar = pripravAdresar("menu");
		final Properties zprava = spust(adresar, "menu", "menu,vzhled");
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
		prikaz.add("-Dhttp.proxyPort=" + proxyPort);
		prikaz.add("-Dhttp.nonProxyHosts=localhost|127.*");
		prikaz.add("-Dfile.encoding=UTF-8");
		if (vlastnosti != null) {
			prikaz.add(vlastnosti);
		}
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
		final long pamet = Long.parseLong(zprava.getProperty("pamet.mb", "0"));
		if (pamet > 400) {
			problemy.add("Po scénáři zůstalo obsazeno " + pamet + " MB paměti");
		}
		final File excrep = new File(adresar, "tmp/geokuk/excrep");
		final String[] vypisy = excrep.list();
		if (bezVypisu && vypisy != null && vypisy.length > 0) {
			problemy.add("Výpisy chyb v " + excrep + ": " + Arrays.toString(vypisy));
		}
		assertTrue(String.join("\n", problemy), problemy.isEmpty());
	}

	/** Počet dlaždic v cache po ukončení programu. */
	private static long dlazdicVCache(final File adresar) throws Exception {
		try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + new File(adresar, "home/geokuk/prchave/kachle/tiles.sqlite"))) {
			final String tabulka;
			try (java.sql.ResultSet t = c.createStatement().executeQuery("select name from sqlite_master where type = 'table'")) {
				t.next();
				tabulka = t.getString(1);
			}
			try (java.sql.ResultSet pocet = c.createStatement().executeQuery("select count(*) from " + tabulka)) {
				pocet.next();
				return pocet.getLong(1);
			}
		}
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
