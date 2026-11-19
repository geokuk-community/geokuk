package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpServer;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Dávkové stahování nejde na hostitele s omezením ve víc souběžných spojeních, než je limit. */
public class KachleSoubehDavkyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private HttpServer server;
	private final AtomicInteger soubezne = new AtomicInteger();
	private final AtomicInteger nejvic = new AtomicInteger();
	private KachleZiskavac ziskavac;

	@Before
	public void setUp() throws Exception {
		final ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", png);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		server.createContext("/", ex -> {
			nejvic.accumulateAndGet(soubezne.incrementAndGet(), Math::max);
			try {
				Thread.sleep(300);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			soubezne.decrementAndGet();
			ex.getResponseHeaders().add("Content-Type", "image/png");
			ex.sendResponseHeaders(200, png.size());
			ex.getResponseBody().write(png.toByteArray());
			ex.close();
		});
		server.start();
		final File soubor = new File(tmp.getRoot(), "davka" + UzivatelskeMapy.PRIPONA);
		Files.write(soubor.toPath(), ("nazev=Dávka\nurl=http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{x}/{y}.png\n").getBytes(StandardCharsets.UTF_8));
		UzivatelskeMapyPristup.nacti(tmp.getRoot());

		ziskavac = new KachleZiskavac();
		ziskavac.inject(new OnofflineModel() {
			@Override
			public boolean isOnlineMode() {
				return true;
			}
		});
		ziskavac.setKachleManager(new KachleManager() {
			@Override
			public boolean exists(final Ka ki) {
				return false;
			}

			@Override
			public Image load(final Ka ki) {
				return null;
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				return true;
			}
		});
		ziskavac.setSoubehDavky(new SoubehNaHostitele(hostitel -> "127.0.0.1".equals(hostitel) ? 2 : 0));
	}

	@After
	public void tearDown() {
		server.stop(0);
		UzivatelskeMapyPristup.vycisti();
	}

	@Test(timeout = 30000)
	public void sestSoubeznychPozadavkuJdeNaServerNejvysDvakrat() throws Exception {
		final List<CompletableFuture<KachloStav>> vysledky = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			final Ka ka = new Ka(KaLoc.ofJZ(new Mou(0x40000000 + i * 0x100000, 0x20000000), 12), EKaType.podleJmena("user-davka"));
			final CompletableFuture<KachloStav> f = new CompletableFuture<>();
			ziskavac.ziskejObsah(new KaOneReq(ka, f::complete, Priority.STAHOVANI), DiagnosticsData.create(null, null, null));
			vysledky.add(f);
		}
		for (final CompletableFuture<KachloStav> f : vysledky) {
			final KachloStav stav = f.get(20, TimeUnit.SECONDS);
			Assert.assertNull(String.valueOf(stav.getThr()), stav.getThr());
		}
		Assert.assertEquals("souběžně se mělo stahovat právě 2", 2, nejvic.get());
	}

	@Test(timeout = 30000)
	public void onlineProhlizeniNaOmezenemHostiteliNeniOmezeno() throws Exception {
		final List<CompletableFuture<KachloStav>> vysledky = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			final Ka ka = new Ka(KaLoc.ofJZ(new Mou(0x40000000 + i * 0x100000, 0x20000000), 12), EKaType.podleJmena("user-davka"));
			final CompletableFuture<KachloStav> f = new CompletableFuture<>();
			ziskavac.ziskejObsah(new KaOneReq(ka, f::complete, Priority.KACHLE), DiagnosticsData.create(null, null, null));
			vysledky.add(f);
		}
		for (final CompletableFuture<KachloStav> f : vysledky) {
			final KachloStav stav = f.get(20, TimeUnit.SECONDS);
			Assert.assertNull(String.valueOf(stav.getThr()), stav.getThr());
		}
		Assert.assertTrue("omezení hostitele se týká jen dávky, souběžně bylo " + nejvic.get(), nejvic.get() > 2);
	}

	@Test
	public void vychoziOmezeniDavkyJeProServeryBezHromadnehoStahovani() {
		final KachleZiskavac vychozi = new KachleZiskavac();
		Assert.assertEquals(2, vychozi.soubehDavky.pro("tile.openstreetmap.org").availablePermits());
		Assert.assertNull(vychozi.soubehDavky.pro("freemap.sk"));
	}
}
