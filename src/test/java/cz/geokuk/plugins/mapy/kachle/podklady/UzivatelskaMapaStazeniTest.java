package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import javax.imageio.ImageIO;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpServer;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.program.FConst;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Uživatelská mapa ze souboru až po stažení dlaždice s jejími hlavičkami. */
public class UzivatelskaMapaStazeniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private HttpServer server;
	private final List<String> cesty = new ArrayList<>();
	private final List<Map<String, List<String>>> hlavicky = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		final ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", png);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			cesty.add(ex.getRequestURI().toString());
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
		UzivatelskeMapyPristup.vycisti();
	}

	@Test
	public void stahneDlazdiciSHlavickami() throws Exception {
		final int port = server.getAddress().getPort();
		final File soubor = new File(tmp.getRoot(), "mistni" + UzivatelskeMapy.PRIPONA);
		Files.write(soubor.toPath(), ("nazev=Místní\n" + "url=http://127.0.0.1:" + port + "/t/{z}/{x}/{y}.png?klic=abc\n" + "hlavicka.Referer=https://example.org/\n"
				+ "hlavicka.User-Agent=Geokuk/{verze}\n").getBytes(StandardCharsets.UTF_8));
		Assert.assertEquals(Collections.emptyList(), UzivatelskeMapyPristup.nacti(tmp.getRoot()));
		final EKaType mapa = EKaType.podleJmena("user-mistni");

		final KaLoc loc = KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 12);
		final ImageWithData obrazek = new KachloDownloader().downloadImage(new Ka(loc, mapa).getUrl(), mapa.getHlavicky());

		Assert.assertNotNull(obrazek.getImg());
		Assert.assertEquals(Collections.singletonList("/t/12/" + loc.getFromSzUnsignedX() + "/" + loc.getFromSzUnsignedY() + ".png?klic=abc"), cesty);
		Assert.assertEquals(Collections.singletonList("https://example.org/"), hlavicky.get(0).get("Referer"));
		Assert.assertEquals(Collections.singletonList("Geokuk/" + FConst.VERSION), hlavicky.get(0).get("User-agent"));
	}

	@Test
	public void bezHlavicekPosleJenVychozi() throws Exception {
		final int port = server.getAddress().getPort();
		final File soubor = new File(tmp.getRoot(), "m" + UzivatelskeMapy.PRIPONA);
		Files.write(soubor.toPath(), ("nazev=M\nurl=http://127.0.0.1:" + port + "/{z}/{x}/{y}\n").getBytes(StandardCharsets.UTF_8));
		UzivatelskeMapyPristup.nacti(tmp.getRoot());
		final EKaType mapa = EKaType.podleJmena("user-m");
		new KachloDownloader().downloadImage(new Ka(KaLoc.ofJZ(new Mou(0, 0), 3), mapa).getUrl(), mapa.getHlavicky());
		Assert.assertNull(hlavicky.get(0).get("Referer"));
	}
}
