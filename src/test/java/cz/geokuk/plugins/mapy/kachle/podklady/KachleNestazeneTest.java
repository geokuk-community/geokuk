package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpServer;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Dlaždice, kterou server odmítl, se hned znovu nestahuje a o chybě se dozví všichni příjemci. */
public class KachleNestazeneTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private HttpServer server;
	private final AtomicInteger pozadavku = new AtomicInteger();
	private final CountDownLatch nacteni = new CountDownLatch(1);
	private KachleZiskavac ziskavac;
	private Ka kachle;

	@Before
	public void setUp() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			pozadavku.incrementAndGet();
			ex.sendResponseHeaders(500, -1);
			ex.close();
		});
		server.start();
		final File soubor = tmp.newFile(UzivatelskeMapy.SOUBOR);
		Files.write(soubor.toPath(), ("chybna.nazev=Chybná\nchybna.url=http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{x}/{y}.png\n").getBytes(StandardCharsets.UTF_8));
		UzivatelskeMapyPristup.nacti(soubor);
		kachle = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 12), EKaType.podleJmena("user-chybna"));

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

	@After
	public void tearDown() {
		server.stop(0);
		UzivatelskeMapyPristup.vycisti();
	}

	private KachloStav ziskej() throws Exception {
		final CompletableFuture<KachloStav> stav = new CompletableFuture<>();
		ziskavac.ziskejObsah(new KaOneReq(kachle, stav::complete, Priority.KACHLE), DiagnosticsData.create(null, null, null));
		return stav.get(10, TimeUnit.SECONDS);
	}

	@Test(timeout = 30000)
	public void nestazenaDlazdiceSeHnedZnovuNestahuje() throws Exception {
		nacteni.countDown();
		Assert.assertNotNull(ziskej().getThr());
		ziskavac.clearMemoryCache();
		Assert.assertNotNull(ziskej().getThr());
		Assert.assertEquals("server dostal jen první požadavek", 1, pozadavku.get());
	}

	@Test(timeout = 30000)
	public void chybuDostanouIPrijemciPoOdhlaseniJinehoPrijemce() throws Exception {
		final AtomicReference<Kanceler> prvniKanceler = new AtomicReference<>();
		prvniKanceler.set(ziskavac.ziskejObsah(new KaOneReq(kachle, stav -> prvniKanceler.get().cancel(), Priority.KACHLE), DiagnosticsData.create(null, null, null)));
		final CompletableFuture<KachloStav> druhy = new CompletableFuture<>();
		ziskavac.ziskejObsah(new KaOneReq(kachle, druhy::complete, Priority.KACHLE), DiagnosticsData.create(null, null, null));
		nacteni.countDown();
		Assert.assertNotNull(druhy.get(10, TimeUnit.SECONDS).getThr());
	}
}
