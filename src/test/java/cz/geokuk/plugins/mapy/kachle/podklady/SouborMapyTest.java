package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.reader.MapFile;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/** Přehledová mapa světa se nad z8 nekreslí tam, kde je podrobná mapa (jinak třeba druhá, hrubá hranice státu). */
public class SouborMapyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final File PREHLED = new File("distribuce/offline-mapy/prehled-svet-ne.map");

	private static Tile dlazdice(final double lat, final double lon, final int z) {
		return OfflineRenderer.dlazdice(KaLoc.ofJZ(new Wgs(lat, lon).toMou(), z));
	}

	@Test
	public void prehledJenDoZ8NeboMimoPodrobnou() throws IOException {
		OfflineMapyTest.zkopirujMapu(tmp.getRoot(), "kukov.map");
		final File kukov = new File(tmp.getRoot(), "kukov.map");
		final MapFile podrobna = new MapFile(kukov);
		final SouborMapy prehled = new SouborMapy(PREHLED);
		try {
			prehled.setPodrobne(Collections.singletonList(podrobna));
			Assert.assertTrue("z5 v podrobné", prehled.supportsTile(dlazdice(50.003, 14.405, 5)));
			Assert.assertTrue("z8 v podrobné", prehled.supportsTile(dlazdice(50.003, 14.405, SouborMapy.PREHLED_DO_ZOOMU)));
			Assert.assertFalse("z9 v podrobné", prehled.supportsTile(dlazdice(50.003, 14.405, 9)));
			Assert.assertFalse("z15 v podrobné", prehled.supportsTile(dlazdice(50.003, 14.405, 15)));
			Assert.assertTrue("z10 mimo podrobnou", prehled.supportsTile(dlazdice(48.0, 17.0, 10)));
			final SouborMapy bezPodrobnych = new SouborMapy(PREHLED);
			try {
				Assert.assertTrue("bez podrobné mapy beze změny", bezPodrobnych.supportsTile(dlazdice(50.003, 14.405, 10)));
			} finally {
				bezPodrobnych.close();
			}
		} finally {
			prehled.close();
			podrobna.close();
		}
	}

	private static int cest(final File slozka, final Tile t) throws IOException {
		final OfflineMapy mapy = new OfflineMapy(() -> {});
		try {
			mapy.kontrolaSlozkyNs = 0;
			mapy.nastav(slozka, TemaOfflineMapy.VYCHOZI);
			final OfflineRenderer r = mapy.pouzij();
			try {
				return r.getData().readMapData(t).ways.size();
			} finally {
				r.skonci();
			}
		} finally {
			mapy.zavri();
		}
	}

	@Test
	public void rendererCtePrehledJenKdeMa() throws IOException {
		final File jen = tmp.newFolder("jen-data");
		OfflineMapyTest.zkopirujMapu(jen, "kukov.map");
		final File sPrehledem = tmp.newFolder("s-prehledem-data");
		OfflineMapyTest.zkopirujMapu(sPrehledem, "kukov.map");
		Files.copy(PREHLED.toPath(), new File(sPrehledem, PREHLED.getName()).toPath());
		final Tile z15 = dlazdice(50.003, 14.405, 15);
		Assert.assertEquals("z15 v podrobné mapě bez cest přehledové", cest(jen, z15), cest(sPrehledem, z15));
		final Tile z5 = dlazdice(50.003, 14.405, 5);
		Assert.assertTrue("z5 s cestami přehledové", cest(sPrehledem, z5) > cest(jen, z5));
	}

	private static BufferedImage vyrendruj(final File slozka, final KaLoc loc) throws IOException {
		final OfflineMapy mapy = new OfflineMapy(() -> {});
		try {
			mapy.kontrolaSlozkyNs = 0;
			mapy.nastav(slozka, TemaOfflineMapy.VYCHOZI);
			final OfflineRenderer r = mapy.pouzij();
			try {
				return r.vyrendruj(loc);
			} finally {
				r.skonci();
			}
		} finally {
			mapy.zavri();
		}
	}

	private static int[] pixely(final BufferedImage img) {
		return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
	}

	@Test
	public void podrobnaDlazdiceJeStejnaSPrehledovouMapouIBezNi() throws IOException {
		final File jen = tmp.newFolder("jen");
		OfflineMapyTest.zkopirujMapu(jen, "kukov.map");
		final File sPrehledem = tmp.newFolder("s-prehledem");
		OfflineMapyTest.zkopirujMapu(sPrehledem, "kukov.map");
		Files.copy(PREHLED.toPath(), new File(sPrehledem, PREHLED.getName()).toPath());
		Assert.assertArrayEquals(pixely(vyrendruj(jen, OfflineMapyTest.STRED_Z15)), pixely(vyrendruj(sPrehledem, OfflineMapyTest.STRED_Z15)));
		final KaLoc z5 = KaLoc.ofJZ(new Wgs(50.003, 14.405).toMou(), 5);
		Assert.assertFalse("na z5 přehledová mapa kreslí", Arrays.equals(pixely(vyrendruj(jen, z5)), pixely(vyrendruj(sPrehledem, z5))));
	}
}
