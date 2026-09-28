package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;

import javax.imageio.ImageIO;

import org.junit.*;

import com.sun.net.httpserver.HttpServer;

import cz.geokuk.core.program.FConst;

public class KachloDownloaderHlavickyTest {

	private HttpServer server;
	private final List<Map<String, List<String>>> hlavicky = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		final ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", png);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			hlavicky.add(new TreeMap<>(ex.getRequestHeaders()));
			ex.sendResponseHeaders(200, png.size());
			try (OutputStream out = ex.getResponseBody()) {
				png.writeTo(out);
			}
		});
		server.start();
	}

	@After
	public void tearDown() {
		server.stop(0);
	}

	@Test
	public void kazdyPozadavekIdentifikujeGeokuk() throws Exception {
		new KachloDownloader().downloadImage(new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/1/2/3.png"));
		Assert.assertEquals(Collections.singletonList("Geokuk/" + FConst.VERSION + " (+" + FConst.WEB_PAGE_URL + ")"), hlavicky.get(0).get("User-agent"));
		Assert.assertNull(hlavicky.get(0).get("Referer"));
	}
}
