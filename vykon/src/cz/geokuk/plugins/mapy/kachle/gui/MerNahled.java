package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.*;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.*;

/**
 * Měření náhledu dlaždice: {@code MerNahled [meritko=1|2] [n=2000]}. Cena nalezení náhledu v paměťové cache a jeho nakreslení do dlaždice 256 logických
 * bodů (obrázek rodiče 256×měřítko px), medián z n opakování.
 */
public class MerNahled {

	public static void main(final String[] a) {
		int meritko = 1;
		int n = 2000;
		for (final String s : a) {
			if (s.startsWith("meritko=")) {
				meritko = Integer.parseInt(s.substring(8));
			} else if (s.startsWith("n=")) {
				n = Integer.parseInt(s.substring(2));
			}
		}
		final KaLoc rodic = KaLoc.ofJZ(new Mou(0x23800000, 0x28100000), 13);
		final Map<Ka, Image> cache = new HashMap<>();
		final BufferedImage obraz = new BufferedImage(256 * meritko, 256 * meritko, BufferedImage.TYPE_INT_ARGB);
		cache.put(new Ka(rodic, EKaType.OFFLINE_MF), obraz);
		final Ka dite = new Ka(rodic.dite(1, 0), EKaType.OFFLINE_MF);
		final BufferedImage cil = new BufferedImage(256 * meritko, 256 * meritko, BufferedImage.TYPE_INT_ARGB);
		final long[] najdi = new long[n];
		final long[] kresli = new long[n];
		for (int i = 0; i < n; i++) {
			long t = System.nanoTime();
			final List<NahledDlazdice> nahled = NahledDlazdice.najdi(dite, cache::get, 256);
			najdi[i] = System.nanoTime() - t;
			final Graphics2D g = cil.createGraphics();
			g.scale(meritko, meritko);
			t = System.nanoTime();
			for (final NahledDlazdice d : nahled) {
				d.kresli(g);
			}
			kresli[i] = System.nanoTime() - t;
			g.dispose();
		}
		Arrays.sort(najdi);
		Arrays.sort(kresli);
		System.out.printf("měřítko %d: nalezení náhledu medián %.1f µs, nakreslení medián %.1f µs, p95 %.1f µs%n", meritko, najdi[n / 2] / 1e3, kresli[n / 2] / 1e3, kresli[n * 95 / 100] / 1e3);
	}
}
