package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import cz.geokuk.plugins.mapy.sprava.StavMapy.Faze;
import cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni;
import cz.geokuk.plugins.mapy.sprava.StavMapy.Server;

/** Stahování mapy proti lokálnímu HTTP serveru: celé, pokračování, přerušení, zrušení, vadná data a odpovědi serveru. */
public class StahovacMapyTest {

	private static final String LM = "Thu, 15 Oct 2026 10:00:00 GMT";
	private static final String ETAG = "\"abc-1\"";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private HttpServer server;
	private File slozka;
	private KatalogovaMapa mapa;
	private StahovacMapy stahovac;
	private final List<StavMapy> zmeny = new CopyOnWriteArrayList<>();
	private final AtomicInteger uvolneni = new AtomicInteger();

	/** Data, která server posílá, a jeho chování. */
	private volatile byte[] data;
	private volatile String etag = ETAG;
	private volatile String lm = LM;
	private volatile int vynucenyKod;
	private volatile int useknoutPo = -1;
	private volatile CountDownLatch zdrzet;
	private volatile int zdrzetPo;
	/** Posílá data po kouscích s pauzou, jako pomalé spojení. */
	private volatile boolean pomalu;
	private final List<Map<String, String>> pozadavky = new CopyOnWriteArrayList<>();

	static byte[] kukov() throws IOException {
		try (InputStream in = StahovacMapyTest.class.getResourceAsStream("/offline-mapy/kukov.map")) {
			return com.google.common.io.ByteStreams.toByteArray(in);
		}
	}

	@Before
	public void setUp() throws Exception {
		data = kukov();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/maps/v5/", this::obsluz);
		server.createContext("/vypadek/", ex -> {
			pozadavky.add(Collections.singletonMap("metoda", "vypadek " + ex.getRequestMethod()));
			ex.sendResponseHeaders(503, -1);
			ex.close();
		});
		server.createContext("/zrcadlo/", ex -> {
			ex.getResponseHeaders().add("Location", ex.getRequestURI().getPath().replace("/zrcadlo/", "/maps/v5/"));
			ex.sendResponseHeaders(302, -1);
			ex.close();
		});
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		slozka = tmp.newFolder("offline-mapy");
		mapa = katalog(server).getMapy().get(0);
		stahovac = new StahovacMapy("GeoKuk/test (https://example.org)", uvolneni::incrementAndGet);
		stahovac.casovyLimitMs = 10_000;
		stahovac.pauzaPresunuMs = 10;
	}

	@After
	public void tearDown() {
		final CountDownLatch z = zdrzet;
		if (z != null) {
			z.countDown();
		}
		server.stop(0);
	}

	static KatalogMap katalog(final HttpServer server) throws IOException {
		final String zaklad = "http://127.0.0.1:" + server.getAddress().getPort();
		return KatalogMap.nacti("{\"zdroj\": \"test\", \"licence\": \"ODbL 1.0\", \"atribuce\": \"© OSM\", \"mapy\": \"" + zaklad + "/maps/v5/\", \"polygony\": \"" + zaklad
				+ "/maps/poly/\", \"polozky\": [{\"id\": \"europe/kukov\", \"nazev\": \"Kukov\", \"velikost\": 2615, \"obdelnik\": [49, 14, 51, 15]}]}", id -> null);
	}

	private void obsluz(final HttpExchange ex) throws IOException {
		final Map<String, String> z = new HashMap<>();
		z.put("metoda", ex.getRequestMethod());
		ex.getRequestHeaders().forEach((k, v) -> z.put(k.toLowerCase(Locale.ROOT), v.get(0)));
		pozadavky.add(z);
		try {
			final byte[] d = data;
			if (vynucenyKod != 0) {
				ex.sendResponseHeaders(vynucenyKod, -1);
				return;
			}
			ex.getResponseHeaders().add("Last-Modified", lm);
			ex.getResponseHeaders().add("ETag", etag);
			ex.getResponseHeaders().add("Accept-Ranges", "bytes");
			if ("HEAD".equals(ex.getRequestMethod())) {
				final String inm = z.get("if-none-match");
				if (inm != null && inm.equals(etag)) {
					ex.sendResponseHeaders(304, -1);
					return;
				}
				ex.getResponseHeaders().add("Content-Length", Integer.toString(d.length));
				ex.sendResponseHeaders(200, -1);
				return;
			}
			int od = 0;
			final String range = z.get("range");
			final String ifRange = z.get("if-range");
			if (range != null && (ifRange == null || ifRange.equals(etag))) {
				od = Integer.parseInt(range.replaceAll("bytes=(\\d+)-", "$1"));
				if (od >= d.length) {
					ex.sendResponseHeaders(416, -1);
					return;
				}
				ex.getResponseHeaders().add("Content-Range", "bytes " + od + "-" + (d.length - 1) + "/" + d.length);
				ex.sendResponseHeaders(206, d.length - od);
			} else {
				ex.sendResponseHeaders(200, d.length);
			}
			final OutputStream out = ex.getResponseBody();
			for (int i = od; i < d.length; i++) {
				if (i - od == useknoutPo) {
					out.flush();
					throw new IOException("useknuto");
				}
				if (zdrzet != null && i - od == zdrzetPo) {
					out.flush();
					zdrzet.await(20, TimeUnit.SECONDS);
				}
				if (pomalu && (i - od) % 4096 == 0) {
					out.flush();
					Thread.sleep(5);
				}
				out.write(d[i]);
			}
			out.close();
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (final IOException e) {
			// useknuté nebo zrušené spojení
		} finally {
			ex.close();
		}
	}

	private StavMapy stahni() {
		return stahovac.stahni(StavMapy.novy(mapa), slozka, () -> false, zmeny::add);
	}

	private File cil() {
		return new File(slozka, "kukov.map");
	}

	private File cast() {
		return new File(slozka, "kukov.map" + StahovacMapy.PRIPONA);
	}

	@Test
	public void celeStazeni() throws IOException {
		final StavMapy s = stahni();
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertNull(s.getChyba());
		assertEquals(Mistni.MAME, s.getMistni());
		assertEquals(Server.AKTUALNI, s.getServer());
		assertArrayEquals(data, Files.readAllBytes(cil().toPath()));
		assertFalse(cast().exists());
		assertFalse(StahovacMapy.info(slozka, mapa).exists());
		assertEquals(data.length, s.getStazeno());
		assertEquals(data.length, s.getCelkem());
		assertFalse(s.isRozpracovano());
		assertNotNull(s.getDatumDatMistni());
		final Map<String, String> z = pozadavky.get(0);
		assertEquals("GET", z.get("metoda"));
		assertEquals("GeoKuk/test (https://example.org)", z.get("user-agent"));
		assertNull(z.get("range"));
		assertEquals(Faze.STAHUJE, zmeny.get(0).getFaze());
		assertTrue(zmeny.stream().anyMatch(x -> x.getFaze() == Faze.OVERUJE));
		assertEquals("nová mapa nic nenahrazuje", 0, uvolneni.get());
	}

	@Test
	public void staraMapaSeNahradiAzPoOvereni() throws IOException {
		Files.write(cil().toPath(), "stará".getBytes("UTF-8"));
		data = Arrays.copyOf(data, data.length - 10);
		StavMapy s = stahni();
		assertEquals(Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("poškozená"));
		assertEquals("stará", new String(Files.readAllBytes(cil().toPath()), "UTF-8"));
		assertFalse(cast().exists());

		data = kukov();
		s = stahni();
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertArrayEquals(data, Files.readAllBytes(cil().toPath()));
		assertTrue("otevřená stará mapa se před nahrazením uvolní", uvolneni.get() >= 1);
	}

	@Test
	public void otevrenaStaraMapaSeNapredUvolni() throws IOException {
		Files.write(cil().toPath(), "stará".getBytes("UTF-8"));
		final RandomAccessFile otevrena = new RandomAccessFile(cil(), "r");
		stahovac = new StahovacMapy("GeoKuk/test", () -> {
			try {
				otevrena.close();
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		final StavMapy s = stahni();
		otevrena.close();
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertArrayEquals(data, Files.readAllBytes(cil().toPath()));
	}

	@Test
	public void prerusenePokracujeOdMista() throws IOException {
		useknoutPo = 1000;
		StavMapy s = stahni();
		assertEquals(s.toString(), Faze.CHYBA, s.getFaze());
		assertTrue(s.isRozpracovano());
		assertFalse(cil().exists());
		assertEquals(1000, cast().length());

		final StavMapy zDisku = StahovacMapy.mistniStav(StavMapy.novy(mapa), slozka);
		assertTrue(zDisku.isRozpracovano());
		assertEquals(1000, zDisku.getStazeno());
		assertEquals(data.length, zDisku.getCelkem());

		useknoutPo = -1;
		s = stahni();
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertArrayEquals(data, Files.readAllBytes(cil().toPath()));
		final Map<String, String> z = pozadavky.get(pozadavky.size() - 1);
		assertEquals("bytes=1000-", z.get("range"));
		assertEquals(ETAG, z.get("if-range"));
	}

	@Test
	public void presmerovaniNaZrcadlo() throws IOException {
		final String zaklad = "http://127.0.0.1:" + server.getAddress().getPort();
		mapa = KatalogMap.nacti("{\"zdroj\": \"t\", \"licence\": \"l\", \"atribuce\": \"a\", \"mapy\": \"" + zaklad + "/zrcadlo/\", \"polygony\": \"" + zaklad
				+ "/p/\", \"polozky\": [{\"id\": \"europe/kukov\", \"nazev\": \"K\", \"velikost\": 1, \"obdelnik\": [49, 14, 51, 15]}]}", id -> null).getMapy().get(0);
		final StavMapy k = stahovac.zkontroluj(StavMapy.novy(mapa), slozka, zmeny::add);
		assertEquals(data.length, k.getVelikostNaServeru());
		final StavMapy s = stahni();
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertArrayEquals(data, Files.readAllBytes(cil().toPath()));
	}

	@Test
	public void nedostupneZrcadloZkusiZalohu() throws IOException {
		final String zaklad = "http://127.0.0.1:" + server.getAddress().getPort();
		mapa = KatalogMap.nacti("{\"zdroj\": \"t\", \"licence\": \"l\", \"atribuce\": \"a\", \"mapy\": [\"" + zaklad + "/vypadek/\", \"" + zaklad
				+ "/maps/v5/\"], \"polygony\": \"" + zaklad + "/p/\", \"polozky\": [{\"id\": \"europe/kukov\", \"nazev\": \"K\", \"velikost\": 1, \"obdelnik\": [49, 14, 51, 15]}]}",
				id -> null).getMapy().get(0);
		final StavMapy k = stahovac.zkontroluj(StavMapy.novy(mapa), slozka, zmeny::add);
		assertEquals(data.length, k.getVelikostNaServeru());
		useknoutPo = 1000;
		StavMapy s = stahni();
		assertEquals(Faze.CHYBA, s.getFaze());
		assertEquals(zaklad + "/maps/v5/europe/kukov.map", StahovacMapy.nactiInfo(slozka, mapa).url);
		useknoutPo = -1;
		pozadavky.clear();
		s = stahni();
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertEquals("navazuje se rovnou u serveru s rozpracovanou částí", "bytes=1000-", pozadavky.get(0).get("range"));
		assertArrayEquals(data, Files.readAllBytes(cil().toPath()));
	}

	@Test
	public void bezValidatoruSeNenavazuje() throws IOException {
		Files.write(cast().toPath(), Arrays.copyOf(data, 1000));
		Files.write(StahovacMapy.info(slozka, mapa).toPath(), ("url=" + mapa.getUrlMapy() + "\ncelkem=" + data.length + "\n").getBytes("UTF-8"));
		final StavMapy s = stahni();
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertNull(pozadavky.get(0).get("range"));
		assertArrayEquals(data, Files.readAllBytes(cil().toPath()));
	}

	@Test
	public void zmenenaMapaNaServeruZacneZnovu() throws IOException {
		useknoutPo = 1000;
		stahni();
		assertEquals(1000, cast().length());
		// Na serveru je nová verze: If-Range nesedí, server pošle celý soubor.
		useknoutPo = -1;
		etag = "\"abc-2\"";
		final StavMapy s = stahni();
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertArrayEquals(data, Files.readAllBytes(cil().toPath()));
	}

	@Test
	public void dokoncenyRozpracovanySeJenOveriAPresune() throws IOException {
		Files.write(cast().toPath(), data);
		Files.write(StahovacMapy.info(slozka, mapa).toPath(), ("url=" + mapa.getUrlMapy() + "\ncelkem=" + data.length + "\n").getBytes("UTF-8"));
		final StavMapy s = stahni();
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertTrue("bez sítě", pozadavky.isEmpty());
		assertArrayEquals(data, Files.readAllBytes(cil().toPath()));
	}

	@Test
	public void rozpracovanyBezUdajuZacneOdNuly() throws IOException {
		Files.write(cast().toPath(), new byte[] { 1, 2, 3 });
		final StavMapy s = stahni();
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertNull(pozadavky.get(0).get("range"));
		assertArrayEquals(data, Files.readAllBytes(cil().toPath()));
	}

	@Test
	public void poskozenaHlavicka() throws IOException {
		data[3] = 'X';
		final StavMapy s = stahni();
		assertEquals(Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("poškozená"));
		assertFalse(cil().exists());
		assertFalse(cast().exists());
		assertFalse(StahovacMapy.info(slozka, mapa).exists());
		assertFalse(s.isRozpracovano());
	}

	@Test
	public void spatnaVelikostProtiHlavicce() throws IOException {
		data = Arrays.copyOf(data, data.length + 100);
		final StavMapy s = stahni();
		assertEquals(Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("neúplná"));
		assertFalse(cil().exists());
	}

	@Test
	public void velikostProtiServeru() throws IOException {
		Files.write(cast().toPath(), Arrays.copyOf(data, data.length + 5));
		Files.write(StahovacMapy.info(slozka, mapa).toPath(), ("url=" + mapa.getUrlMapy() + "\ncelkem=" + data.length + "\n").getBytes("UTF-8"));
		final StavMapy s = stahni();
		assertEquals(Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("jinou velikost"));
		assertFalse(cil().exists());
		assertFalse(cast().exists());
	}

	@Test
	public void nenaNaServeru() {
		vynucenyKod = 404;
		final StavMapy s = stahni();
		assertEquals(Faze.CHYBA, s.getFaze());
		assertEquals(Server.CHYBI, s.getServer());
		assertEquals("Mapa na serveru není.", s.getChyba());
		assertFalse(cil().exists());
		assertFalse(cast().exists());
	}

	@Test
	public void chybaServeru() {
		vynucenyKod = 503;
		final StavMapy s = stahni();
		assertEquals(Faze.CHYBA, s.getFaze());
		assertEquals(Server.NEDOSTUPNY, s.getServer());
		assertTrue(s.getChyba().contains("503"));
	}

	@Test
	public void serverNedostupny() {
		server.stop(0);
		final StavMapy s = stahni();
		assertEquals(Faze.CHYBA, s.getFaze());
		assertNotNull(s.getChyba());
		assertFalse(cil().exists());
	}

	@Test
	public void maloMista() {
		stahovac.volneMisto = 1000;
		final StavMapy s = stahni();
		assertEquals(Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("málo místa"));
		assertFalse(cast().exists());
	}

	@Test
	public void zruseni() throws Exception {
		data = new byte[8 * 1024 * 1024];
		pomalu = true;
		final AtomicBoolean zruseno = new AtomicBoolean();
		final CountDownLatch prvniData = new CountDownLatch(1);
		final ExecutorService e = Executors.newSingleThreadExecutor();
		try {
			final Future<StavMapy> f = e.submit(() -> stahovac.stahni(StavMapy.novy(mapa), slozka, zruseno::get, x -> {
				if (x.getStazeno() > 0) {
					prvniData.countDown();
				}
			}));
			assertTrue(prvniData.await(10, TimeUnit.SECONDS));
			zruseno.set(true);
			final StavMapy s = f.get(5, TimeUnit.SECONDS);
			assertEquals(s.toString(), Faze.ZRUSENO, s.getFaze());
			assertNull(s.getChyba());
			assertFalse(cil().exists());
			assertTrue(cast().exists());
			assertTrue(cast().length() < data.length);
			assertTrue(StahovacMapy.mistniStav(StavMapy.novy(mapa), slozka).isRozpracovano());
		} finally {
			e.shutdownNow();
		}
	}

	@Test
	public void zaseknutyServerPoLimitu() {
		stahovac.casovyLimitMs = 500;
		zdrzet = new CountDownLatch(1);
		zdrzetPo = 500;
		final StavMapy s = stahni();
		assertEquals(s.toString(), Faze.CHYBA, s.getFaze());
		assertTrue(s.isRozpracovano());
		assertEquals(500, cast().length());
	}

	@Test
	public void zruseniMeziDaty() {
		final StavMapy s = stahovac.stahni(StavMapy.novy(mapa), slozka, () -> true, zmeny::add);
		assertEquals(s.toString(), Faze.ZRUSENO, s.getFaze());
		assertFalse(cil().exists());
	}

	@Test
	public void kontrolaHeadAPodminena() throws IOException {
		StavMapy s = stahovac.zkontroluj(StavMapy.novy(mapa), slozka, zmeny::add);
		assertEquals(s.toString(), Faze.NIC, s.getFaze());
		assertEquals(Mistni.NEMAME, s.getMistni());
		assertEquals(data.length, s.getVelikostNaServeru());
		assertNotNull(s.getDatumNaServeru());
		assertNotNull(s.getZkontrolovano());
		assertEquals("HEAD", pozadavky.get(0).get("metoda"));
		assertNull(pozadavky.get(0).get("if-none-match"));
		assertTrue("kontrola nic nestahuje", slozka.list().length == 0);

		// Máme stejnou mapu → aktuální; podruhé podmíněně (304).
		Files.write(cil().toPath(), data);
		s = stahovac.zkontroluj(s, slozka, zmeny::add);
		assertEquals(Server.AKTUALNI, s.getServer());
		assertEquals(ETAG, pozadavky.get(1).get("if-none-match"));
		assertEquals(data.length, s.getVelikostNaServeru());

		// Na serveru je jiná (větší) mapa novější než data na disku.
		etag = "\"abc-2\"";
		data = Arrays.copyOf(data, data.length + 1);
		s = stahovac.zkontroluj(s, slozka, zmeny::add);
		assertEquals(Server.NOVEJSI, s.getServer());
		assertEquals(Mistni.MAME, s.getMistni());

		// Jiná mapa, ale starší než data na disku → server nic novějšího nemá.
		etag = "\"abc-3\"";
		lm = "Fri, 21 Aug 2026 10:00:00 GMT";
		s = stahovac.zkontroluj(s, slozka, zmeny::add);
		assertEquals(Server.AKTUALNI, s.getServer());
	}

	@Test
	public void kontrolaChybi() {
		vynucenyKod = 404;
		final StavMapy s = stahovac.zkontroluj(StavMapy.novy(mapa), slozka, zmeny::add);
		assertEquals(Server.CHYBI, s.getServer());
		assertEquals(Faze.CHYBA, s.getFaze());
		server.stop(0);
		final StavMapy n = stahovac.zkontroluj(StavMapy.novy(mapa), slozka, zmeny::add);
		assertEquals(Server.NEDOSTUPNY, n.getServer());
	}

	@Test
	public void mistniStavPoskozene() throws IOException {
		Files.write(cil().toPath(), new byte[] { 1, 2, 3 });
		assertEquals(Mistni.POSKOZENA, StahovacMapy.mistniStav(StavMapy.novy(mapa), slozka).getMistni());
	}

	@Test
	public void zahozeni() throws IOException {
		useknoutPo = 1000;
		stahni();
		assertTrue(cast().exists());
		StahovacMapy.zahod(slozka, mapa);
		assertFalse(cast().exists());
		assertFalse(StahovacMapy.info(slozka, mapa).exists());
	}

	@Test
	public void nezapisovatelnaSlozka() {
		final StavMapy s = stahovac.stahni(StavMapy.novy(mapa), new File(slozka, "neni"), () -> false, zmeny::add);
		assertEquals(Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("nejde zapisovat"));
		assertTrue(pozadavky.isEmpty());
	}
}
