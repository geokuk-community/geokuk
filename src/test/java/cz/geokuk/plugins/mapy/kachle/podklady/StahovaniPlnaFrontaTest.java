package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Hromadné stahování z pomalého serveru zaplní frontu dávky; i dlaždice, které stáhne volající vlákno, musí dostat odpověď. */
public class StahovaniPlnaFrontaTest {

	private static final int DLAZDIC = 300;

	@Test(timeout = 120_000)
	public void kazdaDlazdiceDostaneOdpovedIPresPlnouFrontu() throws Exception {
		final ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", png);
		final byte[] data = png.toByteArray();
		final ExecutorService vlaknaServeru = Executors.newCachedThreadPool();
		final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(vlaknaServeru);
		final AtomicInteger pozadavku = new AtomicInteger();
		server.createContext("/", ex -> {
			pozadavku.incrementAndGet();
			try {
				Thread.sleep(100);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			ex.sendResponseHeaders(200, data.length);
			ex.getResponseBody().write(data);
			ex.close();
		});
		server.start();
		try {
			final EKaType mapa = UzivatelskeMapyPristup.sHromadnymStahovanim("plna-fronta", "http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{x}/{y}", 10, 18);
			final KachleZiskavac ziskavac = new KachleZiskavac();
			ziskavac.inject(new OnofflineModel() {
				@Override
				public boolean isOnlineMode() {
					return true;
				}
			});
			ziskavac.inject(new KachleModel() {
				@Override
				public boolean isUkladatMapyNaDisk() {
					return true;
				}
			});
			ziskavac.setKachleManager(new KachleManager() {
				@Override
				public boolean exists(final Ka ki) {
					return false;
				}

				@Override
				public java.awt.Image load(final Ka ki) {
					return null;
				}

				@Override
				public boolean save(final Collection<ItemToSave> polozky) {
					return true;
				}
			});
			final AtomicInteger hotovo = new AtomicInteger();
			final AtomicInteger chyb = new AtomicInteger();
			for (int i = 0; i < DLAZDIC; i++) {
				final KaLoc loc = KaLoc.ofSZ(new Wgs(50 + i % 20 * 0.05, 14 + i / 20 * 0.05).toMou(), 14);
				ziskavac.ziskejObsah(new KaOneReq(new Ka(loc, mapa), stav -> (stav.getThr() == null ? hotovo : chyb).incrementAndGet(), Priority.STAHOVANI),
						DiagnosticsData.create(null, "plna-fronta", (a, b) -> {}));
			}
			final long konec = System.currentTimeMillis() + 60_000;
			while (hotovo.get() + chyb.get() < DLAZDIC && System.currentTimeMillis() < konec) {
				Thread.sleep(100);
			}
			Assert.assertEquals("odpovědí (chyb " + chyb + ", server dostal " + pozadavku + ")", DLAZDIC, hotovo.get() + chyb.get());
			Assert.assertEquals("chyb", 0, chyb.get());
			Assert.assertEquals("každá dlaždice se stáhla jednou", DLAZDIC, pozadavku.get());
		} finally {
			server.stop(0);
			vlaknaServeru.shutdownNow();
		}
	}
}
