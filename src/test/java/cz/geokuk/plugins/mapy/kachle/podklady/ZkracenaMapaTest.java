package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Useknutý soubor .map (nedokončené stažení nebo kopie) nesmí shodit kreslení; ostatní mapy ve složce dál kreslí. */
public class ZkracenaMapaTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void zkracenaMapaVedleDobreNevadi() throws Exception {
		final File vzor = tmp.newFolder("vzor");
		OfflineMapyTest.zkopirujMapu(vzor, "kukov.map");
		final byte[] cela = Files.readAllBytes(new File(vzor, "kukov.map").toPath());
		for (final int procent : new int[] { 0, 5, 20, 50, 80, 95, 99 }) {
			final File slozka = tmp.newFolder("mapy" + procent);
			OfflineMapyTest.zkopirujMapu(slozka, "a-dobra.map");
			Files.write(new File(slozka, "b-zkracena.map").toPath(), Arrays.copyOf(cela, cela.length * procent / 100));
			final OfflineMapy mapy = new OfflineMapy(() -> {});
			mapy.kontrolaSlozkyNs = 0;
			mapy.nastav(slozka, TemaOfflineMapy.VYCHOZI);
			try {
				final OfflineRenderer r = mapy.pouzij();
				try {
					final BufferedImage img = r.vyrendruj(OfflineMapyTest.STRED_Z15);
					Assert.assertTrue(procent + " %: dobrá mapa kreslí", barev(img) > 10);
				} finally {
					r.skonci();
				}
			} catch (final IOException e) {
				Assert.fail(procent + " %: s dobrou mapou ve složce se kreslit má: " + e);
			} finally {
				mapy.zavri();
			}
		}
	}

	@Test
	public void samotnaZkracenaMapaDaSrozumitelnouChybuNeboKresli() throws Exception {
		final File vzor = tmp.newFolder("vzor");
		OfflineMapyTest.zkopirujMapu(vzor, "kukov.map");
		final byte[] cela = Files.readAllBytes(new File(vzor, "kukov.map").toPath());
		for (final int procent : new int[] { 0, 5, 20, 50, 80, 95, 99 }) {
			final File slozka = tmp.newFolder("sama" + procent);
			Files.write(new File(slozka, "zkracena.map").toPath(), Arrays.copyOf(cela, cela.length * procent / 100));
			final OfflineMapy mapy = new OfflineMapy(() -> {});
			mapy.kontrolaSlozkyNs = 0;
			mapy.nastav(slozka, TemaOfflineMapy.VYCHOZI);
			try {
				final OfflineRenderer r = mapy.pouzij();
				try {
					if (r.pokryva(OfflineMapyTest.STRED_Z15)) {
						Assert.assertNotNull(r.vyrendruj(OfflineMapyTest.STRED_Z15));
					}
				} finally {
					r.skonci();
				}
			} catch (final IOException e) {
				Assert.assertTrue(procent + " %: " + e.getMessage(), e.getMessage().contains("zkracena.map"));
			} finally {
				mapy.zavri();
			}
		}
	}

	private static int barev(final BufferedImage img) {
		final Set<Integer> barvy = new HashSet<>();
		for (final int p : img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth())) {
			barvy.add(p);
		}
		return barvy.size();
	}
}
