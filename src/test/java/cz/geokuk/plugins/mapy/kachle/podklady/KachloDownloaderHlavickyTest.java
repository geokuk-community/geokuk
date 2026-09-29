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

	private static final int ZA_OBRAZKEM = 100;

	private HttpServer server;
	private boolean neuplne;
	private boolean sBajtyZaObrazkem;
	private final ByteArrayOutputStream png = new ByteArrayOutputStream();
	private final List<Map<String, List<String>>> hlavicky = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", png);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			hlavicky.add(new TreeMap<>(ex.getRequestHeaders()));
			// neúplná odpověď: hlavička slíbí víc bajtů, než server pošle
			ex.sendResponseHeaders(200, neuplne || sBajtyZaObrazkem ? png.size() + ZA_OBRAZKEM : png.size());
			try (OutputStream out = ex.getResponseBody()) {
				png.writeTo(out);
				if (sBajtyZaObrazkem) {
					// zvlášť, ať je konec těla až za tím, co si dekodér obrázku stihne přečíst
					out.flush();
					try {
						Thread.sleep(300);
					} catch (final InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					out.write(new byte[ZA_OBRAZKEM]);
				}
			}
		});
		server.start();
	}

	private URL url() throws Exception {
		return new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/1/2/3.png");
	}

	@After
	public void tearDown() {
		server.stop(0);
	}

	@Test(expected = java.io.IOException.class)
	public void neuplnaDlazdiceSeOdmitne() throws Exception {
		neuplne = true;
		new KachloDownloader().downloadImage(url());
	}

	@Test
	public void dlazdiceSBajtyZaObrazkemSePrijme() throws Exception {
		// dekodér obrázku přestane číst, jakmile má obrázek; zbytek těla odpovědi nesmí vypadat jako useknutá dlaždice
		sBajtyZaObrazkem = true;
		Assert.assertEquals(png.size() + ZA_OBRAZKEM, new KachloDownloader().downloadImage(url()).getData().length);
	}

	@Test
	public void kazdyPozadavekIdentifikujeGeokuk() throws Exception {
		new KachloDownloader().downloadImage(url());
		Assert.assertEquals(Collections.singletonList("Geokuk/" + FConst.VERSION + " (+" + FConst.WEB_PAGE_URL + ")"), hlavicky.get(0).get("User-agent"));
		Assert.assertNull(hlavicky.get(0).get("Referer"));
	}
}
