package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpServer;

import cz.geokuk.plugins.mapy.sprava.StavMapy.Faze;
import cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni;

/** Model: jedno stahování najednou, zrušení, události a stav z disku. */
public class SpravaMapModelTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private HttpServer server;
	private final CountDownLatch pustit = new CountDownLatch(1);
	private final CountDownLatch zacalo = new CountDownLatch(1);
	private byte[] data;

	@Before
	public void setUp() throws Exception {
		data = StahovacMapyTest.kukov();
		final byte[] velka = new byte[8 * 1024 * 1024];
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/maps/v5/", ex -> {
			try {
				// Velká mapa po kouscích jako pomalé spojení, aby stahování běželo, dokud ho test nezruší.
				ex.sendResponseHeaders(200, velka.length);
				final OutputStream out = ex.getResponseBody();
				for (int i = 0; i < velka.length && pustit.getCount() > 0; i += 4096) {
					out.write(velka, i, 4096);
					out.flush();
					zacalo.countDown();
					Thread.sleep(5);
				}
				out.close();
			} catch (final InterruptedException | IOException e) {
				// zrušené spojení
			} finally {
				ex.close();
			}
		});
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
	}

	@After
	public void tearDown() {
		pustit.countDown();
		server.stop(0);
	}

	@Test
	public void jednoStahovaniNajednouAZruseni() throws Exception {
		final File slozka = tmp.newFolder("mapy");
		final List<SpravaMapEvent> udalosti = new CopyOnWriteArrayList<>();
		final SpravaMapModel model = new SpravaMapModel(StahovacMapyTest.katalog(server));
		model.nastavProTestMap(() -> slozka, udalosti::add);
		final KatalogovaMapa mapa = model.getKatalog().getMapy().get(0);

		assertTrue(model.stahni(mapa));
		assertTrue(zacalo.await(10, TimeUnit.SECONDS));
		assertFalse("druhé stahování neběží souběžně", model.stahni(mapa));
		assertTrue(model.getBeziciStahovani().isPresent());
		model.zrusStahovani();
		model.pockej();
		assertFalse(model.getBeziciStahovani().isPresent());
		final StavMapy zrusena = model.getStav(mapa);
		assertEquals(zrusena.toString(), Faze.ZRUSENO, zrusena.getFaze());
		assertTrue(zrusena.isRozpracovano());
		assertEquals(Faze.ZRUSENO, udalosti.get(udalosti.size() - 1).getStav().getFaze());
		assertEquals(Faze.STAHUJE, udalosti.get(0).getStav().getFaze());

		model.zahodRozpracovane(mapa);
		model.pockej();
		assertFalse(model.getStav(mapa).isRozpracovano());
		assertEquals(0, slozka.list().length);
	}

	@Test
	public void stavZDisku() throws Exception {
		final File slozka = tmp.newFolder("mapy");
		Files.write(new File(slozka, "kukov.map").toPath(), data);
		final SpravaMapModel model = new SpravaMapModel(StahovacMapyTest.katalog(server));
		final List<SpravaMapEvent> udalosti = new CopyOnWriteArrayList<>();
		model.nastavProTestMap(() -> slozka, udalosti::add);
		final KatalogovaMapa mapa = model.getKatalog().getMapy().get(0);
		assertEquals(Mistni.NEMAME, model.getStav(mapa).getMistni());
		model.obnovStavy();
		model.pockej();
		assertEquals(Mistni.MAME, model.getStav(mapa).getMistni());
		assertEquals(1, udalosti.size());
		model.obnovStavy();
		model.pockej();
		assertEquals("beze změny bez události", 1, udalosti.size());
	}

	@Test
	public void katalogySeNactouNaPozadi() throws Exception {
		final List<String> vlakna = new CopyOnWriteArrayList<>();
		final KatalogMap mapy = StahovacMapyTest.katalog(server);
		final SpravaMapModel model = new SpravaMapModel(() -> {
			vlakna.add(Thread.currentThread().getName());
			return mapy;
		}, () -> {
			vlakna.add(Thread.currentThread().getName());
			return KatalogVrstev.vestaveny();
		});
		model.pockej();
		assertEquals(Arrays.asList("Kontrola offline map", "Kontrola offline map"), vlakna);
	}

	/** Vrstva ve složce: soubor s délkou a kontrolním součtem podle katalogu. */
	private static String vrstvaNaDisku(final File sv, final String typ, final long velikost, final String sha) throws IOException {
		final String soubor = KatalogVrstevTest.soubor(typ + ".map", typ + ".map", velikost, sha);
		final String json = KatalogVrstevTest.vrstva(typ + "/europe/czech-republic", typ, "europe/czech-republic", "map", soubor, "");
		final String cesta = "europe/czech-republic." + typ + "." + typ + ".map";
		final File f = new File(sv, cesta);
		assertTrue(f.getParentFile().isDirectory() || f.getParentFile().mkdirs());
		Files.write(f.toPath(), new byte[(int) velikost]);
		Files.write(new File(sv, cesta + StahovacVrstev.PRIPONA_SOUCTU).toPath(), sha.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		return json;
	}

	/** Nový katalog vrstev nechá stažené vrstvy stažené, dokud se jejich stav znovu nepřečte; pak přijde za všechny změny jedna událost. */
	@Test
	public void novyKatalogVrstevDrziStavyAPosleJednuUdalost() throws Exception {
		final String jinySha = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
		final File prvni = tmp.newFolder("prvni");
		final File druha = tmp.newFolder("druha");
		final KatalogVrstev puvodni = KatalogVrstev.nacti(KatalogVrstevTest.katalog(vrstvaNaDisku(new File(prvni, SpravaMapModel.SLOZKA_VRSTEV), "tourist", 1000, KatalogVrstevTest.SHA),
				vrstvaNaDisku(new File(prvni, SpravaMapModel.SLOZKA_VRSTEV), "contours", 2000, KatalogVrstevTest.SHA)), KatalogVrstevTest.mapy());
		final File sv2 = new File(druha, SpravaMapModel.SLOZKA_VRSTEV);
		vrstvaNaDisku(sv2, "tourist", 1000, KatalogVrstevTest.SHA);
		vrstvaNaDisku(sv2, "contours", 2000, KatalogVrstevTest.SHA);
		// Ve druhé složce je kopie novějšího katalogu: obě vrstvy mají jinou verzi souborů.
		Files.write(new File(sv2, SpravaMapModel.SOUBOR_KATALOGU).toPath(), KatalogVrstevTest.katalog(KatalogVrstevTest.vrstva("tourist/europe/czech-republic", "tourist", "europe/czech-republic", "map",
				KatalogVrstevTest.soubor("tourist.map", "tourist.map", 1100, jinySha), ""), KatalogVrstevTest.vrstva("contours/europe/czech-republic", "contours", "europe/czech-republic", "map",
				KatalogVrstevTest.soubor("contours.map", "contours.map", 2100, jinySha), "")).getBytes(java.nio.charset.StandardCharsets.UTF_8));

		final SpravaMapModel model = new SpravaMapModel(KatalogVrstevTest.mapy(), puvodni);
		final File[] slozka = { prvni };
		final List<cz.geokuk.framework.Event0<SpravaMapModel>> udalosti = new CopyOnWriteArrayList<>();
		final List<String> priNovemKatalogu = new CopyOnWriteArrayList<>();
		model.nastavProTest(() -> slozka[0], e -> {
			if (e instanceof KatalogVrstevEvent) {
				for (final KatalogovaVrstva v : ((KatalogVrstevEvent) e).getKatalog().getVrstvy()) {
					priNovemKatalogu.add(v.getId() + " " + model.getStav(v).getMistni());
				}
			} else {
				udalosti.add(e);
			}
		});
		model.obnovStavy();
		model.pockej();
		for (final KatalogovaVrstva v : puvodni.getVrstvy()) {
			assertEquals(StavVrstvy.Mistni.MAME, model.getStav(v).getMistni());
		}
		udalosti.clear();
		slozka[0] = druha;
		model.obnovStavy();
		model.pockej();
		assertEquals("stažené vrstvy nejsou s novým katalogem chvíli ke stažení", Arrays.asList("contours/europe/czech-republic MAME", "tourist/europe/czech-republic MAME"),
				priNovemKatalogu.stream().sorted().collect(java.util.stream.Collectors.toList()));
		for (final KatalogovaVrstva v : model.getKatalogVrstev().getVrstvy()) {
			assertEquals(StavVrstvy.Mistni.JINA_VERZE, model.getStav(v).getMistni());
			assertSame(v, model.getStav(v).getVrstva());
		}
		assertEquals("dvě změny, jedna událost: " + udalosti, 1, udalosti.size());
		assertTrue(udalosti.get(0) instanceof StavyMapEvent);
	}
}
