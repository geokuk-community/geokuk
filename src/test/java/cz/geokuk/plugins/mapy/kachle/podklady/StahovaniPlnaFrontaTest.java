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

import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Hromadné stahování víc dlaždic, než se vejde do fronty dávkového web executoru: každá dlaždice dostane odpověď. */
public class StahovaniPlnaFrontaTest {
	@Test(timeout = 120000)
	public void vsechnyDlazdiceDostanouOdpoved() throws Exception {
		final ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", png);
		final byte[] data = png.toByteArray();
		final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		final AtomicInteger prijato = new AtomicInteger();
		server.createContext("/", ex -> {
			prijato.incrementAndGet();
			try {
				Thread.sleep(50);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			ex.sendResponseHeaders(200, data.length);
			ex.getResponseBody().write(data);
			ex.close();
		});
		server.start();
		final EKaType mapa = UzivatelskeMapyPristup.sHromadnymStahovanim("pokus", "http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{x}/{y}", 10, 18);
		final KachleZiskavac z = new KachleZiskavac();
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
		final int N = 300;
		final AtomicInteger ok = new AtomicInteger(), chyb = new AtomicInteger();
		for (int i = 0; i < N; i++) {
			final KaLoc loc = KaLoc.ofSZ(new cz.geokuk.core.coordinates.Wgs(50 + (i % 20) * 0.05, 14 + (i / 20) * 0.05).toMou(), 14);
			z.ziskejObsah(new KaOneReq(new Ka(loc, mapa), stav -> (stav.getThr() == null ? ok : chyb).incrementAndGet(), Priority.STAHOVANI), DiagnosticsData.create(null, "pokus", (a, b) -> {}));
		}
		final long konec = System.currentTimeMillis() + 60000;
		while (ok.get() + chyb.get() < N && System.currentTimeMillis() < konec) {
			Thread.sleep(200);
		}
		server.stop(0);
		Assert.assertEquals("ok " + ok + " chyb " + chyb + ", server přijal " + prijato, N, ok.get() + chyb.get());
		Assert.assertEquals(0, chyb.get());
	}
}
