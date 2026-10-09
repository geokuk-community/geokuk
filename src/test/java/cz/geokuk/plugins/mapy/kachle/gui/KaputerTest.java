package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Dimension;
import java.awt.Point;
import java.util.*;

import org.junit.*;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/** Které dlaždice pokrývají výřez: kontrola proti vzorci XYZ (EPSG:3857), bez displeje. */
public class KaputerTest {

	private static Set<String> ocekavane(final Wgs stred, final int z, final int w, final int h) {
		final double svet = 256.0 * (1L << z);
		final double sin = Math.sin(Math.toRadians(stred.lat));
		final double px = (stred.lon + 180) / 360 * svet;
		final double py = (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * svet;
		final Set<String> vysledek = new TreeSet<>();
		for (int x = (int) Math.floor((px - w / 2.0) / 256); x <= (int) Math.floor((px + w / 2.0 - 1) / 256); x++) {
			for (int y = (int) Math.floor((py - h / 2.0) / 256); y <= (int) Math.floor((py + h / 2.0 - 1) / 256); y++) {
				vysledek.add(z + "/" + x + "/" + y);
			}
		}
		return vysledek;
	}

	private static Set<String> dlazdice(final Kaputer k) {
		final Set<String> vysledek = new TreeSet<>();
		for (int y = 0; y < k.getPocetKachliY(); y++) {
			for (int x = 0; x < k.getPocetKachliX(); x++) {
				final KaLoc l = k.getKaloc(x, y);
				vysledek.add(l.getMoumer() + "/" + l.getFromSzUnsignedX() + "/" + l.getFromSzUnsignedY());
			}
		}
		return vysledek;
	}

	@Test
	public void dlazdiceVyrezuOdpovidajiVzorciProRuznaMeritkaAStredy() {
		final Wgs[] stredy = { new Wgs(50.0875, 14.4214), new Wgs(48.2, 17.1), new Wgs(-33.9, 151.2), new Wgs(60.17, -1.15) };
		for (final Wgs stred : stredy) {
			for (final int z : new int[] { 8, 12, 15, 18 }) {
				for (final Dimension d : new Dimension[] { new Dimension(1000, 700), new Dimension(333, 257), new Dimension(1920, 1080) }) {
					final Kaputer k = new Kaputer(new Coord(z, stred.toMou(), d, 0.0));
					Assert.assertEquals(stred + " z" + z + " " + d, ocekavane(stred, z, d.width, d.height), dlazdice(k));
				}
			}
		}
	}

	@Test
	public void kachleSeUmistujiDoMrizky256Pixelu() {
		final Kaputer k = new Kaputer(new Coord(12, new Wgs(50.0875, 14.4214).toMou(), new Dimension(1000, 700), 0.0));
		final Point p0 = k.getKachlePoint(0, 0);
		Assert.assertTrue(p0.x <= 0 && p0.x > -256 && p0.y <= 0 && p0.y > -256);
		Assert.assertEquals(new Point(p0.x + 256, p0.y), k.getKachlePoint(1, 0));
		Assert.assertEquals(new Point(p0.x, p0.y + 256), k.getKachlePoint(0, 1));
		Assert.assertTrue("poslední sloupec zasahuje do pravého okraje", k.getKachlePoint(k.getPocetKachliX() - 1, 0).x < 1000);
		Assert.assertTrue("a další už ne", k.getKachlePoint(k.getPocetKachliX(), 0).x >= 1000);
		Assert.assertTrue(k.getKachlePoint(0, k.getPocetKachliY() - 1).y < 700);
		Assert.assertTrue(k.getKachlePoint(0, k.getPocetKachliY()).y >= 700);
	}

	@Test
	public void sousedniKachleMajiSousedniCislaVOsachXY() {
		final Kaputer k = new Kaputer(new Coord(12, new Wgs(50.0875, 14.4214).toMou(), new Dimension(1000, 700), 0.0));
		final KaLoc a = k.getKaloc(0, 0);
		Assert.assertEquals(a.getFromSzUnsignedX() + 1, k.getKaloc(1, 0).getFromSzUnsignedX());
		Assert.assertEquals(a.getFromSzUnsignedY() + 1, k.getKaloc(0, 1).getFromSzUnsignedY());
	}
}
