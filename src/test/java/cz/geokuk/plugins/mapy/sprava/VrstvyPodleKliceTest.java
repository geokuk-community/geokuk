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
			if (d == null || kod != 200) {
				ex.sendResponseHeaders(d == null ? 404 : kod, -1);
				return;
			}
			ex.getResponseHeaders().add("ETag", "\"v-1\"");
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
		data.put("layers-2026-10/europe_germany_bayern.routes.map", StahovacMapyTest.kukov());
		data.put("layers-2026-10/europe_germany_bayern.dem.zip", "dem".getBytes(StandardCharsets.US_ASCII));
		final byte[] t = data.get("layers-2026-10/europe_germany_bayern.routes.map");
		final byte[] d = data.get("layers-2026-10/europe_germany_bayern.dem.zip");
		return "{\"version\": 1, \"sources\": [\"" + zaklad() + "\"], \"layers\": ["
				+ KatalogVrstevTest.vrstva("routes/europe/germany/bayern", "routes", "europe/germany/bayern", "map", KatalogVrstevTest.soubor("europe_germany_bayern.routes.map",
						"europe/germany/bayern.routes.map", "layers-2026-10/europe_germany_bayern.routes.map", t.length, sha(t)), "")
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
		final File naDisku = new File(slozka, "europe/germany/bayern.routes.map");
		assertArrayEquals(StahovacMapyTest.kukov(), Files.readAllBytes(naDisku.toPath()));
		assertTrue(new File(slozka, "europe/germany/bayern.dem.zip").isFile());
		assertEquals(Collections.singletonList(naDisku), SouboryVrstev.mapy(slozka));
		assertEquals(Collections.singletonList(new File(slozka, "europe/germany/bayern.dem.zip")), SouboryVrstev.dem(slozka));
		assertSame(trasy, k.podleCesty("europe/germany/bayern.routes.map").get());
	}

	@Test
	public void souboryVrstevNajdouJenVrstvy() throws IOException {
		final File slozka = tmp.newFolder("vrstvy");
		new File(slozka, "europe/germany").mkdirs();
		for (final String f : Arrays.asList("europe/czech-republic.routes.map", "europe/czech-republic.routes.map.sha256", "europe/germany/bayern.routes.map.stahuje",
				"europe/germany/bayern.contours.MAP", "europe/germany/bayern.dem.zip", "europe/germany/N48E011.hgt", "._skryty.map", "vrstvy.json")) {
			Files.write(new File(slozka, f).toPath(), new byte[1]);
		}
		assertEquals(Arrays.asList(new File(slozka, "europe/czech-republic.routes.map"), new File(slozka, "europe/germany/bayern.contours.MAP")),
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
		Files.write(new File(stara, "czech-republic.routes.map").toPath(), new byte[] { 1 });
		Files.write(new File(mapy, "vrstvy/vrstvy.json").toPath(), "{}".getBytes(StandardCharsets.US_ASCII));
		final SpravaMapModel model = new SpravaMapModel(KatalogVrstevTest.mapy(), KatalogVrstev.vestaveny());
		model.nastavProTest(() -> mapy, e -> {
		});
		model.obnovStavy();
		model.pockej();
		assertFalse(new File(mapy, "vrstvy").exists());
		assertTrue(new File(mapy, "layers/europe/czech-republic.routes.map").isFile());
		assertFalse("kopie katalogu ve starém formátu pryč", new File(mapy, "layers/vrstvy.json").exists());
		assertEquals(new File(mapy, "layers"), model.getSlozkaVrstev());

		// Když už layers/ existuje, stará složka se nechá být.
		assertTrue(new File(mapy, "vrstvy").mkdir());
		model.obnovStavy();
		model.pockej();
		assertTrue(new File(mapy, "vrstvy").isDirectory());
		assertTrue(new File(mapy, "layers/europe/czech-republic.routes.map").isFile());
	}
}
