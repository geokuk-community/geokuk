package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.*;

import com.sun.net.httpserver.HttpServer;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** U podkladu s omezeným územím je 404 prázdná dlaždice: bez chyby a bez dalšího stahování. */
public class KachleMimoUzemiTest {

	private HttpServer server;
	private final AtomicInteger pozadavku = new AtomicInteger();
	private final AtomicInteger kod = new AtomicInteger(404);
	private final CountDownLatch nacteni = new CountDownLatch(1);
	private KachleZiskavac ziskavac;
	private Ka kachle;

	@Before
	public void setUp() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			pozadavku.incrementAndGet();
			ex.sendResponseHeaders(kod.get(), -1);
			ex.close();
		});
		server.start();
		kachle = mapa(404);

		ziskavac = new KachleZiskavac();
		ziskavac.inject(new OnofflineModel() {
			@Override
			public boolean isOnlineMode() {
				return true;
			}
		});
		ziskavac.setKachleManager(new KachleManager() {
			@Override
			public boolean exists(final Ka ki) {
				return false;
			}

			@Override
			public Image load(final Ka ki) {
				try {
					nacteni.await();
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return null;
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				return true;
			}
		});
	}

	private Ka mapa(final Integer... kodyMimoUzemi) {
		return new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 12), UzivatelskeMapyPristup.sOmezenymUzemim("omezena", "http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{y}/{x}", kodyMimoUzemi));
	}

	@Test(timeout = 30000)
	public void kod503MimoUzemiJenUPodkladuKteryHoTakHlasi() throws Exception {
		nacteni.countDown();
		kod.set(503);
		kachle = mapa(404, 503);
		Assert.assertSame(KachleZiskavac.PRAZDNA_MIMO_UZEMI, ziskej().getImg());
		ziskavac.clearMemoryCache();
		kachle = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13), kachle.getType());
		Assert.assertSame(KachleZiskavac.PRAZDNA_MIMO_UZEMI, ziskej().getImg());
		kachle = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 14), UzivatelskeMapyPristup.sOmezenymUzemim("jen404", "http://127.0.0.1:" + server.getAddress().getPort() + "/a/{z}/{y}/{x}", 404));
		Assert.assertNotNull("503 u podkladu, který mimo území vrací 404, je chyba", ziskej().getThr());
	}

	@After
	public void tearDown() {
		server.stop(0);
	}

	private KachloStav ziskej() throws Exception {
		final CompletableFuture<KachloStav> stav = new CompletableFuture<>();
		ziskavac.ziskejObsah(new KaOneReq(kachle, stav::complete, Priority.KACHLE), DiagnosticsData.create(null, null, null));
		return stav.get(10, TimeUnit.SECONDS);
	}

	@Test(timeout = 30000)
	public void mimoUzemiPrazdnaDlazdiceBezChybyAJenJednouStazena() throws Exception {
		nacteni.countDown();
		final KachloStav prvni = ziskej();
		Assert.assertNull(prvni.getThr());
		Assert.assertSame(KachleZiskavac.PRAZDNA_MIMO_UZEMI, prvni.getImg());
		ziskavac.clearMemoryCache();
		final KachloStav druhy = ziskej();
		Assert.assertNull(druhy.getThr());
		Assert.assertSame(KachleZiskavac.PRAZDNA_MIMO_UZEMI, druhy.getImg());
		Assert.assertEquals("server dostal jen první požadavek", 1, pozadavku.get());
	}
}
