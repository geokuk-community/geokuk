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
		slozka = new File(tmp.getRoot(), "offline-mapy/layers");
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
				if (zdrzetSoubor != null && jmeno.startsWith(zdrzetSoubor) && i - od == d.length / 2) {
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
		final String json = "{\"version\": 1, \"sources\": [\"http://127.0.0.1:" + server.getAddress().getPort() + "/rel/\"], \"layers\": ["
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

	private static int indexZmeny(final List<StavVrstvy> zmeny, final java.util.function.Predicate<StavVrstvy> p) {
		for (int i = 0; i < zmeny.size(); i++) {
			if (p.test(zmeny.get(i))) {
				return i;
			}
		}
		return -1;
	}

	private List<String> vSlozce() {
		return vSlozce(slozka);
	}

	private File naDisku(final String jmeno) {
		return naDisku(slozka, jmeno);
	}

	/** Předpona souborů testovací vrstvy dem/europe/czech-republic ve složce vrstev. */
	static final String PREDPONA = "europe/czech-republic.dem.";

	/** Soubor testovací vrstvy podle jména souboru na serveru („a.zip“, „a.zip.stahuje“); založí podsložku. */
	static File naDisku(final File slozka, final String jmeno) {
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(.*?)(\\.zip|\\.map)(.*)").matcher(jmeno);
		if (!m.matches()) {
			throw new IllegalArgumentException(jmeno);
		}
		final File f = new File(slozka, PREDPONA + m.group(1).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "-") + m.group(2) + m.group(3));
		f.getParentFile().mkdirs();
		return f;
	}

	/** Soubory ve složce vrstev i v podsložkách, s předponou testovací vrstvy zkrácenou na jméno souboru na serveru. */
	static List<String> vSlozce(final File slozka) {
		final List<String> l = new ArrayList<>();
		if (slozka.isDirectory()) {
			try (java.util.stream.Stream<java.nio.file.Path> s = Files.walk(slozka.toPath())) {
				s.filter(Files::isRegularFile).forEach(f -> l.add(slozka.toPath().relativize(f).toString().replace('\\', '/').replace(PREDPONA, "")));
			} catch (final IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}
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
		assertEquals(Arrays.asList("cz_1.zip", "cz_1.zip.sha256", "cz_2.zip", "cz_2.zip.sha256"), vSlozce());
		assertArrayEquals(data.get("cz_2.zip"), Files.readAllBytes(naDisku("cz_2.zip").toPath()));

		pozadavky.clear();
		assertEquals("stažená vrstva se znovu nestahuje", Faze.HOTOVO, stahni(v, new AtomicBoolean()).getFaze());
		assertEquals(Collections.emptyList(), pozadavky);
	}

	/** SHA-256 se počítá při stahování: ověření po stažení soubor znovu nečte (změna na disku po stažení se už neprojeví). */
	@Test
	public void soucetSeNepocitaZnovuZDisku() throws IOException {
		final KatalogovaVrstva v = dvaSoubory();
		final AtomicBoolean podvrzeno = new AtomicBoolean();
		final StavVrstvy s = stahovac.stahni(StavVrstvy.novy(v), slozka, () -> false, st -> {
			if (st.getFaze() == Faze.OVERUJE && !podvrzeno.getAndSet(true)) {
				try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(naDisku("cz_1.zip.stahuje"), "rw")) {
					f.seek(10);
					f.write(~data.get("cz_1.zip")[10]);
				} catch (final IOException e) {
					throw new java.io.UncheckedIOException(e);
				}
			}
		});
		assertTrue(podvrzeno.get());
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
	}

	@Test
	public void spatnySoucetNicNepresune() throws IOException {
		data.put("cz_1.zip", nahodna(300_000, 1));
		data.put("cz_2.zip", nahodna(200_000, 2));
		final KatalogovaVrstva v = vrstva("hgt-zip", Collections.singletonMap("cz_2.zip", KatalogVrstevTest.SHA), "cz_1.zip", "cz_2.zip");
		final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(StahovacVrstev.class);
		final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> zachycene = new ch.qos.logback.core.read.ListAppender<>();
		zachycene.start();
		logger.addAppender(zachycene);
		final StavVrstvy s;
		try {
			s = stahni(v, new AtomicBoolean());
		} finally {
			logger.detachAppender(zachycene);
		}
		assertEquals(s.toString(), Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("cz_2.zip je poškozený"));
		final String skutecny = sha(data.get("cz_2.zip"));
		assertTrue("vadný součet je v logu s cestou a oběma součty", zachycene.list.stream().anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
				&& e.getFormattedMessage().contains("cz_2.zip") && e.getFormattedMessage().contains(KatalogVrstevTest.SHA) && e.getFormattedMessage().contains(skutecny)));
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
		assertFalse(naDisku("cz_1.zip").exists());

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
			// Server poslal půlku, klient ji ale nemusel ještě zapsat; zrušit až po zápisu.
			final long konec = System.currentTimeMillis() + 20_000;
			while (naDisku("cz_2.zip.stahuje").length() == 0 && System.currentTimeMillis() < konec) {
				Thread.sleep(10);
			}
			zruseno.set(true);
			zdrzet.countDown();
			final StavVrstvy s = f.get(20, TimeUnit.SECONDS);
			assertEquals(s.toString(), Faze.ZRUSENO, s.getFaze());
			assertTrue(s.isRozpracovano());
			assertFalse("nic není na místě", naDisku("cz_1.zip").exists());
		} finally {
			e.shutdownNow();
		}
		final StavVrstvy zDisku = StahovacVrstev.mistniStav(StavVrstvy.novy(v), slozka);
		assertTrue(zDisku.isRozpracovano());
		assertEquals(Mistni.NEMAME, zDisku.getMistni());
		final long mame = naDisku("cz_2.zip.stahuje").length();
		assertTrue("půlka druhého souboru: " + mame, mame > 0 && mame < 200_000);
		assertEquals(300_000 + mame, zDisku.getStazeno());

		zdrzetSoubor = null;
		pozadavky.clear();
		final List<StavVrstvy> zmeny = new CopyOnWriteArrayList<>();
		final StavVrstvy s = stahovac.stahni(StavVrstvy.novy(v), slozka, () -> false, zmeny::add);
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		final int dopocteno = indexZmeny(zmeny, z -> z.getFaze() == Faze.OVERUJE && z.getStazeno() == 300_000 + mame);
		assertTrue("při navázání se součet rozpracované části dopočítá s průběhem ověřování", dopocteno >= 0);
		assertTrue("pak se stahuje dál", indexZmeny(zmeny.subList(dopocteno, zmeny.size()), z -> z.getFaze() == Faze.STAHUJE) >= 0);
		assertEquals("navazuje druhý soubor", Collections.singletonList("cz_2.zip bytes=" + mame + "-"), pozadavky);
		assertArrayEquals(data.get("cz_2.zip"), Files.readAllBytes(naDisku("cz_2.zip").toPath()));
		assertEquals(Arrays.asList("cz_1.zip", "cz_1.zip.sha256", "cz_2.zip", "cz_2.zip.sha256"), vSlozce());
	}

	@Test
	public void jinaVerzeSeNahradi() throws IOException {
		final KatalogovaVrstva v = dvaSoubory();
		assertTrue(slozka.mkdirs());
		Files.write(naDisku("cz_1.zip").toPath(), new byte[10]);
		assertEquals(Mistni.JINA_VERZE, StahovacVrstev.mistniStav(StavVrstvy.novy(v), slozka).getMistni());
		final StavVrstvy s = stahni(v, new AtomicBoolean());
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertArrayEquals(data.get("cz_1.zip"), Files.readAllBytes(naDisku("cz_1.zip").toPath()));
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
		assertFalse(naDisku("vadna.map").exists());
	}

	/** Vrstva tourist pro Česko se souborem cz.tourist.map ze serveru; {@code sha} null = správný součet. */
	private KatalogovaVrstva vrstvaTourist(final String sha) throws IOException {
		final byte[] mapa = StahovacMapyTest.kukov();
		data.put("cz.tourist.map", mapa);
		final String json = "{\"version\": 1, \"sources\": [\"http://127.0.0.1:" + server.getAddress().getPort() + "/rel/\"], \"layers\": ["
				+ KatalogVrstevTest.vrstva("tourist/europe/czech-republic", "tourist", "europe/czech-republic", "map",
						KatalogVrstevTest.soubor("cz.tourist.map", "europe/czech-republic.tourist.map", "cz.tourist.map", mapa.length, sha == null ? sha(mapa) : sha), "")
				+ "]}";
		return KatalogVrstev.nacti(json, KatalogVrstevTest.mapy()).getVrstvy().get(0);
	}

	/** Starší verze vrstvy tras Česka se součtem, k tomu vrstevnice a starší trasy Slovenska. */
	private void staraVrstvaTras() throws IOException {
		final File europe = new File(slozka, "europe");
		assertTrue(europe.mkdirs());
		for (final String f : Arrays.asList("czech-republic.routes.map", "czech-republic.routes.map.sha256", "czech-republic.contours.map", "slovakia.routes.map")) {
			Files.write(new File(europe, f).toPath(), new byte[1]);
		}
	}

	private boolean staraVrstvaZustala() {
		return vSlozce().containsAll(Arrays.asList("europe/czech-republic.routes.map", "europe/czech-republic.routes.map.sha256"));
	}

	/** Vrstva tras starší verze (*.routes.map) se nabídne k aktualizaci a po stažení vrstvy tourist se i se součtem smaže; jiné soubory zůstanou. */
	@Test
	public void starsiVerzeTrasSePoStazeniSmaze() throws IOException {
		final KatalogovaVrstva v = vrstvaTourist(null);
		staraVrstvaTras();
		assertEquals(Mistni.JINA_VERZE, StahovacVrstev.mistniStav(StavVrstvy.novy(v), slozka).getMistni());
		final StavVrstvy s = stahni(v, new AtomicBoolean());
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertEquals(Mistni.MAME, s.getMistni());
		assertEquals(Arrays.asList("europe/czech-republic.contours.map", "europe/czech-republic.tourist.map", "europe/czech-republic.tourist.map.sha256",
				"europe/slovakia.routes.map"), vSlozce());
	}

	/** Starší verze vrstvy tras zůstane, když se nová nestáhne, neprojde ověřením nebo ji nejde dát na místo. */
	@Test
	public void starsiVerzeTrasZustanePriChybe() throws IOException {
		staraVrstvaTras();
		chybi.add("cz.tourist.map");
		StavVrstvy s = stahni(vrstvaTourist(null), new AtomicBoolean());
		assertEquals(s.toString(), Faze.CHYBA, s.getFaze());
		assertTrue("stažení selhalo", staraVrstvaZustala());
		chybi.clear();

		s = stahni(vrstvaTourist(KatalogVrstevTest.SHA), new AtomicBoolean());
		assertEquals(s.toString(), Faze.CHYBA, s.getFaze());
		assertTrue("nová verze neprošla ověřením", staraVrstvaZustala());

		// Na místě je jiná verze vrstvy tourist; nový soubor zmizí, když se před výměnou uvolňují mapy, a výměna selže.
		final KatalogovaVrstva v = vrstvaTourist(null);
		Files.write(new File(slozka, "europe/czech-republic.tourist.map").toPath(), new byte[1]);
		final StahovacMapy http = new StahovacMapy("GeoKuk/test (https://example.org)", () -> StahovacMapy.smaz(StahovacVrstev.rozpracovany(slozka, v.getSoubory().get(0))));
		http.pauzaPresunuMs = 10;
		stahovac = new StahovacVrstev(http);
		s = stahni(v, new AtomicBoolean());
		assertEquals(s.toString(), Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("nejde nahradit"));
		assertTrue("výměna selhala", staraVrstvaZustala());
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
	public void modelStahujeVrstvu() throws Exception {
		final KatalogovaVrstva v = dvaSoubory();
		final KatalogVrstev katalog = KatalogVrstev.nacti("{\"version\": 1, \"sources\": [\"http://127.0.0.1:" + server.getAddress().getPort()
				+ "/rel/\"], \"layers\": [" + KatalogVrstevTest.vrstva(v.getId(), "dem", v.getRegion(), "hgt-zip", KatalogVrstevTest.soubor("cz_1.zip", "cz_1.zip",
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

	/** Vrstvy se stahují nejvýš dvě souběžně; zrušení zastaví obě a nové stahování po něm už běží. */
	@Test(timeout = 60_000)
	public void modelStahujeDveVrstvySoubezne() throws Exception {
		final StringBuilder vrstvy = new StringBuilder();
		final String[] regiony = { "europe/czech-republic", "europe/germany", "europe/germany/bayern" };
		for (int i = 0; i < regiony.length; i++) {
			final String jmeno = "d_" + i + ".zip";
			data.put(jmeno, nahodna(200_000, 10 + i));
			vrstvy.append(i == 0 ? "" : ",").append(KatalogVrstevTest.vrstva("dem/" + regiony[i], "dem", regiony[i], "hgt-zip",
					KatalogVrstevTest.soubor(jmeno, jmeno, 200_000, sha(data.get(jmeno))), ""));
		}
		final KatalogVrstev katalog = KatalogVrstev.nacti("{\"version\": 1, \"sources\": [\"http://127.0.0.1:" + server.getAddress().getPort() + "/rel/\"], \"layers\": ["
				+ vrstvy + "]}", KatalogVrstevTest.mapy());
		final File mapy = new File(tmp.getRoot(), "offline-mapy");
		final SpravaMapModel model = new SpravaMapModel(KatalogVrstevTest.mapy(), katalog);
		model.nastavProTest(() -> mapy, e -> {
		});
		model.getStahovac().casovyLimitMs = 10_000;
		final KatalogovaVrstva a = katalog.getVrstvy().get(0);
		final KatalogovaVrstva b = katalog.getVrstvy().get(1);
		final KatalogovaVrstva c = katalog.getVrstvy().get(2);
		zdrzet = new CountDownLatch(1);
		zdrzetSoubor = "d_";
		assertTrue(model.stahni(a));
		assertTrue(model.stahni(b));
		while (!(pozadavky.contains("d_0.zip") && pozadavky.contains("d_1.zip"))) {
			Thread.sleep(10);
		}
		assertFalse("třetí vrstva čeká", model.stahni(c));
		assertFalse(model.stahni(a));
		assertEquals(2, model.getBeziciStahovaniVrstev().size());

		model.zrusStahovani();
		zdrzet.countDown();
		model.pockej();
		assertEquals(Collections.emptyList(), model.getBeziciStahovaniVrstev());
		assertNotEquals(Faze.HOTOVO, model.getStav(a).getFaze());
		assertNotEquals(Faze.HOTOVO, model.getStav(b).getFaze());

		assertTrue(model.stahni(c));
		model.pockej();
		assertEquals("dřívější zrušení nové stahování nezastaví", Faze.HOTOVO, model.getStav(c).getFaze());
	}
}
