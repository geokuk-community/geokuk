package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpServer;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/** Symbol SVG z tématu nic nenačítá zvenku, odkazy dovnitř souboru a data: fungují. */
public class SymbolTematuOdkazyTest {

	private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"20\" height=\"20\">";
	private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private HttpServer server;
	private final AtomicInteger pozadavku = new AtomicInteger();
	private String adresa;

	@Before
	public void setUp() throws Exception {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", ex -> {
			pozadavku.incrementAndGet();
			ex.sendResponseHeaders(404, -1);
			ex.close();
		});
		server.start();
		adresa = "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + server.getAddress().getPort();
	}

	@After
	public void tearDown() {
		server.stop(0);
	}

	private static void vykresli(final String svg) throws IOException {
		new GrafikaOfflineMapy(null).renderSvg(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)), 1, 20, 20, 100, svg.hashCode());
	}

	private void odmitne(final String svg) {
		try {
			vykresli(svg);
			Assert.fail("symbol s odkazem ven se vykreslil: " + svg);
		} catch (final IOException e) {
			// symbol se nevykreslí
		}
	}

	@Test
	public void odkazVenSeNenacte() throws Exception {
		odmitne(SVG + "<use xlink:href=\"" + adresa + "/use.svg#a\"/></svg>");
		odmitne(SVG + "<image xlink:href=\"" + adresa + "/obrazek.png\" width=\"20\" height=\"20\"/></svg>");
		odmitne(SVG + "<image href=\"" + adresa + "/svg2.png\" width=\"20\" height=\"20\"/></svg>");
		odmitne(SVG + "<image xlink:href=\"&#104;ttp" + adresa.substring(4) + "/entita.png\" width=\"20\" height=\"20\"/></svg>");
		odmitne(SVG + "<image xlink:href=\"obrazek.png\" width=\"20\" height=\"20\"/></svg>");
		odmitne(SVG + "<rect width=\"20\" height=\"20\" fill=\"url(" + adresa + "/fill.svg#g)\"/></svg>");
		odmitne(SVG + "<rect width=\"20\" height=\"20\" style=\"fill:url( '" + adresa + "/style.svg#g' )\"/></svg>");
		odmitne(SVG + "<rect width=\"20\" height=\"20\" clip-path=\"URL(" + adresa + "/clip.svg#c)\"/></svg>");
		odmitne(SVG + "<rect width=\"20\" height=\"20\" filter=\"url(" + adresa + "/filter.svg#f)\"/></svg>");
		odmitne(SVG + "<style>rect{fill:url(\"" + adresa + "/css.svg#g\")}</style><rect width=\"20\" height=\"20\"/></svg>");
		Assert.assertEquals("požadavky na server", 0, pozadavku.get());
	}

	/** Téma se symbolem, který odkazuje ven, se načte a mapa se vykreslí bez toho symbolu. */
	@Test
	public void temaSOdkazemVenKresliBezSymbolu() throws Exception {
		final File slozka = tmp.newFolder("mapy");
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final File zip = new File(tmp.getRoot(), "pivo.zip");
		try (FileOutputStream fos = new FileOutputStream(zip); ZipOutputStream out = new ZipOutputStream(fos)) {
			out.putNextEntry(new ZipEntry("pivo.xml"));
			out.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rendertheme xmlns=\"http://mapsforge.org/renderTheme\" version=\"5\" map-background=\"#FFFFFF\">"
					+ "<rule e=\"node\" k=\"amenity\" v=\"pub\"><symbol src=\"file:pivo.svg\" symbol-width=\"40\"/></rule></rendertheme>").getBytes(StandardCharsets.UTF_8));
			out.putNextEntry(new ZipEntry("pivo.svg"));
			out.write((SVG + "<image xlink:href=\"" + adresa + "/pivo.png\" width=\"20\" height=\"20\"/></svg>").getBytes(StandardCharsets.UTF_8));
		}
		final OfflineMapy mapy = new OfflineMapy(() -> {});
		mapy.nastav(slozka, TemaOfflineMapy.zeSouboru(zip, null));
		final OfflineRenderer r = mapy.pouzij();
		try {
			Assert.assertNull(r.getChybaTematu());
			r.vyrendruj(KaLoc.ofJZ(new Wgs(50.003, 14.403).toMou(), 15));
		} finally {
			r.skonci();
			mapy.zavri();
		}
		Assert.assertEquals(0, pozadavku.get());
	}

	@Test
	public void odkazDovnitrADataFunguji() throws Exception {
		vykresli(SVG + "<defs><circle id=\"k\" cx=\"10\" cy=\"10\" r=\"8\" fill=\"#C08000\"/><linearGradient id=\"g\"><stop offset=\"0\" stop-color=\"red\"/><stop offset=\"1\" stop-color=\"blue\"/></linearGradient></defs>"
				+ "<use xlink:href=\"#k\"/><rect width=\"5\" height=\"5\" fill=\"url(#g)\" style=\"stroke:url( '#g' )\"/><image xlink:href=\"" + PNG + "\" width=\"20\" height=\"20\"/></svg>");
		Assert.assertEquals(0, pozadavku.get());
	}
}
