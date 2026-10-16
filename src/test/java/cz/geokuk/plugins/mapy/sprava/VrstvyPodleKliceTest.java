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
			final byte[] d = "latest/vrstvy.json".equals(jmeno) ? katalog == null ? null : katalog.getBytes(StandardCharsets.UTF_8) : data.get(jmeno);
			final int kod = "latest/vrstvy.json".equals(jmeno) ? kodKatalogu : 200;
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
		data.put("vrstvy-2026-10/europe_germany_bayern.trasy.map", StahovacMapyTest.kukov());
		data.put("vrstvy-2026-10/europe_germany_bayern.dem.zip", "dem".getBytes(StandardCharsets.US_ASCII));
		final byte[] t = data.get("vrstvy-2026-10/europe_germany_bayern.trasy.map");
		final byte[] d = data.get("vrstvy-2026-10/europe_germany_bayern.dem.zip");
		return "{\"format\": 1, \"zdroje\": [\"" + zaklad() + "\"], \"vrstvy\": ["
				+ KatalogVrstevTest.vrstva("trasy/europe/germany/bayern", "trasy", "europe/germany/bayern", "map", KatalogVrstevTest.soubor("europe_germany_bayern.trasy.map",
						"europe/germany/bayern.trasy.map", "vrstvy-2026-10/europe_germany_bayern.trasy.map", t.length, sha(t)), "")
				+ "," + KatalogVrstevTest.vrstva("dem/europe/germany/bayern", "dem", "europe/germany/bayern", "hgt-zip", KatalogVrstevTest.soubor(
						"europe_germany_bayern.dem.zip", "europe/germany/bayern.dem.zip", "vrstvy-2026-10/europe_germany_bayern.dem.zip", d.length, sha(d)), "")
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
		final File slozka = new File(tmp.getRoot(), "offline-mapy/vrstvy");
		final StahovacMapy http = new StahovacMapy("GeoKuk/test", () -> {
		});
		final StahovacVrstev stahovac = new StahovacVrstev(http);
		for (final KatalogovaVrstva v : Arrays.asList(trasy, dem)) {
			final StavVrstvy s = stahovac.stahni(StavVrstvy.novy(v), slozka, () -> false, x -> {
			});
			assertEquals(s.toString(), Faze.HOTOVO, s.getFaze());
			assertEquals(Mistni.MAME, s.getMistni());
		}
		final File naDisku = new File(slozka, "europe/germany/bayern.trasy.map");
		assertArrayEquals(StahovacMapyTest.kukov(), Files.readAllBytes(naDisku.toPath()));
		assertTrue(new File(slozka, "europe/germany/bayern.dem.zip").isFile());
		assertEquals(Collections.singletonList(naDisku), SouboryVrstev.mapy(slozka));
		assertEquals(Collections.singletonList(new File(slozka, "europe/germany/bayern.dem.zip")), SouboryVrstev.dem(slozka));
		assertSame(trasy, k.podleCesty("europe/germany/bayern.trasy.map").get());
	}

	@Test
	public void souboryVrstevNajdouJenVrstvy() throws IOException {
		final File slozka = tmp.newFolder("vrstvy");
		new File(slozka, "europe/germany").mkdirs();
		for (final String f : Arrays.asList("europe/czech-republic.trasy.map", "europe/czech-republic.trasy.map.sha256", "europe/germany/bayern.trasy.map.stahuje",
				"europe/germany/bayern.vrstevnice.MAP", "europe/germany/bayern.dem.zip", "europe/germany/N48E011.hgt", "._skryty.map", "vrstvy.json")) {
			Files.write(new File(slozka, f).toPath(), new byte[1]);
		}
		assertEquals(Arrays.asList(new File(slozka, "europe/czech-republic.trasy.map"), new File(slozka, "europe/germany/bayern.vrstevnice.MAP")),
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
		model.urlKataloguVrstev = URI.create(zaklad() + "latest/vrstvy.json");
		assertEquals("vestavěný je prázdný", 0, model.getKatalogVrstev().getVrstvy().size());

		model.obnovKatalogVrstev();
		model.pockej();
		assertEquals("Katalog vrstev na serveru zatím není.", posledni(udalosti).getChyba());
		assertEquals(0, model.getKatalogVrstev().getVrstvy().size());

		katalog = "{\"format\": 1, \"zdroje\": [], \"vrstvy\": []}";
		model.obnovKatalogVrstev();
		model.pockej();
		assertNotNull("vadný katalog se nepřevezme", posledni(udalosti).getChyba());
		assertFalse(new File(mapy, "vrstvy/vrstvy.json").exists());

		katalog = katalogBavorska();
		model.obnovKatalogVrstev();
		model.pockej();
		assertNull(posledni(udalosti).getChyba());
		assertEquals(2, model.getKatalogVrstev().getVrstvy().size());
		assertSame(model.getKatalogVrstev(), posledni(udalosti).getKatalog());
		assertEquals(katalog, new String(Files.readAllBytes(new File(mapy, "vrstvy/vrstvy.json").toPath()), StandardCharsets.UTF_8));
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
}
