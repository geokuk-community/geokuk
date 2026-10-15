package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import cz.geokuk.framework.Event0;
import cz.geokuk.plugins.mapy.sprava.StavMapy.Faze;
import cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni;

/** Stahování vrstvy o více souborech proti lokálnímu HTTP serveru: vše, nebo nic; pokračování; kontrolní součet; poškozená mapa. */
public class StahovacVrstevTest {

	private static final String ETAG = "\"v-1\"";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private HttpServer server;
	private File slozka;
	private StahovacVrstev stahovac;
	/** Soubory, které server posílá, podle jména. */
	private final Map<String, byte[]> data = new ConcurrentHashMap<>();
	private final Set<String> chybi = ConcurrentHashMap.newKeySet();
	private final List<String> pozadavky = new CopyOnWriteArrayList<>();
	private volatile String zdrzetSoubor;
	private volatile CountDownLatch zdrzet;
	private final CountDownLatch zdrzeno = new CountDownLatch(1);

	@Before
	public void setUp() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/rel/", this::obsluz);
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		slozka = new File(tmp.getRoot(), "offline-mapy/vrstvy");
		final StahovacMapy http = new StahovacMapy("GeoKuk/test (https://example.org)", () -> {
		});
		http.casovyLimitMs = 10_000;
		http.pauzaPresunuMs = 10;
		stahovac = new StahovacVrstev(http);
	}

	@After
	public void tearDown() {
		final CountDownLatch z = zdrzet;
		if (z != null) {
			z.countDown();
		}
		server.stop(0);
	}

	private void obsluz(final HttpExchange ex) throws IOException {
		final String jmeno = ex.getRequestURI().getPath().replace("/rel/", "");
		final String range = ex.getRequestHeaders().getFirst("Range");
		pozadavky.add(jmeno + (range == null ? "" : " " + range));
		try {
			final byte[] d = data.get(jmeno);
			if (d == null || chybi.contains(jmeno)) {
				ex.sendResponseHeaders(404, -1);
				return;
			}
			ex.getResponseHeaders().add("ETag", ETAG);
			int od = 0;
			final String ifRange = ex.getRequestHeaders().getFirst("If-Range");
			if (range != null && ETAG.equals(ifRange)) {
				try {
					od = Integer.parseInt(range.replaceAll("bytes=(\\d+)-", "$1"));
				} catch (final NumberFormatException e) {
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
				if (jmeno.equals(zdrzetSoubor) && i - od == d.length / 2) {
					out.flush();
					zdrzeno.countDown();
					zdrzet.await(20, TimeUnit.SECONDS);
				}
				out.write(d[i]);
			}
			out.close();
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (final IOException e) {
			// zrušené spojení
		} finally {
			ex.close();
		}
	}

	private static byte[] nahodna(final int delka, final long seed) {
		final byte[] b = new byte[delka];
		new Random(seed).nextBytes(b);
		return b;
	}

	private static String sha(final byte[] b) throws IOException {
		final File f = File.createTempFile("sha", ".bin");
		try {
			Files.write(f.toPath(), b);
			return StahovacVrstev.sha256(f, () -> false);
		} finally {
			Files.delete(f.toPath());
		}
	}

	/** Vrstva ze souborů, které server posílá; {@code shaPodvrh} přepíše kontrolní součet souboru v katalogu. */
	private KatalogovaVrstva vrstva(final String format, final Map<String, String> shaPodvrh, final String... soubory) throws IOException {
		final StringBuilder sb = new StringBuilder();
		for (final String s : soubory) {
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(KatalogVrstevTest.soubor(s, s, data.get(s).length, shaPodvrh.getOrDefault(s, sha(data.get(s)))));
		}
		final String json = "{\"format\": 1, \"zdroje\": [\"http://127.0.0.1:" + server.getAddress().getPort() + "/rel/\"], \"vrstvy\": ["
				+ KatalogVrstevTest.vrstva("dem/europe/czech-republic", "dem", "europe/czech-republic", format, sb.toString(), "") + "]}";
		return KatalogVrstev.nacti(json, KatalogVrstevTest.mapy()).getVrstvy().get(0);
	}

	private KatalogovaVrstva dvaSoubory() throws IOException {
		data.put("cz_1.zip", nahodna(300_000, 1));
		data.put("cz_2.zip", nahodna(200_000, 2));
		return vrstva("hgt-zip", Collections.emptyMap(), "cz_1.zip", "cz_2.zip");
	}

	private StavVrstvy stahni(final KatalogovaVrstva v, final AtomicBoolean zruseno) {
		return stahovac.stahni(StavVrstvy.novy(v), slozka, zruseno::get, s -> {
		});
	}

	private List<String> vSlozce() {
		final String[] jmena = slozka.list();
		final List<String> l = jmena == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(jmena));
		Collections.sort(l);
		return l;
	}

	@Test
	public void celaVrstvaDoNoveSlozky() throws IOException {
		final KatalogovaVrstva v = dvaSoubory();
		assertEquals(Mistni.NEMAME, StahovacVrstev.mistniStav(StavVrstvy.novy(v), slozka).getMistni());
		final StavVrstvy s = stahni(v, new AtomicBoolean());
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertEquals(Mistni.MAME, s.getMistni());
		assertEquals(500_000, s.getStazeno());
		assertFalse(s.isRozpracovano());
		assertEquals(Arrays.asList("cz_1.zip", "cz_2.zip"), vSlozce());
		assertArrayEquals(data.get("cz_2.zip"), Files.readAllBytes(new File(slozka, "cz_2.zip").toPath()));

		pozadavky.clear();
		assertEquals("stažená vrstva se znovu nestahuje", Faze.HOTOVO, stahni(v, new AtomicBoolean()).getFaze());
		assertEquals(Collections.emptyList(), pozadavky);
	}

	@Test
	public void spatnySoucetNicNepresune() throws IOException {
		data.put("cz_1.zip", nahodna(300_000, 1));
		data.put("cz_2.zip", nahodna(200_000, 2));
		final KatalogovaVrstva v = vrstva("hgt-zip", Collections.singletonMap("cz_2.zip", KatalogVrstevTest.SHA), "cz_1.zip", "cz_2.zip");
		final StavVrstvy s = stahni(v, new AtomicBoolean());
		assertEquals(s.toString(), Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("cz_2.zip je poškozený"));
		assertEquals(Mistni.NEMAME, s.getMistni());
		assertEquals("ověřený první soubor čeká, vadný je pryč", Arrays.asList("cz_1.zip.stahuje", "cz_1.zip.stahuje.info"), vSlozce());
	}

	@Test
	public void chybejiciSouborNaServeruNicNepresune() throws IOException {
		final KatalogovaVrstva v = dvaSoubory();
		chybi.add("cz_2.zip");
		final StavVrstvy s = stahni(v, new AtomicBoolean());
		assertEquals(s.toString(), Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("na serveru není"));
		assertFalse(new File(slozka, "cz_1.zip").exists());

		chybi.clear();
		pozadavky.clear();
		assertEquals(Faze.HOTOVO, stahni(v, new AtomicBoolean()).getFaze());
		assertEquals("celý první soubor se znovu nestahuje", Collections.singletonList("cz_2.zip"), pozadavky);
	}

	@Test
	public void zruseniAPokracovani() throws Exception {
		final KatalogovaVrstva v = dvaSoubory();
		zdrzet = new CountDownLatch(1);
		zdrzetSoubor = "cz_2.zip";
		final AtomicBoolean zruseno = new AtomicBoolean();
		final ExecutorService e = Executors.newSingleThreadExecutor();
		try {
			final Future<StavVrstvy> f = e.submit(() -> stahni(v, zruseno));
			assertTrue(zdrzeno.await(20, TimeUnit.SECONDS));
			zruseno.set(true);
			zdrzet.countDown();
			final StavVrstvy s = f.get(20, TimeUnit.SECONDS);
			assertEquals(s.toString(), Faze.ZRUSENO, s.getFaze());
			assertTrue(s.isRozpracovano());
			assertFalse("nic není na místě", new File(slozka, "cz_1.zip").exists());
		} finally {
			e.shutdownNow();
		}
		final StavVrstvy zDisku = StahovacVrstev.mistniStav(StavVrstvy.novy(v), slozka);
		assertTrue(zDisku.isRozpracovano());
		assertEquals(Mistni.NEMAME, zDisku.getMistni());
		final long mame = new File(slozka, "cz_2.zip.stahuje").length();
		assertTrue("půlka druhého souboru: " + mame, mame > 0 && mame < 200_000);
		assertEquals(300_000 + mame, zDisku.getStazeno());

		zdrzetSoubor = null;
		pozadavky.clear();
		final StavVrstvy s = stahni(v, new AtomicBoolean());
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertEquals("navazuje druhý soubor", Collections.singletonList("cz_2.zip bytes=" + mame + "-"), pozadavky);
		assertArrayEquals(data.get("cz_2.zip"), Files.readAllBytes(new File(slozka, "cz_2.zip").toPath()));
		assertEquals(Arrays.asList("cz_1.zip", "cz_2.zip"), vSlozce());
	}

	@Test
	public void jinaVerzeSeNahradi() throws IOException {
		final KatalogovaVrstva v = dvaSoubory();
		assertTrue(slozka.mkdirs());
		Files.write(new File(slozka, "cz_1.zip").toPath(), new byte[10]);
		assertEquals(Mistni.JINA_VERZE, StahovacVrstev.mistniStav(StavVrstvy.novy(v), slozka).getMistni());
		final StavVrstvy s = stahni(v, new AtomicBoolean());
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertArrayEquals(data.get("cz_1.zip"), Files.readAllBytes(new File(slozka, "cz_1.zip").toPath()));
	}

	@Test
	public void mapaSeOveriHlavickou() throws IOException {
		data.put("cz_trasy.map", StahovacMapyTest.kukov());
		assertEquals(Faze.HOTOVO, stahni(vrstva("map", Collections.emptyMap(), "cz_trasy.map"), new AtomicBoolean()).getFaze());

		final byte[] vadna = StahovacMapyTest.kukov();
		vadna[0] = 'X';
		data.put("vadna.map", vadna);
		final StavVrstvy s = stahni(vrstva("map", Collections.emptyMap(), "vadna.map"), new AtomicBoolean());
		assertEquals("součet sedí, ale mapa je vadná: " + s, Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("poškozená"));
		assertFalse(new File(slozka, "vadna.map").exists());
	}

	@Test
	public void maloMista() throws IOException {
		final KatalogovaVrstva v = dvaSoubory();
		stahovac.volneMisto = 1000;
		final StavVrstvy s = stahni(v, new AtomicBoolean());
		assertEquals(Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("málo místa"));
		assertEquals(Collections.emptyList(), pozadavky);
	}

	@Test
	public void modelStahujeVrstvuAMapuNesoubezne() throws Exception {
		final KatalogovaVrstva v = dvaSoubory();
		final KatalogVrstev katalog = KatalogVrstev.nacti("{\"format\": 1, \"zdroje\": [\"http://127.0.0.1:" + server.getAddress().getPort()
				+ "/rel/\"], \"vrstvy\": [" + KatalogVrstevTest.vrstva(v.getId(), "dem", v.getRegion(), "hgt-zip", KatalogVrstevTest.soubor("cz_1.zip", "cz_1.zip",
						300_000, v.getSoubory().get(0).getSha256()) + "," + KatalogVrstevTest.soubor("cz_2.zip", "cz_2.zip", 200_000, v.getSoubory().get(1).getSha256()), "")
				+ "]}", KatalogVrstevTest.mapy());
		final File mapy = new File(tmp.getRoot(), "offline-mapy");
		final SpravaMapModel model = new SpravaMapModel(KatalogVrstevTest.mapy(), katalog);
		final List<Event0<SpravaMapModel>> udalosti = new CopyOnWriteArrayList<>();
		model.nastavProTest(() -> mapy, udalosti::add);
		model.getStahovac().casovyLimitMs = 10_000;
		final KatalogovaVrstva vm = katalog.getVrstvy().get(0);
		assertEquals(slozka, model.getSlozkaVrstev());
		zdrzet = new CountDownLatch(1);
		zdrzetSoubor = "cz_1.zip";
		assertTrue(model.stahni(vm));
		assertTrue(zdrzeno.await(20, TimeUnit.SECONDS));
		assertFalse("mapa se nestahuje souběžně s vrstvou", model.stahni(model.getKatalog().getMapy().get(0)));
		assertFalse(model.stahni(vm));
		assertTrue(model.getBeziciStahovaniVrstvy().isPresent());
		assertFalse(model.getBeziciStahovani().isPresent());
		zdrzet.countDown();
		model.pockej();
		assertEquals(Faze.HOTOVO, model.getStav(vm).getFaze());
		assertEquals(Mistni.MAME, model.getStav(vm).getMistni());
		final Event0<SpravaMapModel> posledni = udalosti.get(udalosti.size() - 1);
		assertTrue(posledni instanceof SpravaVrstevEvent);
		assertSame(vm, ((SpravaVrstevEvent) posledni).getVrstva());
		assertFalse(model.getBeziciStahovaniVrstvy().isPresent());

		udalosti.clear();
		model.obnovStavy();
		model.pockej();
		assertEquals("stav z disku se nezměnil, bez události", Collections.emptyList(), udalosti);
		assertEquals(Mistni.MAME, model.getStav(vm).getMistni());
	}
}
