package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.junit.*;

import com.sun.net.httpserver.HttpServer;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Hromadné stahování víc dlaždic, než se vejde do fronty dávkového web executoru. */
public class StahovaniPlnaFrontaTest {

	private static final int N = 300;

	private HttpServer server;
	private final AtomicInteger prijato = new AtomicInteger();
	private volatile long zpozdeniMs;
	private EKaType mapa;
	private KachleZiskavac z;

	@Before
	public void setUp() throws Exception {
		final ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", png);
		final byte[] data = png.toByteArray();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		server.createContext("/", ex -> {
			prijato.incrementAndGet();
			try {
				Thread.sleep(zpozdeniMs);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			ex.sendResponseHeaders(200, data.length);
			ex.getResponseBody().write(data);
			ex.close();
		});
		server.start();
		mapa = UzivatelskeMapyPristup.sHromadnymStahovanim("pokus", "http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{x}/{y}", 10, 18);
		z = new KachleZiskavac();
		z.inject(new OnofflineModel() {
			@Override
			public boolean isOnlineMode() {
				return true;
			}
		});
		z.inject(new KachleModel() {
			@Override
			public boolean isUkladatMapyNaDisk() {
				return true;
			}
		});
		z.setKachleManager(new KachleManager() {
			@Override
			public boolean exists(final Ka ki) {
				return false;
			}

			@Override
			public java.awt.Image load(final Ka ki) {
				return null;
			}

			@Override
			public boolean save(final Collection<ItemToSave> i) {
				return true;
			}
		});
	}

	@After
	public void tearDown() {
		server.stop(0);
	}

	private Ka dlazdice(final int i) {
		return new Ka(KaLoc.ofSZ(new Wgs(50 + i % 20 * 0.05, 14 + i / 20 * 0.05).toMou(), 14), mapa);
	}

	private void zadejDavku(final AtomicInteger ok, final AtomicInteger chyb) {
		for (int i = 0; i < N; i++) {
			z.ziskejObsah(new KaOneReq(dlazdice(i), stav -> (stav.getThr() == null ? ok : chyb).incrementAndGet(), Priority.STAHOVANI),
					DiagnosticsData.create(null, "dávka", (a, b) -> {}));
		}
	}

	@Test(timeout = 120000)
	public void vsechnyDlazdiceDostanouOdpoved() throws Exception {
		zpozdeniMs = 50;
		final AtomicInteger ok = new AtomicInteger(), chyb = new AtomicInteger();
		zadejDavku(ok, chyb);
		final long konec = System.currentTimeMillis() + 60000;
		while (ok.get() + chyb.get() < N && System.currentTimeMillis() < konec) {
			Thread.sleep(200);
		}
		Assert.assertEquals("ok " + ok + " chyb " + chyb + ", server přijal " + prijato, N, ok.get() + chyb.get());
		Assert.assertEquals(0, chyb.get());
		Assert.assertEquals("každá dlaždice se stáhla jednou", N, prijato.get());
	}

	/** Dávka stahuje při plné frontě ve svém vlákně; obrazovka chce tytéž dlaždice a nesmí na stahování čekat. */
	@Test(timeout = 120000)
	public void obrazovkaNecekaNaStahovaniDavky() throws Exception {
		zpozdeniMs = 1000;
		final AtomicInteger ok = new AtomicInteger(), chyb = new AtomicInteger();
		final Thread davka = new Thread(() -> zadejDavku(ok, chyb), "dávka");
		davka.setDaemon(true);
		davka.start();
		Thread.sleep(3000);
		long nejdele = 0;
		for (int kolo = 0; kolo < 3; kolo++) {
			for (int i = 0; i < N; i++) {
				final long zacatek = System.nanoTime();
				z.ziskejObsah(new KaOneReq(dlazdice(i), stav -> {}, Priority.KACHLE), DiagnosticsData.create(null, "obrazovka", (a, b) -> {})).cancel();
				nejdele = Math.max(nejdele, System.nanoTime() - zacatek);
			}
		}
		Assert.assertTrue("nejdelší žádost obrazovky " + TimeUnit.NANOSECONDS.toMillis(nejdele) + " ms", nejdele < TimeUnit.MILLISECONDS.toNanos(300));
	}
}
