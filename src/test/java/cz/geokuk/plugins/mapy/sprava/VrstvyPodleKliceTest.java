package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import cz.geokuk.framework.Event0;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva.Typ;
import cz.geokuk.plugins.mapy.sprava.StavMapy.Faze;
import cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni;

/** Vrstvy se ukládají podle klíče základní mapy (podsložky jako na serveru map) a katalog vrstev jde stáhnout ze serveru. */
public class VrstvyPodleKliceTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private HttpServer server;
	private final Map<String, byte[]> data = new ConcurrentHashMap<>();
	private volatile int kodKatalogu = 200;
	private volatile String katalog;
	private final java.util.concurrent.atomic.AtomicInteger dotazyNaKatalog = new java.util.concurrent.atomic.AtomicInteger();

	@Before
	public void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/rel/", this::obsluz);
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
	}

	@After
	public void tearDown() {
		server.stop(0);
	}

	private void obsluz(final HttpExchange ex) throws IOException {
		try {
			final String jmeno = ex.getRequestURI().getPath().replace("/rel/", "");
			final byte[] d = "latest/layers.json".equals(jmeno) ? katalog == null ? null : katalog.getBytes(StandardCharsets.UTF_8) : data.get(jmeno);
			final int kod = "latest/layers.json".equals(jmeno) ? kodKatalogu : 200;
			if ("latest/layers.json".equals(jmeno)) {
				dotazyNaKatalog.incrementAndGet();
			}
			if (d == null || kod != 200) {
				ex.sendResponseHeaders(d == null ? 404 : kod, -1);
				return;
			}
			final String etag = "\"v-" + Arrays.hashCode(d) + "\"";
			if (etag.equals(ex.getRequestHeaders().getFirst("If-None-Match"))) {
				ex.sendResponseHeaders(304, -1);
				return;
			}
			ex.getResponseHeaders().add("ETag", etag);
			ex.sendResponseHeaders(200, d.length);
			try (OutputStream out = ex.getResponseBody()) {
				out.write(d);
			}
		} finally {
			ex.close();
		}
	}

	private String zaklad() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/rel/";
	}

	private String sha(final byte[] b) throws IOException {
		final File f = tmp.newFile();
		Files.write(f.toPath(), b);
		return StahovacVrstev.sha256(f, () -> false);
	}

	/** Katalog s trasami a stínováním Bavorska; assety bez lomítek, jako v GitHub Releases. */
	private String katalogBavorska() throws IOException {
		data.put("layers-2026-10/europe_germany_bayern.tourist.map", StahovacMapyTest.kukov());
		data.put("layers-2026-10/europe_germany_bayern.dem.zip", "dem".getBytes(StandardCharsets.US_ASCII));
		final byte[] t = data.get("layers-2026-10/europe_germany_bayern.tourist.map");
		final byte[] d = data.get("layers-2026-10/europe_germany_bayern.dem.zip");
		return "{\"version\": 1, \"sources\": [\"" + zaklad() + "\"], \"layers\": ["
				+ KatalogVrstevTest.vrstva("tourist/europe/germany/bayern", "tourist", "europe/germany/bayern", "map", KatalogVrstevTest.soubor("europe_germany_bayern.tourist.map",
						"europe/germany/bayern.tourist.map", "layers-2026-10/europe_germany_bayern.tourist.map", t.length, sha(t)), "")
				+ "," + KatalogVrstevTest.vrstva("dem/europe/germany/bayern", "dem", "europe/germany/bayern", "hgt-zip", KatalogVrstevTest.soubor(
						"europe_germany_bayern.dem.zip", "europe/germany/bayern.dem.zip", "layers-2026-10/europe_germany_bayern.dem.zip", d.length, sha(d)), "")
				+ "]}";
	}

	@Test
	public void vrstvaSeUloziPodleKliceMapy() throws IOException {
		final KatalogVrstev k = KatalogVrstev.nacti(katalogBavorska(), KatalogVrstevTest.mapy());
		final KatalogMap mapy = KatalogVrstevTest.mapy();
		final KatalogovaMapa bavorsko = mapy.podleId("europe/germany/bayern").get();
		final KatalogovaVrstva trasy = k.vrstva(bavorsko.getId(), Typ.TRASY).get();
		final KatalogovaVrstva dem = k.vrstva(bavorsko.getId(), Typ.DEM).get();
		assertFalse(k.vrstva(bavorsko.getId(), Typ.VRSTEVNICE).isPresent());
		final File slozka = new File(tmp.getRoot(), "offline-mapy/layers");
		final StahovacMapy http = new StahovacMapy("GeoKuk/test", () -> {
		});
		final StahovacVrstev stahovac = new StahovacVrstev(http);
		for (final KatalogovaVrstva v : Arrays.asList(trasy, dem)) {
			final StavVrstvy s = stahovac.stahni(StavVrstvy.novy(v), slozka, () -> false, x -> {
			});
			assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
			assertEquals(Mistni.MAME, s.getMistni());
		}
		final File naDisku = new File(slozka, "europe/germany/bayern.tourist.map");
		assertArrayEquals(StahovacMapyTest.kukov(), Files.readAllBytes(naDisku.toPath()));
		assertTrue(new File(slozka, "europe/germany/bayern.dem.zip").isFile());
		assertEquals(Collections.singletonList(naDisku), SouboryVrstev.mapy(slozka));
		assertEquals(Collections.singletonList(new File(slozka, "europe/germany/bayern.dem.zip")), SouboryVrstev.dem(slozka));
		assertSame(trasy, k.podleCesty("europe/germany/bayern.tourist.map").get());
	}

	@Test
	public void souboryVrstevNajdouJenVrstvy() throws IOException {
		final File slozka = tmp.newFolder("vrstvy");
		new File(slozka, "europe/germany").mkdirs();
		for (final String f : Arrays.asList("europe/czech-republic.tourist.map", "europe/czech-republic.tourist.map.sha256", "europe/germany/bayern.tourist.map.stahuje",
				"europe/germany/bayern.contours.MAP", "europe/germany/bayern.dem.zip", "europe/germany/N48E011.hgt", "._skryty.map", "vrstvy.json")) {
			Files.write(new File(slozka, f).toPath(), new byte[1]);
		}
		assertEquals(Arrays.asList(new File(slozka, "europe/czech-republic.tourist.map"), new File(slozka, "europe/germany/bayern.contours.MAP")),
				SouboryVrstev.mapy(slozka));
		assertEquals(Arrays.asList(new File(slozka, "europe/germany/bayern.dem.zip"), new File(slozka, "europe/germany/N48E011.hgt")), SouboryVrstev.dem(slozka));
		assertEquals(Collections.emptyList(), SouboryVrstev.mapy(new File(slozka, "neni")));
	}

	@Test
	public void katalogVrstevZeServeru() throws Exception {
		final File mapy = tmp.newFolder("offline-mapy");
		final List<Event0<SpravaMapModel>> udalosti = new CopyOnWriteArrayList<>();
		final SpravaMapModel model = new SpravaMapModel(KatalogVrstevTest.mapy(), KatalogVrstev.vestaveny());
		model.nastavProTest(() -> mapy, udalosti::add);
		model.urlKataloguVrstev = URI.create(zaklad() + "latest/layers.json");
		assertEquals("vestavěný je prázdný", 0, model.getKatalogVrstev().getVrstvy().size());

		model.obnovKatalogVrstev();
		model.pockej();
		assertEquals("Katalog vrstev na serveru zatím není.", posledni(udalosti).getChyba());
		assertEquals(0, model.getKatalogVrstev().getVrstvy().size());

		katalog = "{\"version\": 1, \"sources\": [], \"layers\": []}";
		model.obnovKatalogVrstev();
		model.pockej();
		assertNotNull("vadný katalog se nepřevezme", posledni(udalosti).getChyba());
		assertFalse(new File(mapy, "layers/layers.json").exists());

		katalog = katalogBavorska();
		model.obnovKatalogVrstev();
		model.pockej();
		assertNull(posledni(udalosti).getChyba());
		assertEquals(2, model.getKatalogVrstev().getVrstvy().size());
		assertSame(model.getKatalogVrstev(), posledni(udalosti).getKatalog());
		assertEquals(katalog, new String(Files.readAllBytes(new File(mapy, "layers/layers.json").toPath()), StandardCharsets.UTF_8));
		final KatalogovaVrstva trasy = model.getKatalogVrstev().vrstva("europe/germany/bayern", Typ.TRASY).get();
		assertSame("stav patří vrstvě z nového katalogu", trasy, model.getStav(trasy).getVrstva());

		// Další start programu: katalog se načte z kopie ve složce vrstev, bez sítě.
		server.stop(0);
		final SpravaMapModel znovu = new SpravaMapModel(KatalogVrstevTest.mapy(), KatalogVrstev.vestaveny());
		final List<Event0<SpravaMapModel>> udalosti2 = new CopyOnWriteArrayList<>();
		znovu.nastavProTest(() -> mapy, udalosti2::add);
		znovu.obnovStavy();
		znovu.pockej();
		assertEquals(2, znovu.getKatalogVrstev().getVrstvy().size());
		assertTrue(udalosti2.stream().anyMatch(e -> e instanceof KatalogVrstevEvent));
	}

	/** Čistá instalace (vestavěný katalog je prázdný): katalog vrstev se stáhne sám při startu a obnoví se, až je kopie stará; bez sítě tiše platí kopie. */
	@Test
	public void katalogVrstevSeStahneSam() throws Exception {
		final File mapy = tmp.newFolder("offline-mapy");
		final List<Event0<SpravaMapModel>> udalosti = new CopyOnWriteArrayList<>();
		final SpravaMapModel model = new SpravaMapModel(KatalogVrstevTest.mapy(), KatalogVrstev.vestaveny());
		model.nastavProTest(() -> mapy, udalosti::add);
		model.urlKataloguVrstev = URI.create(zaklad() + "latest/layers.json");
		katalog = katalogBavorska();

		model.initAndFire();
		model.pockej();
		assertEquals(2, model.getKatalogVrstev().getVrstvy().size());
		assertNull(posledni(udalosti).getChyba());
		final File kopie = new File(mapy, "layers/layers.json");
		assertTrue(kopie.isFile());
		assertEquals(1, dotazyNaKatalog.get());

		model.obnovKatalogVrstevKdyzJeStary();
		model.pockej();
		assertEquals("čerstvá kopie se znovu nestahuje", 1, dotazyNaKatalog.get());

		assertTrue(kopie.setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)));
		kodKatalogu = 404;
		final int udalostiPred = udalosti.size();
		model.obnovKatalogVrstevKdyzJeStary();
		model.pockej();
		assertEquals(2, dotazyNaKatalog.get());
		assertEquals("bez katalogu na serveru platí kopie", 2, model.getKatalogVrstev().getVrstvy().size());
		assertFalse("chyba se uživateli neukazuje", udalosti.subList(udalostiPred, udalosti.size()).stream().anyMatch(e -> e instanceof KatalogVrstevEvent));

		model.obnovKatalogVrstevKdyzJeStary();
		model.pockej();
		assertEquals("po chybě se hned znovu nezkouší", 2, dotazyNaKatalog.get());

		kodKatalogu = 200;
		model.opakovaniKataloguNs = 0;
		model.obnovKatalogVrstevKdyzJeStary();
		model.pockej();
		assertEquals(3, dotazyNaKatalog.get());
		assertTrue("stará kopie se obnoví", System.currentTimeMillis() - kopie.lastModified() < TimeUnit.HOURS.toMillis(1));
	}

	/** Kopie katalogu starší než hodina se ověří podmíněným dotazem: nezměněný katalog se nestahuje, novější (třeba s vícedílnou vrstvou) se převezme. */
	@Test
	public void novejsiKatalogVrstevSePrevezmeDoHodiny() throws Exception {
		final File mapy = tmp.newFolder("offline-mapy");
		final List<Event0<SpravaMapModel>> udalosti = new CopyOnWriteArrayList<>();
		final SpravaMapModel model = new SpravaMapModel(KatalogVrstevTest.mapy(), KatalogVrstev.vestaveny());
		model.nastavProTest(() -> mapy, udalosti::add);
		model.urlKataloguVrstev = URI.create(zaklad() + "latest/layers.json");
		katalog = katalogBavorska();
		model.obnovKatalogVrstevKdyzJeStary();
		model.pockej();
		assertEquals(2, model.getKatalogVrstev().getVrstvy().size());
		final File kopie = new File(mapy, "layers/layers.json");

		assertTrue(kopie.setLastModified(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(90)));
		final int udalostiPred = udalosti.size();
		model.obnovKatalogVrstevKdyzJeStary();
		model.pockej();
		assertEquals(2, dotazyNaKatalog.get());
		assertFalse("nezměněný katalog se znovu neohlásí", udalosti.subList(udalostiPred, udalosti.size()).stream().anyMatch(e -> e instanceof KatalogVrstevEvent));
		assertTrue("ověření se počítá od teď", System.currentTimeMillis() - kopie.lastModified() < TimeUnit.MINUTES.toMillis(1));
		model.obnovKatalogVrstevKdyzJeStary();
		model.pockej();
		assertEquals("do hodiny se znovu neověřuje", 2, dotazyNaKatalog.get());

		final StringBuilder dily = new StringBuilder();
		for (int i = 1; i <= 4; i++) {
			dily.append(i == 1 ? "" : ",").append(KatalogVrstevTest.soubor("europe_germany_bayern.contours." + i + ".map", "europe/germany/bayern.contours." + i + ".map",
					"layers-2026-10b/europe_germany_bayern.contours." + i + ".map", 10, sha(new byte[10])));
		}
		katalog = katalogBavorska().replace("]}", "," + KatalogVrstevTest.vrstva("contours/europe/germany/bayern", "contours", "europe/germany/bayern", "map", dily.toString(), "")
				+ "]}");
		assertTrue(kopie.setLastModified(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(90)));
		model.obnovKatalogVrstevKdyzJeStary();
		model.pockej();
		assertEquals(3, dotazyNaKatalog.get());
		final KatalogovaVrstva vrstevnice = model.getKatalogVrstev().vrstva("europe/germany/bayern", Typ.VRSTEVNICE).get();
		assertEquals(4, vrstevnice.getSoubory().size());
		assertSame(model.getKatalogVrstev(), posledni(udalosti).getKatalog());
	}

	/** Na žádost uživatele (Zkontrolovat aktualizace) se katalog ověří hned, i když je kopie čerstvá; nezměněný se nestahuje. */
	@Test
	public void kontrolaKataloguVrstevNaZadostHned() throws Exception {
		final File mapy = tmp.newFolder("offline-mapy");
		final List<Event0<SpravaMapModel>> udalosti = new CopyOnWriteArrayList<>();
		final SpravaMapModel model = new SpravaMapModel(KatalogVrstevTest.mapy(), KatalogVrstev.vestaveny());
		model.nastavProTest(() -> mapy, udalosti::add);
		model.urlKataloguVrstev = URI.create(zaklad() + "latest/layers.json");
		katalog = katalogBavorska();
		model.obnovKatalogVrstevKdyzJeStary();
		model.pockej();
		assertEquals(1, dotazyNaKatalog.get());

		final int udalostiPred = udalosti.size();
		model.zkontrolujKatalogVrstev();
		model.pockej();
		assertEquals("čerstvá kopie se na žádost ověří", 2, dotazyNaKatalog.get());
		assertFalse("nezměněný katalog se neohlásí", udalosti.subList(udalostiPred, udalosti.size()).stream().anyMatch(e -> e instanceof KatalogVrstevEvent));

		katalog = katalogBavorska().replace("]}", "," + KatalogVrstevTest.vrstva("contours/europe/germany/bayern", "contours", "europe/germany/bayern", "map",
				KatalogVrstevTest.soubor("europe_germany_bayern.contours.map", "europe/germany/bayern.contours.map", "layers-2026-10b/europe_germany_bayern.contours.map", 10,
						sha(new byte[10])), "") + "]}");
		model.zkontrolujKatalogVrstev();
		model.pockej();
		assertEquals(3, dotazyNaKatalog.get());
		assertTrue("změněný katalog se převezme", model.getKatalogVrstev().vrstva("europe/germany/bayern", Typ.VRSTEVNICE).isPresent());
		assertSame(model.getKatalogVrstev(), posledni(udalosti).getKatalog());
	}

	private static KatalogVrstevEvent posledni(final List<Event0<SpravaMapModel>> udalosti) {
		for (int i = udalosti.size() - 1; i >= 0; i--) {
			if (udalosti.get(i) instanceof KatalogVrstevEvent) {
				return (KatalogVrstevEvent) udalosti.get(i);
			}
		}
		throw new AssertionError("žádná událost katalogu vrstev");
	}

	/** Složka vrstev z dřívější bety (vrstvy/) se převezme jako layers/, stažené vrstvy zůstanou. */
	@Test
	public void staraSlozkaVrstevSePrevezme() throws Exception {
		final File mapy = tmp.newFolder("offline-mapy");
		final File stara = new File(mapy, "vrstvy/europe");
		assertTrue(stara.mkdirs());
		Files.write(new File(stara, "czech-republic.tourist.map").toPath(), new byte[] { 1 });
		Files.write(new File(mapy, "vrstvy/vrstvy.json").toPath(), "{}".getBytes(StandardCharsets.US_ASCII));
		final SpravaMapModel model = new SpravaMapModel(KatalogVrstevTest.mapy(), KatalogVrstev.vestaveny());
		model.nastavProTest(() -> mapy, e -> {
		});
		model.obnovStavy();
		model.pockej();
		assertFalse(new File(mapy, "vrstvy").exists());
		assertTrue(new File(mapy, "layers/europe/czech-republic.tourist.map").isFile());
		assertFalse("kopie katalogu ve starém formátu pryč", new File(mapy, "layers/vrstvy.json").exists());
		assertEquals(new File(mapy, "layers"), model.getSlozkaVrstev());

		// Když už layers/ existuje, stará složka se nechá být.
		assertTrue(new File(mapy, "vrstvy").mkdir());
		model.obnovStavy();
		model.pockej();
		assertTrue(new File(mapy, "vrstvy").isDirectory());
		assertTrue(new File(mapy, "layers/europe/czech-republic.tourist.map").isFile());
	}
}
