package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;

import org.junit.*;

import com.sun.net.httpserver.HttpServer;

/** Chybová odpověď a pomalý server při stahování dlaždice. */
public class KachloDownloaderChybyTest {

	private HttpServer server;

	@After
	public void tearDown() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	public void omezeniServeruSePozna() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			ex.getResponseHeaders().add("Retry-After", "120");
			ex.sendResponseHeaders(429, -1);
			ex.close();
		});
		server.start();
		try {
			new KachloDownloader().downloadImage(new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/1/2/3.png"));
			Assert.fail();
		} catch (final KachloDownloader.ChybaServeru e) {
			Assert.assertEquals(429, e.getKod());
			Assert.assertTrue(e.jeOmezeni());
		}
	}

	@Test(expected = IOException.class)
	public void pomalyServerNeblokujeDonekonecna() throws Exception {
		final InputStream poBajtu = new InputStream() {
			@Override
			public int read() throws IOException {
				try {
					Thread.sleep(20);
				} catch (final InterruptedException e) {
					throw new InterruptedIOException();
				}
				return 0;
			}
		};
		try (InputStream in = new DataHoldingInputStream(poBajtu, 200)) {
			final byte[] buf = new byte[1];
			for (int i = 0; i < 1000; i++) {
				in.read(buf, 0, 1);
			}
		}
	}
}
