package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpServer;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Dlaždice, jejíž stahování už běží, se po zrušení a novém vyžádání nestahuje podruhé. */
public class KachleRozbehnuteStazeniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private HttpServer server;
	private final AtomicInteger pozadavku = new AtomicInteger();
	private final CountDownLatch serverDostal = new CountDownLatch(1);
	private final CountDownLatch serverOdpovi = new CountDownLatch(1);
	private KachleZiskavac ziskavac;
	private Ka kachle;

	@Before
	public void setUp() throws Exception {
		final ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", png);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		server.createContext("/", ex -> {
			pozadavku.incrementAndGet();
			serverDostal.countDown();
			try {
				serverOdpovi.await(10, TimeUnit.SECONDS);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			ex.getResponseHeaders().add("Content-Type", "image/png");
			ex.sendResponseHeaders(200, png.size());
			ex.getResponseBody().write(png.toByteArray());
			ex.close();
		});
		server.start();
		final File soubor = new File(tmp.getRoot(), "pomala" + UzivatelskeMapy.PRIPONA);
		Files.write(soubor.toPath(), ("nazev=Pomalá\nurl=http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{x}/{y}.png\n").getBytes(StandardCharsets.UTF_8));
		UzivatelskeMapyPristup.nacti(tmp.getRoot());
		kachle = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 12), EKaType.podleJmena("user-pomala"));

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
		serverOdpovi.countDown();
		server.stop(0);
		UzivatelskeMapyPristup.vycisti();
	}

	@Test(timeout = 30000)
	public void zrusenaRozbehnutaDlazdiceSeStahujeJenJednou() throws Exception {
		final Kanceler prvni = ziskavac.ziskejObsah(new KaOneReq(kachle, stav -> {}, Priority.KACHLE), DiagnosticsData.create(null, null, null));
		Assert.assertTrue("stahování začalo", serverDostal.await(10, TimeUnit.SECONDS));
		prvni.cancel();

		final CompletableFuture<KachloStav> druhy = new CompletableFuture<>();
		ziskavac.ziskejObsah(new KaOneReq(kachle, druhy::complete, Priority.KACHLE), DiagnosticsData.create(null, null, null));
		Thread.sleep(500);
		serverOdpovi.countDown();

		final KachloStav stav = druhy.get(10, TimeUnit.SECONDS);
		Assert.assertNull(String.valueOf(stav.getThr()), stav.getThr());
		Assert.assertNotNull(stav.getImg());
		Assert.assertEquals("server dostal jediný požadavek", 1, pozadavku.get());
	}
}
