package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

import org.junit.*;

import com.sun.net.httpserver.HttpServer;

/** Spojení se po chybě a přesměrování znovupoužije a pool dávky využije celý souběh. */
public class KachloDownloaderSpojeniTest {

	private HttpServer server;

	@After
	public void tearDown() {
		if (server != null) {
			server.stop(0);
		}
	}

	private List<Integer> zavolejDvakrat(final int kod, final String cesta) throws Exception {
		final List<Integer> porty = Collections.synchronizedList(new ArrayList<>());
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			porty.add(ex.getRemoteAddress().getPort());
			final byte[] telo = "chyba".getBytes();
			if (kod / 100 == 3) {
				ex.getResponseHeaders().add("Location", "/cil");
			}
			ex.sendResponseHeaders(kod, telo.length);
			try (OutputStream out = ex.getResponseBody()) {
				out.write(telo);
			}
		});
		server.start();
		for (int i = 0; i < 2; i++) {
			try {
				new KachloDownloader().downloadImage(new URL("http://127.0.0.1:" + server.getAddress().getPort() + cesta));
			} catch (final java.io.IOException e) {
				// očekávané, jde o spojení
			}
		}
		return porty;
	}

	@Test
	public void chybovaOdpovedNezavreSpojeni() throws Exception {
		final List<Integer> porty = zavolejDvakrat(404, "/1/2/3.png");
		Assert.assertEquals(2, porty.size());
		Assert.assertEquals("Druhý požadavek měl použít stejné spojení", porty.get(0), porty.get(1));
	}

	@Test
	public void presmerovaniNezavreSpojeni() throws Exception {
		final List<Integer> porty = zavolejDvakrat(302, "/1/2/3.png");
		// každé volání: přesměrování + cíl (opět 302, do vyčerpání), všechny na jednom spojení
		Assert.assertEquals(1, new HashSet<>(porty).size());
	}

	@Test
	public void maxConnectionsOdpovidaSoubehu() {
		new KachloDownloader();
		Assert.assertEquals(String.valueOf(KachleZiskavac.NTHREADS_WEB_CORE_ONLINE + KachleZiskavac.NTHREADS_WEB_MAXIMUM_BACTH), System.getProperty("http.maxConnections"));
	}

	@Test
	public void poolDavkyVyuzijeMaximumVlaken() throws Exception {
		final java.util.concurrent.ThreadPoolExecutor exec = KachleZiskavac.vytvorWebBatch();
		final CountDownLatch vsichniBezi = new CountDownLatch(KachleZiskavac.NTHREADS_WEB_MAXIMUM_BACTH);
		final CountDownLatch pust = new CountDownLatch(1);
		try {
			for (int i = 0; i < KachleZiskavac.NTHREADS_WEB_MAXIMUM_BACTH; i++) {
				exec.execute(() -> {
					vsichniBezi.countDown();
					try {
						pust.await();
					} catch (final InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				});
			}
			Assert.assertTrue("Všechny úlohy měly běžet souběžně", vsichniBezi.await(5, TimeUnit.SECONDS));
		} finally {
			pust.countDown();
			exec.shutdownNow();
		}
	}
}
