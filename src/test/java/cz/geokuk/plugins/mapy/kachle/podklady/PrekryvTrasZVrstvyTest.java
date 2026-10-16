package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;

/** Překryv tras přes online podklad se kreslí i z vrstvy tras (layers/…routes.map) bez stažené základní mapy. */
public class PrekryvTrasZVrstvyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private OfflineMapy trasy;

	@After
	public void tearDown() {
		if (trasy != null) {
			trasy.zavri();
		}
	}

	private File vrstvaTras(final File slozka, final String jmeno) throws IOException {
		final File podslozka = new File(new File(slozka, SpravaMapModel.SLOZKA_VRSTEV), "europe");
		Assert.assertTrue(podslozka.mkdirs());
		final File cil = new File(podslozka, jmeno);
		try (InputStream in = PrekryvTrasTest.class.getResourceAsStream("/offline-mapy-trasy/trasy.map")) {
			Files.copy(in, cil.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		return cil;
	}

	private BufferedImage vyrendruj(final KaLoc loc) throws IOException {
		final OfflineRenderer r = trasy.pouzij();
		try {
			return r.vyrendruj(loc);
		} finally {
			r.skonci();
		}
	}

	private static int cervenych(final BufferedImage img) {
		int n = 0;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				final int p = img.getRGB(x, y);
				if (p >>> 24 >= 200 && Math.abs((p >> 16 & 0xff) - 0xE0) < 40 && Math.abs((p >> 8 & 0xff) - 0x00) < 40 && Math.abs((p & 0xff) - 0x00) < 40) {
					n++;
				}
			}
		}
		return n;
	}

	@Test(timeout = 30000)
	public void trasyZVrstvyBezZakladniMapy() throws IOException {
		final File slozka = tmp.newFolder("offline-mapy");
		vrstvaTras(slozka, "czech-republic.routes.map");
		trasy = new OfflineMapy(() -> {}, true);
		trasy.nastav(slozka, TemaOfflineMapy.TRASY);
		final BufferedImage img = vyrendruj(PrekryvTrasTest.PESINA_Z16);
		Assert.assertTrue("červená trasa z vrstvy tras", cervenych(img) > 50);
	}

	/** Vrstevnice a jiné vrstvy do překryvu tras nepatří. */
	@Test(timeout = 30000)
	public void jinaVrstvaNeniPrekryvTras() throws IOException {
		final File slozka = tmp.newFolder("offline-mapy");
		vrstvaTras(slozka, "czech-republic.contours.map");
		trasy = new OfflineMapy(() -> {}, true);
		trasy.nastav(slozka, TemaOfflineMapy.TRASY);
		try {
			trasy.pouzij().skonci();
			Assert.fail("bez tras a bez základní mapy není co kreslit");
		} catch (final IOException e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("nejsou offline mapy"));
		}
	}
}
