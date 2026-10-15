package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
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
		model.nastavProTest(() -> slozka, udalosti::add);
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
		model.nastavProTest(() -> slozka, udalosti::add);
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
}
