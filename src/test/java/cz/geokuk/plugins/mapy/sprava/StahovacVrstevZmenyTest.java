package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import cz.geokuk.plugins.mapy.sprava.StavMapy.Faze;

/** Stahování vrstvy, když se soubory změní na serveru, v katalogu nebo na disku, a výměna souborů vše, nebo nic. */
public class StahovacVrstevZmenyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private HttpServer server;
	private File slozka;
	private StahovacVrstev stahovac;
	private final Map<String, byte[]> data = new ConcurrentHashMap<>();
	private final Map<String, String> etagy = new ConcurrentHashMap<>();
	private final List<String> pozadavky = new CopyOnWriteArrayList<>();
	private volatile boolean ignorujRange;
	private volatile boolean ignorujIfRange;
	private volatile boolean spatnyStart;
	private volatile boolean vzdy416;
	private final AtomicInteger uvolneni = new AtomicInteger();
	private volatile Runnable priUvolneni = () -> {
	};

	@Before
	public void setUp() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/rel/", this::obsluz);
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		slozka = new File(tmp.getRoot(), "offline-mapy/vrstvy");
		final StahovacMapy http = new StahovacMapy("GeoKuk/test", () -> {
			uvolneni.incrementAndGet();
			priUvolneni.run();
		});
		http.casovyLimitMs = 10_000;
		http.pauzaPresunuMs = 1;
		stahovac = new StahovacVrstev(http);
	}

	@After
	public void tearDown() {
		server.stop(0);
	}

	private void obsluz(final HttpExchange ex) throws IOException {
		final String jmeno = ex.getRequestURI().getPath().replace("/rel/", "");
		final String range = ex.getRequestHeaders().getFirst("Range");
		final String ifRange = ex.getRequestHeaders().getFirst("If-Range");
		pozadavky.add(jmeno + (range == null ? "" : " " + range + " if=" + ifRange));
		try {
			final byte[] d = data.get(jmeno);
			if (d == null) {
				ex.sendResponseHeaders(404, -1);
				return;
			}
			final String etag = etagy.getOrDefault(jmeno, "\"v-1\"");
			ex.getResponseHeaders().add("ETag", etag);
			int od = 0;
			if (range != null && vzdy416) {
				ex.sendResponseHeaders(416, -1);
				return;
			}
			if (range != null && !ignorujRange && (ignorujIfRange || etag.equals(ifRange))) {
				try {
					od = Integer.parseInt(range.replaceAll("bytes=(\\d+)-", "$1"));
				} catch (final NumberFormatException e) {
					ex.sendResponseHeaders(416, -1);
					return;
				}
				final int start = spatnyStart ? 0 : od;
				ex.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + (d.length - 1) + "/" + d.length);
				ex.sendResponseHeaders(206, d.length - od);
			} else {
				ex.sendResponseHeaders(200, d.length);
			}
			try (OutputStream out = ex.getResponseBody()) {
				out.write(d, od, d.length - od);
			}
		} catch (final IOException e) {
			// spojení
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

	private KatalogovaVrstva vrstva(final String... soubory) throws IOException {
		final StringBuilder sb = new StringBuilder();
		for (final String s : soubory) {
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(KatalogVrstevTest.soubor(s, s, data.get(s).length, sha(data.get(s))));
		}
		final String json = "{\"format\": 1, \"zdroje\": [\"http://127.0.0.1:" + server.getAddress().getPort() + "/rel/\"], \"vrstvy\": ["
				+ KatalogVrstevTest.vrstva("dem/europe/czech-republic", "dem", "europe/czech-republic", "hgt-zip", sb.toString(), "") + "]}";
		return KatalogVrstev.nacti(json, KatalogVrstevTest.mapy()).getVrstvy().get(0);
	}

	private StavVrstvy stahni(final KatalogovaVrstva v) {
		return stahovac.stahni(StavVrstvy.novy(v), slozka, () -> false, s -> {
		});
	}

	/** Rozpracovaná část: prvních {@code kolik} bajtů {@code obsah} a info jako po přerušeném stahování. */
	private void rozpracuj(final KatalogovaVrstva v, final int index, final byte[] obsah, final int kolik, final String etag) throws IOException {
		final KatalogovaVrstva.Soubor so = v.getSoubory().get(index);
		slozka.mkdirs();
		Files.write(StahovacVrstev.rozpracovany(slozka, so).toPath(), Arrays.copyOf(obsah, kolik));
		final StahovacMapy.Info i = new StahovacMapy.Info();
		i.url = so.getUrly().get(0).toString();
		i.etag = etag;
		i.sha256 = so.getSha256();
		i.celkem = so.getVelikost();
		StahovacMapy.ulozInfo(StahovacVrstev.info(slozka, so), i);
	}

	private byte[] obsah(final String jmeno) throws IOException {
		return Files.readAllBytes(new File(slozka, jmeno).toPath());
	}

	/** Server Range ignoruje (200): stahuje se od nuly, žádný slepenec. */
	@Test
	public void serverIgnorujeRange() throws IOException {
		data.put("a.zip", nahodna(100_000, 1));
		final KatalogovaVrstva v = vrstva("a.zip");
		rozpracuj(v, 0, data.get("a.zip"), 40_000, "\"v-1\"");
		ignorujRange = true;
		final StavVrstvy s = stahni(v);
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertArrayEquals(data.get("a.zip"), obsah("a.zip"));
		assertEquals(Collections.singletonList("a.zip bytes=40000- if=\"v-1\""), pozadavky);
	}

	/** Katalog změnil SHA (nová verze stejné velikosti): rozpracovaná stará část se zahodí, stahuje se bez Range. */
	@Test
	public void zmenaShaVKataloguZacneOdNuly() throws IOException {
		final byte[] stara = nahodna(100_000, 1);
		data.put("a.zip", nahodna(100_000, 2));
		final KatalogovaVrstva v = vrstva("a.zip");
		rozpracuj(v, 0, stara, 40_000, "\"v-1\"");
		// info patří ke staré verzi
		final StahovacMapy.Info i = StahovacMapy.nactiInfo(StahovacVrstev.info(slozka, v.getSoubory().get(0)));
		i.sha256 = sha(stara);
		StahovacMapy.ulozInfo(StahovacVrstev.info(slozka, v.getSoubory().get(0)), i);
		final StavVrstvy s = stahni(v);
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertArrayEquals(data.get("a.zip"), obsah("a.zip"));
		assertEquals(Collections.singletonList("a.zip"), pozadavky);
	}

	/** Server navázal jinou verzí (jiný ETag v 206, If-Range nerespektoval): žádný slepenec, rozpracovaná část pryč. */
	@Test
	public void jinyEtagV206() throws IOException {
		final byte[] stara = nahodna(100_000, 1);
		data.put("a.zip", stara);
		final KatalogovaVrstva v = vrstva("a.zip");
		rozpracuj(v, 0, stara, 40_000, "\"v-1\"");
		etagy.put("a.zip", "\"v-2\"");
		ignorujIfRange = true;
		final StavVrstvy s = stahni(v);
		assertEquals(s.toString(), Faze.CHYBA, s.getFaze());
		assertFalse(new File(slozka, "a.zip").exists());
		assertFalse(StahovacVrstev.rozpracovany(slozka, v.getSoubory().get(0)).exists());
		assertEquals(Faze.HOTOVO, stahni(v).getFaze());
		assertArrayEquals(stara, obsah("a.zip"));
	}

	/** 416: rozpracovaná část pryč, další pokus projde. */
	@Test
	public void odpoved416() throws IOException {
		data.put("a.zip", nahodna(100_000, 1));
		final KatalogovaVrstva v = vrstva("a.zip");
		rozpracuj(v, 0, data.get("a.zip"), 40_000, "\"v-1\"");
		vzdy416 = true;
		assertEquals(Faze.CHYBA, stahni(v).getFaze());
		assertFalse(StahovacVrstev.rozpracovany(slozka, v.getSoubory().get(0)).exists());
		assertEquals(Faze.HOTOVO, stahni(v).getFaze());
		assertArrayEquals(data.get("a.zip"), obsah("a.zip"));
	}

	/** 206 s Content-Range od jiného bajtu: chyba, nic se nepřesune. */
	@Test
	public void spatnyContentRange() throws IOException {
		data.put("a.zip", nahodna(100_000, 1));
		final KatalogovaVrstva v = vrstva("a.zip");
		rozpracuj(v, 0, data.get("a.zip"), 40_000, "\"v-1\"");
		spatnyStart = true;
		final StavVrstvy s1 = stahni(v);
		assertEquals(Faze.CHYBA, s1.getFaze());
		assertTrue(s1.getChyba(), s1.getChyba().contains("jinou část"));
		assertFalse(new File(slozka, "a.zip").exists());
	}

	/** Pokračování po přerušení: SHA se počítá z celého souboru. */
	@Test
	public void pokracovaniOveriCelySoubor() throws IOException {
		data.put("a.zip", nahodna(100_000, 1));
		final KatalogovaVrstva v = vrstva("a.zip");
		// první část je vadná (jiná data), validátor sedí -> server naváže, celý soubor nesedí na SHA
		rozpracuj(v, 0, nahodna(100_000, 9), 40_000, "\"v-1\"");
		final StavVrstvy s = stahni(v);
		assertEquals(s.toString(), Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("poškozený"));
		assertFalse(new File(slozka, "a.zip").exists());
		assertFalse(StahovacVrstev.rozpracovany(slozka, v.getSoubory().get(0)).exists());
	}

	/** Na místě je stará verze obou souborů; přesun druhého selže (na Windows: soubor otevřený). */
	@Test
	public void presunDruhehoSelzeStaraVerzeZustaneCela() throws IOException {
		data.put("a.zip", nahodna(100_000, 1));
		data.put("b.zip", nahodna(50_000, 2));
		final KatalogovaVrstva v = vrstva("a.zip", "b.zip");
		slozka.mkdirs();
		final byte[] staraA = nahodna(90_000, 3);
		final byte[] staraB = nahodna(45_000, 4);
		Files.write(new File(slozka, "a.zip").toPath(), staraA);
		Files.write(new File(slozka, "b.zip").toPath(), staraB);
		// Před výměnou zmizí nový b.zip, jeho přesun selže jako u zamčeného souboru.
		priUvolneni = () -> {
			if (uvolneni.get() == 1) {
				StahovacMapy.smaz(new File(slozka, "b.zip.stahuje"));
			}
		};
		final StavVrstvy s = stahni(v);
		assertEquals(s.toString(), Faze.CHYBA, s.getFaze());
		assertTrue(s.getChyba(), s.getChyba().contains("nejde nahradit"));
		assertArrayEquals("b.zip je stará verze", staraB, obsah("b.zip"));
		assertArrayEquals("a.zip zůstane stará verze (vše, nebo nic)", staraA, obsah("a.zip"));
		assertArrayEquals("nový a.zip čeká rozpracovaný", data.get("a.zip"), obsah("a.zip.stahuje"));
		assertFalse(new File(slozka, "a.zip" + StahovacVrstev.PRIPONA_STARA).exists());

		priUvolneni = () -> {
		};
		pozadavky.clear();
		assertEquals(Faze.HOTOVO, stahni(v).getFaze());
		assertEquals("stahuje se jen chybějící b.zip", Collections.singletonList("b.zip"), pozadavky);
		assertArrayEquals(data.get("a.zip"), obsah("a.zip"));
		assertArrayEquals(data.get("b.zip"), obsah("b.zip"));
	}

	/** Pád uprostřed výměny: nový a.zip na místě, starý odsunutý. Další stažení vrátí stav a dokončí výměnu. */
	@Test
	public void obnovaPoPaduUprostredVymeny() throws IOException {
		data.put("a.zip", nahodna(100_000, 1));
		data.put("b.zip", nahodna(50_000, 2));
		final KatalogovaVrstva v = vrstva("a.zip", "b.zip");
		slozka.mkdirs();
		final byte[] staraA = nahodna(90_000, 3);
		Files.write(new File(slozka, "a.zip" + StahovacVrstev.PRIPONA_STARA).toPath(), staraA);
		rozpracuj(v, 0, data.get("a.zip"), 100_000, "\"v-1\"");
		Files.move(new File(slozka, "a.zip.stahuje").toPath(), new File(slozka, "a.zip").toPath());
		rozpracuj(v, 1, data.get("b.zip"), 50_000, "\"v-1\"");
		StahovacVrstev.obnovPoPreruseneVymene(slozka, v);
		assertArrayEquals("stará verze zpět na místě", staraA, obsah("a.zip"));
		assertArrayEquals(data.get("a.zip"), obsah("a.zip.stahuje"));
		assertEquals(StavVrstvy.Mistni.JINA_VERZE, StahovacVrstev.mistniStav(StavVrstvy.novy(v), slozka).getMistni());
		final StavVrstvy s = stahni(v);
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertArrayEquals(data.get("a.zip"), obsah("a.zip"));
		assertArrayEquals(data.get("b.zip"), obsah("b.zip"));
		assertFalse(new File(slozka, "a.zip" + StahovacVrstev.PRIPONA_STARA).exists());
	}

	/** Soubor správné velikosti, ale jiného obsahu (jiná verze se stejnou velikostí / poškozený) se bere za stažený. */
	@Test
	public void stejnaVelikostJinyObsahSeNestahne() throws IOException {
		data.put("a.zip", nahodna(100_000, 1));
		final KatalogovaVrstva v = vrstva("a.zip");
		slozka.mkdirs();
		Files.write(new File(slozka, "a.zip").toPath(), new byte[100_000]);
		assertEquals(StavVrstvy.Mistni.JINA_VERZE, StahovacVrstev.mistniStav(StavVrstvy.novy(v), slozka).getMistni());
		final StavVrstvy s = stahni(v);
		assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
		assertEquals(StavVrstvy.Mistni.MAME, s.getMistni());
		assertArrayEquals(data.get("a.zip"), obsah("a.zip"));
		assertEquals(Collections.singletonList("a.zip"), pozadavky);
	}

	/** Nová verze stejné velikosti v katalogu: stažená stará verze už není aktuální. */
	@Test
	public void novaVerzeStejneVelikosti() throws IOException {
		data.put("a.zip", nahodna(100_000, 1));
		assertEquals(Faze.HOTOVO, stahni(vrstva("a.zip")).getFaze());
		data.put("a.zip", nahodna(100_000, 2));
		final KatalogovaVrstva nova = vrstva("a.zip");
		assertEquals(StavVrstvy.Mistni.JINA_VERZE, StahovacVrstev.mistniStav(StavVrstvy.novy(nova), slozka).getMistni());
		assertEquals(Faze.HOTOVO, stahni(nova).getFaze());
		assertArrayEquals(data.get("a.zip"), obsah("a.zip"));
	}

	/** Pád v úklidu: všechny nové soubory jsou na místě, u některých ještě odsunutá stará verze. Výměna se dokončí, žádný mix verzí. */
	@Test
	public void padPriUkliduDokonciVymenu() throws IOException {
		data.put("a.zip", nahodna(100_000, 1));
		data.put("b.zip", nahodna(50_000, 2));
		data.put("c.zip", nahodna(30_000, 3));
		final KatalogovaVrstva v = vrstva("a.zip", "b.zip", "c.zip");
		slozka.mkdirs();
		for (int i = 0; i < 3; i++) {
			final KatalogovaVrstva.Soubor so = v.getSoubory().get(i);
			rozpracuj(v, i, data.get(so.getNazev()), (int) so.getVelikost(), "\"v-1\"");
			Files.move(StahovacVrstev.rozpracovany(slozka, so).toPath(), StahovacVrstev.cil(slozka, so).toPath());
			Files.write(StahovacVrstev.soucet(slozka, so).toPath(), so.getSha256().getBytes("US-ASCII"));
		}
		StahovacMapy.smaz(StahovacVrstev.info(slozka, v.getSoubory().get(0)));
		Files.write(new File(slozka, "b.zip" + StahovacVrstev.PRIPONA_STARA).toPath(), nahodna(45_000, 4));
		Files.write(new File(slozka, "c.zip" + StahovacVrstev.PRIPONA_STARA).toPath(), nahodna(25_000, 5));
		StahovacVrstev.obnovPoPreruseneVymene(slozka, v);
		for (final String n : Arrays.asList("a.zip", "b.zip", "c.zip")) {
			assertArrayEquals(n, data.get(n), obsah(n));
		}
		assertEquals(Arrays.asList("a.zip", "a.zip.sha256", "b.zip", "b.zip.sha256", "c.zip", "c.zip.sha256"), vSlozce());
		assertEquals(StavVrstvy.Mistni.MAME, StahovacVrstev.mistniStav(StavVrstvy.novy(v), slozka).getMistni());
	}

	/** Pád uprostřed přesunů a pak Zahodit: na místě zůstane celá stará verze. */
	@Test
	public void padAZahodVratiStarouVerzi() throws IOException {
		data.put("a.zip", nahodna(100_000, 1));
		data.put("b.zip", nahodna(50_000, 2));
		final KatalogovaVrstva v = vrstva("a.zip", "b.zip");
		slozka.mkdirs();
		final byte[] staraA = nahodna(90_000, 3);
		final byte[] staraB = nahodna(45_000, 4);
		Files.write(new File(slozka, "a.zip" + StahovacVrstev.PRIPONA_STARA).toPath(), staraA);
		Files.write(new File(slozka, "b.zip" + StahovacVrstev.PRIPONA_STARA).toPath(), staraB);
		rozpracuj(v, 0, data.get("a.zip"), 100_000, "\"v-1\"");
		Files.move(new File(slozka, "a.zip.stahuje").toPath(), new File(slozka, "a.zip").toPath());
		rozpracuj(v, 1, data.get("b.zip"), 50_000, "\"v-1\"");
		StahovacVrstev.zahod(slozka, v);
		assertArrayEquals(staraA, obsah("a.zip"));
		assertArrayEquals(staraB, obsah("b.zip"));
		assertEquals(Arrays.asList("a.zip", "b.zip"), vSlozce());
	}

	/** Vrácená stará verze stejné velikosti jako nová se nesmí tvářit jako stažená nová. */
	@Test
	public void vracenaStaraVerzeStejneVelikostiNeniAktualni() throws IOException {
		data.put("a.zip", nahodna(100_000, 1));
		data.put("b.zip", nahodna(50_000, 2));
		final KatalogovaVrstva v = vrstva("a.zip", "b.zip");
		slozka.mkdirs();
		final byte[] staraA = nahodna(100_000, 3);
		Files.write(new File(slozka, "a.zip").toPath(), staraA);
		Files.write(new File(slozka, "b.zip").toPath(), nahodna(50_000, 4));
		priUvolneni = () -> {
			if (uvolneni.get() == 1) {
				StahovacMapy.smaz(new File(slozka, "b.zip.stahuje"));
			}
		};
		final StavVrstvy s = stahni(v);
		assertEquals(s.toString(), Faze.CHYBA, s.getFaze());
		assertArrayEquals(staraA, obsah("a.zip"));
		assertEquals(StavVrstvy.Mistni.JINA_VERZE, s.getMistni());
		assertEquals(StavVrstvy.Mistni.JINA_VERZE, StahovacVrstev.mistniStav(StavVrstvy.novy(v), slozka).getMistni());
	}

	private List<String> vSlozce() {
		final List<String> l = new ArrayList<>(Arrays.asList(slozka.list()));
		Collections.sort(l);
		return l;
	}
}
