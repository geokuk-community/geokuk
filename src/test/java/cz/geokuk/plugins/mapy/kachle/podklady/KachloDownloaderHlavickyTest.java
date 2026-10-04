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
	private byte[] jineTelo;
	private final List<Map<String, List<String>>> hlavicky = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", png);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			hlavicky.add(new TreeMap<>(ex.getRequestHeaders()));
			if (jineTelo != null) {
				ex.sendResponseHeaders(200, jineTelo.length);
				try (OutputStream out = ex.getResponseBody()) {
					out.write(jineTelo);
				}
				return;
			}
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
		server.createContext("/presmeruj/", ex -> {
			hlavicky.add(new TreeMap<>(ex.getRequestHeaders()));
			ex.getResponseHeaders().add("Location", presmerovatNa);
			ex.sendResponseHeaders(302, -1);
			ex.close();
		});
		server.start();
	}

	private String presmerovatNa;

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
		Assert.assertEquals(Collections.singletonList("Geokuk/" + FConst.VERSION), hlavicky.get(0).get("User-agent"));
		Assert.assertNull(hlavicky.get(0).get("Referer"));
	}

	private static byte[] jpeg() throws Exception {
		final BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		final Random rnd = new Random(1);
		for (int x = 0; x < 256; x++) {
			for (int y = 0; y < 256; y++) {
				img.setRGB(x, y, rnd.nextInt());
			}
		}
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "jpg", out);
		return out.toByteArray();
	}

	@Test(expected = java.io.IOException.class)
	public void useknutyJpegSeOdmitneIBezNesouhlasuDelky() throws Exception {
		final byte[] cely = jpeg();
		jineTelo = Arrays.copyOf(cely, cely.length / 2);
		new KachloDownloader().downloadImage(url());
	}

	@Test
	public void celyJpegSePrijme() throws Exception {
		jineTelo = jpeg();
		Assert.assertEquals(jineTelo.length, new KachloDownloader().downloadImage(url()).getData().length);
	}

	@Test
	public void hlavickyUzivatelskeMapyNejdouNaJinyServer() throws Exception {
		final int port = server.getAddress().getPort();
		presmerovatNa = "http://localhost:" + port + "/1/2/3.png";
		new KachloDownloader().downloadImage(new URL("http://127.0.0.1:" + port + "/presmeruj/1/2/3.png"), Collections.singletonMap("X-Api-Key", "Bearer tajne"));
		Assert.assertEquals(2, hlavicky.size());
		Assert.assertEquals(Collections.singletonList("Bearer tajne"), hlavicky.get(0).get("X-api-key"));
		Assert.assertNull("jiný server hlavičku nedostane", hlavicky.get(1).get("X-api-key"));
	}

	@Test
	public void hlavickyUzivatelskeMapyZustanouPriPresmerovaniNaStejnyServer() throws Exception {
		final int port = server.getAddress().getPort();
		presmerovatNa = "http://127.0.0.1:" + port + "/1/2/3.png";
		new KachloDownloader().downloadImage(new URL("http://127.0.0.1:" + port + "/presmeruj/1/2/3.png"), Collections.singletonMap("X-Api-Key", "Bearer tajne"));
		Assert.assertEquals(Collections.singletonList("Bearer tajne"), hlavicky.get(1).get("X-api-key"));
	}
}
