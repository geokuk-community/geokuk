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
	private static final long EDT_LIMIT_MS = 3000;
	private static final long EDT_LIMIT_START_MS = 10_000;

	private FalesnyDlazdicovyServer server;
	private int pocetWpt;
	private int proxyPort;
	private final List<String> vlastnosti = new ArrayList<>();

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
		final long zacatek = System.currentTimeMillis();
		final Properties zprava = spust(adresar, "zlobivy", "meritka,posun");
		final long trvani = System.currentTimeMillis() - zacatek;
		zkontrolujBezChyb(adresar, zprava);
		final Map<String, Integer> pozadavky = server.getPozadavky();
		// Program si nestažené dlaždice pamatuje 30 s, na pomalém stroji je scénář delší a smí je po každém vypršení zkusit znovu.
		final Map<String, Integer> zaPametChyb = server.getNejvicPozadavkuZaDobu(30_000);
		final long nejvicCelkem = 3 + (trvani + 29_999) / 30_000;
		final List<String> dokola = pozadavky.entrySet().stream().filter(e -> e.getKey().startsWith("/"))
				.filter(e -> FalesnyDlazdicovyServer.zlobeni(e.getKey()) == null ? e.getValue() > 1 : zaPametChyb.getOrDefault(e.getKey(), 0) > 3 || e.getValue() > nejvicCelkem)
				.map(e -> e.getKey() + " " + e.getValue() + "x (nejvýš " + nejvicCelkem + "), za 30 s nejvýš " + zaPametChyb.get(e.getKey()) + "x " + FalesnyDlazdicovyServer.zlobeni(e.getKey()))
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
		final File mapy = new File(adresar, "data/uzivatelske-mapy.properties");
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
		final File cache = new File(adresar, "data/cache/tiles.sqlite");
		cache.getParentFile().mkdirs();
		Files.write(cache.toPath(), smeti);
		new File(adresar, "data/vylety").mkdirs();
		Files.write(new File(adresar, "data/vylety/lovim.ggt").toPath(), Arrays.copyOf(smeti, 3000));
		Files.write(new File(adresar, "data/vylety/tedne.ggt").toPath(), "GC1\nnesmysl;;;\n\u0000\n".getBytes(StandardCharsets.UTF_8));
		Files.write(new File(adresar, "data/nastaveni.xml").toPath(), "<?xml version=\"1.0\"?><preferences><useknute".getBytes(StandardCharsets.UTF_8));
		Files.write(new File(adresar, "data/uzivatelske-mapy.properties").toPath(), "rozbita.url=http://127.0.0.1/\n".getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND);

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
		assertFalse("Cache se nesmí odložit jako vadná", new File(adresar, "data/cache/tiles.sqlite.vadna").exists());
		assertTrue("Třetí běh bere dlaždice z cache", pocitadlo(treti, "ka22 DISK cache #zásahů") > 100);
		for (final String beh : Arrays.asList("prvni", "druha", "treti")) {
			final String log = new String(Files.readAllBytes(new File(adresar, beh + ".log").toPath()), StandardCharsets.UTF_8);
			assertFalse("Instance " + beh + " nezapsala dlaždice do sdílené cache, viz " + beh + ".log", log.contains("Nepodařilo se zapsat dlaždice do databáze"));
		}
	}

	@Test
	public void zmeneneProstredi() throws Exception {
		final File adresar = pripravAdresar("prostredi");
		vlastnosti.add("-Dsmoke.zmeneneProstredi=true");
		final Properties zprava = spust(adresar, "prostredi", "meritka,posun");
		zkontrolujBezChyb(adresar, zprava, false);
		assertTrue("Mapa se má načíst", pocitadlo(zprava, "ka32 WEB #načtených") > 100);
		assertEquals("Bez datové složky nejsou keše", "0", zprava.getProperty("kese.wpt"));
		assertTrue("Cache je ve složce programu i po změně prostředí", pocitadlo(zprava, "ka42 disk write #dlaždic") > 0);
	}

	/** První start bez nastavení převezme nastavení starší verze z Java Preferences. Jen na Linuxu, jinde jsou v registru nebo v plistu uživatele. */
	@Test
	public void prevzetiNastaveniZJavaPreferences() throws Exception {
		Assume.assumeTrue("Java Preferences jen v souborech", System.getProperty("os.name").startsWith("Linux"));
		final File adresar = pripravAdresar("prevzeti");
		final File nastaveni = new File(adresar, "data/nastaveni.xml");
		Files.delete(nastaveni.toPath());
		final File prefs = new File(adresar, "prefs/.java/.userPrefs/geokuk/current/vseobecne/prefs.xml");
		prefs.getParentFile().mkdirs();
		Files.write(prefs.toPath(), ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<!DOCTYPE map SYSTEM \"http://java.sun.com/dtd/preferences.dtd\">\n"
				+ "<map MAP_XML_VERSION=\"1.0\">\n  <entry key=\"smokePrevzato\" value=\"ano\"/>\n</map>\n").getBytes(StandardCharsets.UTF_8));
		final Properties zprava = spust(adresar, "prevzeti", "meritka");
		zkontrolujBezChyb(adresar, zprava);
		final String ulozene = new String(Files.readAllBytes(nastaveni.toPath()), StandardCharsets.UTF_8);
		assertTrue("Nastavení z Java Preferences se má převzít: " + ulozene, ulozene.contains("smokePrevzato"));
	}

	/** Úplně první start: bez nastavení a bez čeho převzít. */
	@Test
	public void prvniStartBezNastaveni() throws Exception {
		Assume.assumeTrue("Java Preferences jen v souborech", System.getProperty("os.name").startsWith("Linux"));
		final File adresar = pripravAdresar("prvni-start");
		final File nastaveni = new File(adresar, "data/nastaveni.xml");
		Files.delete(nastaveni.toPath());
		final Properties zprava = spust(adresar, "prvni-start", "meritka");
		zkontrolujBezChyb(adresar, zprava);
		assertEquals("Načtené waypointy", String.valueOf(pocetWpt), zprava.getProperty("kese.wpt"));
		assertTrue("Nastavení se má uložit", nastaveni.isFile());
	}

	@Test
	public void dalkoveOvladani() throws Exception {
		final File adresar = pripravAdresar("ovladani");
		vlastnosti.add("-Dsmoke.args=--ovladani=0 --ovladani-devel");
		final Properties zprava = spust(adresar, "ovladani", "ovladani");
		zkontrolujBezChyb(adresar, zprava);
		assertTrue(zprava.getProperty("ovladani.stav"), zprava.getProperty("ovladani.stav").contains("\"podklad\":\"TURIST_M\""));
		assertTrue("Mapy.cz přes proxy", server.getPozadavky().keySet().stream().anyMatch(k -> k.contains("turist-m")));
	}

	/** Položky menu, které program ukončí, otevřou prohlížeč, přepnou vzhled nebo ovládání samo vypnou. */
	private static final List<String> NESPOUSTET_ZVENKU = Arrays.asList("Soubor > Konec", "Soubor > Celá obrazovka", "Soubor > Dálkové ovládání", "Mapy > Online", "Nápověda > Nápověda",
			"Nápověda > Webová stránka", "Nápověda > Zadat problém", "Nápověda > Zkontrolovat aktualizace", "Nápověda > Nabízet testovací verze", "Skin > ");

	/**
	 * Program spuštěný jako obyčejný {@code java -jar geokuk.jar --ovladani=0} se řídí jen zvenku přes dálkové ovládání: projde všechny položky
	 * menu, zkontroluje otevřená okna a skončí přes Soubor > Konec.
	 */
	@Test
	public void zvenkuPresDalkoveOvladani() throws Exception {
		final File adresar = pripravAdresar("zvenku");
		final Process p = spustZvenku(adresar);
		final List<String> problemy = new ArrayList<>();
		final StringBuilder prubeh = new StringBuilder();
		try {
			final KlientOvladani k = pripojSe(adresar);
			projdiMenuZvenku(k, prubeh, problemy, true);
			prubeh.append("Stav: ").append(k.stav()).append('\n');
			ukonciZvenku(p, k, prubeh, problemy);
		} finally {
			p.destroyForcibly();
			Files.write(new File(adresar, "zvenku-prubeh.txt").toPath(), prubeh.toString().getBytes(StandardCharsets.UTF_8));
		}
		final String[] vypisy = new File(adresar, "data/log/chyby").list();
		if (vypisy != null && vypisy.length > 0) {
			problemy.add("Výpisy chyb v excrep: " + Arrays.toString(vypisy));
		}
		final List<String> mimo = zapsanoMimo(adresar);
		if (!mimo.isEmpty()) {
			problemy.add("Zapsáno mimo složku programu: " + mimo);
		}
		assertTrue(String.join("\n", problemy), problemy.isEmpty());
		assertTrue("Proxy obsloužila i vestavěné mapy nebo uživatelskou mapu", server.getPocetPozadavku() > 0);
	}

	/** Opakované otevírání a zavírání všech dialogů nesmí zvyšovat obsazenou paměť. */
	@Test
	public void opakovaneDialogyNeunikaji() throws Exception {
		final File adresar = pripravAdresar("unik");
		final Process p = spustZvenku(adresar);
		final List<String> problemy = new ArrayList<>();
		final StringBuilder prubeh = new StringBuilder();
		try {
			final KlientOvladani k = pripojSe(adresar);
			projdiMenuZvenku(k, prubeh, problemy, false);
			final int pred = pametPoUklidu(k);
			for (int i = 0; i < 5; i++) {
				projdiMenuZvenku(k, new StringBuilder(), problemy, false);
			}
			final int po = pametPoUklidu(k);
			prubeh.append("Paměť po úklidu: po 1. průchodu ").append(pred).append(" MB, po dalších 5 ").append(po).append(" MB\n");
			if (po - pred > 30) {
				problemy.add("Paměť po 5 průchodech menu vzrostla z " + pred + " na " + po + " MB");
			}
			ukonciZvenku(p, k, prubeh, problemy);
		} finally {
			p.destroyForcibly();
			Files.write(new File(adresar, "unik-prubeh.txt").toPath(), prubeh.toString().getBytes(StandardCharsets.UTF_8));
		}
		assertTrue(String.join("\n", problemy), problemy.isEmpty());
	}

	private static int pametPoUklidu(final KlientOvladani k) throws Exception {
		int nejmene = Integer.MAX_VALUE;
		for (int i = 0; i < 3; i++) {
			nejmene = Math.min(nejmene, ((Number) ((Map<?, ?>) k.get("/stav?gc=ano")).get("pametMb")).intValue());
			Thread.sleep(500);
		}
		return nejmene;
	}

	private Process spustZvenku(final File adresar) throws IOException {
		final List<String> prikaz = new ArrayList<>(jvm(adresar));
		prikaz.add("-jar");
		prikaz.add(new File(adresar, "pracovni/geokuk.jar").getPath());
		prikaz.add("--ovladani=0");
		prikaz.add("--ovladani-devel");
		return new ProcessBuilder(prikaz).directory(new File(adresar, "pracovni")).redirectErrorStream(true).redirectOutput(new File(adresar, adresar.getName() + ".log")).start();
	}

	private KlientOvladani pripojSe(final File adresar) throws Exception {
		final File soubor = new File(adresar, "data/ovladani.properties");
		cekej(60, soubor::isFile);
		final KlientOvladani k = new KlientOvladani(soubor);
		cekej(120, () -> ((Number) k.stav().get("waypointu")).intValue() == pocetWpt);
		k.post("/podklad", "jmeno", "user-smoke");
		// Vybraná keš zpřístupní položky pro keše, výlety a cesty.
		k.post("/kes", "kod", "GC" + Integer.toString(0x10000 + 7, 36).toUpperCase(Locale.ROOT));
		pockejNaKlid(k);
		return k;
	}

	/** @param hlidatEdt hlídat odezvu EDT; opakované průchody slouží k měření paměti a úklid paměti v nich odezvu zkresluje */
	private static void projdiMenuZvenku(final KlientOvladani k, final StringBuilder prubeh, final List<String> problemy, final boolean hlidatEdt) throws Exception {
		long nejdelsiOdezva = 0;
		for (final Map<String, Object> polozka : k.seznam("/menu")) {
			final String cesta = (String) polozka.get("cesta");
			if (!Boolean.TRUE.equals(polozka.get("povoleno")) || NESPOUSTET_ZVENKU.stream().anyMatch(cesta::startsWith) || cesta.startsWith("Mapy > ") && polozka.containsKey("zaskrtnuto")
					&& !cesta.equals("Mapy > Ukládat mapy")) {
				continue;
			}
			final int kolikrat = polozka.containsKey("zaskrtnuto") ? 2 : 1;
			for (int i = 0; i < kolikrat; i++) {
				try {
					k.post("/menu", "cesta", cesta);
				} catch (final IOException e) {
					if (e.getMessage().contains("není povolená") || e.getMessage().contains("v menu není")) {
						continue; // předchozí akce ji vypnula nebo přejmenovala (délka cesty v názvu)
					}
					throw e;
				}
				Thread.sleep(700);
				// Seznam oken se zjišťuje na EDT, dlouhá odpověď znamená, že akce EDT zablokovala.
				final long pred = System.currentTimeMillis();
				final List<Map<String, Object>> okna = k.seznam("/okna");
				final long odezva = System.currentTimeMillis() - pred;
				nejdelsiOdezva = Math.max(nejdelsiOdezva, odezva);
				if (hlidatEdt && odezva > 3000) {
					problemy.add("Po " + cesta + " EDT neodpovídal " + odezva + " ms");
				}
				for (final Map<String, Object> okno : okna) {
					if (!"GeoKuk".equals(okno.get("titulek"))) {
						prubeh.append(cesta).append(" → ").append(okno.get("titulek")).append(' ').append(okno.get("text")).append('\n');
						if (((Number) okno.get("sirka")).intValue() < 100 || ((Number) okno.get("vyska")).intValue() < 50 || ((Number) okno.get("komponent")).intValue() == 0) {
							problemy.add("Po " + cesta + " je okno prázdné nebo malé: " + okno);
						}
					}
				}
				k.post("/okna/zavri");
				cekej(20, () -> k.seznam("/okna").size() == 1);
				pockejNaKlid(k);
			}
		}
		prubeh.append("Nejdelší odezva EDT po akci menu: ").append(nejdelsiOdezva).append(" ms\n");
	}

	private static void ukonciZvenku(final Process p, final KlientOvladani k, final StringBuilder prubeh, final List<String> problemy) throws Exception {
		k.post("/menu", "cesta", "Soubor > Konec");
		// Přidat do cesty v průchodu menu založí cestu, Konec se proto zeptá na uložení.
		if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
			for (final Map<String, Object> okno : k.seznam("/okna")) {
				prubeh.append("Při Konci: ").append(okno.get("titulek")).append(' ').append(okno.get("text")).append('\n');
				if (!"GeoKuk".equals(okno.get("titulek"))) {
					prubeh.append("  tlačítka ").append(okno.get("tlacitka")).append('\n');
					final String neukladat = ((List<?>) okno.get("tlacitka")).stream().map(String::valueOf).filter(t -> t.matches("(?i)ne|no|neukládat|zahodit.*")).findFirst()
							.orElseThrow(() -> new AssertionError("Dialog při Konci nemá tlačítko pro neukládání: " + okno));
					k.post("/okna/tlacitko", "titulek", (String) okno.get("titulek"), "text", neukladat);
				}
			}
		}
		if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
			problemy.add("Program po Soubor > Konec neskončil");
		}
	}

	private static void pockejNaKlid(final KlientOvladani k) throws Exception {
		cekej(60, () -> ((Number) k.stav().get("frontyDlazdic")).intValue() == 0);
	}

	private interface Podminka {
		boolean plati() throws Exception;
	}

	private static void cekej(final int sekund, final Podminka podminka) throws Exception {
		final long konec = System.currentTimeMillis() + sekund * 1000L;
		while (!podminka.plati()) {
			if (System.currentTimeMillis() > konec) {
				fail("Nedočkal jsem se do " + sekund + " s");
			}
			Thread.sleep(200);
		}
	}

	/** 50 tisíc keší (asi 100 tisíc waypointů) po celých Čechách: načtení, měřítka, posun. */
	@Test
	public void velkaData() throws Exception {
		final File adresar = pripravAdresar("velka", 50_000);
		final Properties zprava = spust(adresar, "velka", "meritka,posun");
		zkontrolujBezChyb(adresar, zprava);
		assertEquals(String.valueOf(pocetWpt), zprava.getProperty("kese.wpt"));
		final long nacteni = Long.parseLong(zprava.getProperty("start.keseMs"));
		assertTrue("Načtení " + pocetWpt + " waypointů trvalo " + nacteni + " ms", nacteni < 60_000);
	}

	/** Databáze GeoGetu s 200 tisíci keší a 200 tisíci waypointů, jak ji mají uživatelé s daty větší než ČR. */
	@Test
	public void velkaDatabazeGeogetu() throws Exception {
		final File adresar = pripravAdresar("geoget", 0);
		final File geoget = new File(adresar, "home/geoget");
		geoget.mkdirs();
		final long zacatek = System.currentTimeMillis();
		final int wpt = SyntetickaDatabazeGeogetu.zapis(new File(geoget, "geoget.db3"), 200_000, 200_000);
		final long vyroba = System.currentTimeMillis() - zacatek;
		vlastnosti.add("-Dsmoke.geoget=" + geoget);
		vlastnosti.add("-Xmx2g");
		final Properties zprava = spust(adresar, "geoget", "meritka,posun");
		zkontrolujBezChyb(adresar, zprava);
		assertEquals(String.valueOf(wpt), zprava.getProperty("kese.wpt"));
		System.out.println("Databáze GeoGetu: výroba " + vyroba + " ms, načtení " + zprava.getProperty("start.keseMs") + " ms, paměť " + zprava.getProperty("pamet.mb") + " MB");
	}

	@Test
	public void neporadnaDataVDatoveSlozce() throws Exception {
		final File adresar = pripravAdresar("data");
		final File data = new File(adresar, "data/gpx");
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
		return pripravAdresar(jmeno, 3000);
	}

	private File pripravAdresar(final String jmeno, final int kesi) throws IOException {
		final File adresar = new File(KOREN, jmeno).getAbsoluteFile();
		smaz(adresar);
		final File pracovni = new File(adresar, "pracovni");
		new File(adresar, "data/gpx").mkdirs();
		pocetWpt = SyntetickeKese.zapis(new File(adresar, "data/gpx/kese.gpx"), kesi, 50.08, 14.42, kesi > 3000 ? 1.0 : 0.05);
		new File(adresar, "tmp").mkdirs();
		pracovni.mkdirs();
		final String mapy = "smoke.nazev=" + SmokeScenar.MAPA + "\n" //
				+ "smoke.url=" + server.getUrl() + "\n" //
				+ "smoke.max=18\n" //
				+ "smoke.hromadne=ano\n";
		Files.write(new File(adresar, "data/uzivatelske-mapy.properties").toPath(), mapy.getBytes(StandardCharsets.UTF_8));
		// Hotové nastavení: program pak nepřebírá nastavení z Java Preferences, ve Windows z registru uživatele, který test spustil.
		Files.write(new File(adresar, "data/nastaveni.xml").toPath(), ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
				+ "<!DOCTYPE preferences SYSTEM \"http://java.sun.com/dtd/preferences.dtd\">\n"
				+ "<preferences EXTERNAL_XML_VERSION=\"1.0\"><root type=\"user\"><map/><node name=\"geokuk\"><map/><node name=\"current\"><map/><node name=\"vseobecne\"><map>"
				+ "<entry key=\"nextUpdateCheckTimestamp\" value=\"9223372036854775807\"/></map></node></node></node></root></preferences>\n").getBytes(StandardCharsets.UTF_8));
		Files.copy(new File(System.getProperty("smoke.jar")).toPath(), new File(pracovni, "geokuk.jar").toPath());
		return adresar;
	}

	private List<String> jvm(final File adresar) {
		final List<String> prikaz = new ArrayList<>();
		prikaz.add(new File(System.getProperty("java.home"), "bin/java").getPath());
		prikaz.add("-Xmx768m");
		prikaz.add("-Duser.home=" + new File(adresar, "home"));
		prikaz.add("-Djava.io.tmpdir=" + new File(adresar, "tmp"));
		prikaz.add("-Djava.util.prefs.userRoot=" + new File(adresar, "prefs"));
		prikaz.add("-Dgeokuk.data=" + new File(adresar, "data"));
		// Vestavěné mapy (Mapy.cz po http) jdou přes falešný server jako proxy.
		prikaz.add("-Dhttp.proxyHost=127.0.0.1");
		prikaz.add("-Dhttp.proxyPort=" + proxyPort);
		prikaz.add("-Dhttp.nonProxyHosts=localhost|127.*");
		prikaz.add("-Dfile.encoding=UTF-8");
		prikaz.addAll(vlastnosti);
		return prikaz;
	}

	private Properties spust(final File adresar, final String beh, final String kroky) throws Exception {
		final File zprava = new File(adresar, beh + ".properties");
		final List<String> prikaz = new ArrayList<>(jvm(adresar));
		// Program běží ze sestaveného jaru v pracovním adresáři jako z přenosné složky.
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
		if (zprava.getProperty("edt.nejdelsiMs") == null) {
			problemy.add("Zpráva nemá edt.nejdelsiMs, hlídač EDT neběžel");
		}
		zkontrolujEdt(zprava, "edt.nejdelsiMs", EDT_LIMIT_MS, "", problemy);
		// Start programu (okno, načtení keší, první vykreslení) běží na EDT naráz a na pomalém stroji trvá déle.
		zkontrolujEdt(zprava, "edt.startMs", EDT_LIMIT_START_MS, "při startu: ", problemy);
		final long pamet = Long.parseLong(zprava.getProperty("pamet.mb", "0"));
		if (pamet > 400) {
			problemy.add("Po scénáři zůstalo obsazeno " + pamet + " MB paměti");
		}
		final File excrep = new File(adresar, "data/log/chyby");
		final String[] vypisy = excrep.list();
		if (bezVypisu && vypisy != null && vypisy.length > 0) {
			problemy.add("Výpisy chyb v " + excrep + ": " + Arrays.toString(vypisy));
		}
		final List<String> mimo = zapsanoMimo(adresar);
		if (!mimo.isEmpty()) {
			problemy.add("Zapsáno mimo složku programu: " + mimo);
		}
		assertTrue(String.join("\n", problemy), problemy.isEmpty());
	}

	private static void zkontrolujEdt(final Properties zprava, final String klic, final long limitMs, final String druh, final List<String> problemy) {
		final long edt = Long.parseLong(zprava.getProperty(klic, "0"));
		if (edt > limitMs) {
			final String nejpomalejsi = zprava.stringPropertyNames().stream().filter(k -> k.startsWith("edt.pomala.")).map(zprava::getProperty)
					.filter(p -> p.startsWith(edt + " ms: " + druh)).findFirst().orElse(zprava.getProperty("edt.pomala.0"));
			problemy.add("Událost na EDT " + druh + "trvala " + edt + " ms (limit " + limitMs + " ms): " + nejpomalejsi);
		}
	}

	/**
	 * Soubory v domovské složce a v úložišti Java Preferences. Povolené je jen to, co zapisuje Java sama: cache fontů a zámky Preferences při
	 * převzetí starého nastavení, a data, která tam připravil test.
	 */
	private static List<String> zapsanoMimo(final File adresar) {
		final List<String> povolene = Arrays.asList("home/.java/fonts/", "home/geoget/", "prefs/.java/.userPrefs/.userRootModFile.", "prefs/.java/.userPrefs/.user.lock.",
				"prefs/.java/.userPrefs/geokuk/current/vseobecne/prefs.xml");
		final List<String> mimo = new ArrayList<>();
		for (final String koren : Arrays.asList("home", "prefs")) {
			final File slozka = new File(adresar, koren);
			if (!slozka.isDirectory()) {
				continue;
			}
			try (java.util.stream.Stream<java.nio.file.Path> soubory = Files.walk(slozka.toPath())) {
				soubory.filter(Files::isRegularFile).map(f -> adresar.toPath().relativize(f).toString().replace(File.separatorChar, '/'))
						.filter(f -> povolene.stream().noneMatch(f::startsWith)).forEach(mimo::add);
			} catch (final IOException e) {
				mimo.add(slozka + ": " + e);
			}
		}
		return mimo;
	}

	/** Počet dlaždic v cache po ukončení programu. */
	private static long dlazdicVCache(final File adresar) throws Exception {
		try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + new File(adresar, "data/cache/tiles.sqlite"))) {
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
