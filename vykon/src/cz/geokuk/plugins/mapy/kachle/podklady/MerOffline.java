package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/**
 * Měření vykreslování offline mapy: {@code MerOffline slozka=… [tema=…] [lat=50.08 lon=14.42] [zoomy=10,13,15,17] [n=6] [vlakna=1,2]}. Pro každý zoom vykreslí
 * n×n dlaždic kolem místa: první průchod v jednom vlákně (data mapy ještě nejsou v paměti), pak stejné dlaždice v zadaných počtech vláken. Vypíše čas na
 * dlaždici, stěnu na dlaždici, odhad první obrazovky 1920×1080 (40 dlaždic) a haldu.
 */
public class MerOffline {

	public static void main(final String[] a) throws Exception {
		final Map<String, String> p = new HashMap<>();
		for (final String s : a) {
			final int i = s.indexOf('=');
			p.put(s.substring(0, i), s.substring(i + 1));
		}
		final File slozka = new File(p.get("slozka"));
		final Wgs misto = new Wgs(Double.parseDouble(p.getOrDefault("lat", "50.08")), Double.parseDouble(p.getOrDefault("lon", "14.42")));
		final int n = Integer.parseInt(p.getOrDefault("n", "6"));
		final OfflineMapy mapy = new OfflineMapy(() -> {});
		mapy.nastav(slozka, TemaOfflineMapy.zTextu(p.get("tema")));

		long t = System.nanoTime();
		OfflineRenderer r = mapy.pouzij();
		System.out.printf("otevření map a tématu: %d ms, mapy %s, téma %s%s%n", ms(t), OfflineMapy.mapyVeSlozce(slozka), mapy.getTema(),
				mapy.getChybaTematu() == null ? "" : " (nepoužito: " + mapy.getChybaTematu() + ")");
		r.skonci();

		for (final String zs : p.getOrDefault("zoomy", "10,13,15,17").split(",")) {
			final int z = Integer.parseInt(zs);
			final List<KaLoc> dlazdice = mrizka(misto, z, n);
			for (final String vs : ("prvni," + p.getOrDefault("vlakna", "1,2")).split(",")) {
				final int vlaken = vs.equals("prvni") ? 1 : Integer.parseInt(vs);
				final ExecutorService ex = Executors.newFixedThreadPool(vlaken);
				final List<Long> casy = Collections.synchronizedList(new ArrayList<>());
				final long[] bajtu = new long[1];
				r = mapy.pouzij();
				final OfflineRenderer renderer = r;
				t = System.nanoTime();
				final List<Future<?>> f = new ArrayList<>();
				for (final KaLoc loc : dlazdice) {
					f.add(ex.submit(() -> {
						final long t0 = System.nanoTime();
						final ImageWithData img = renderer.vyrendruj(loc);
						casy.add(System.nanoTime() - t0);
						synchronized (bajtu) {
							bajtu[0] += img.getData().length;
						}
						return null;
					}));
				}
				for (final Future<?> x : f) {
					x.get();
				}
				final long stena = System.nanoTime() - t;
				ex.shutdown();
				r.skonci();
				final List<Long> s = new ArrayList<>(casy);
				Collections.sort(s);
				final double stenaNaDlazdici = stena / 1e6 / dlazdice.size();
				System.out.printf("z%d %s: %d dlaždic, na dlaždici průměr %.1f ms, medián %.1f ms, max %.1f ms, stěna %.1f ms/dlaždici, obrazovka 40 dlaždic ~%.1f s, PNG ⌀ %d kB%n", z,
						vs.equals("prvni") ? "první průchod" : "vláken " + vlaken, s.size(), s.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6, s.get(s.size() / 2) / 1e6, s.get(s.size() - 1) / 1e6, stenaNaDlazdici,
						stenaNaDlazdici * 40 / 1000, bajtu[0] / s.size() / 1024);
			}
		}
		System.gc();
		final Runtime rt = Runtime.getRuntime();
		System.out.printf("halda po GC: %d MB%n", (rt.totalMemory() - rt.freeMemory()) >> 20);
		mapy.zavri();
		System.exit(0);
	}

	/** n×n dlaždic kolem místa. */
	private static List<KaLoc> mrizka(final Wgs misto, final int z, final int n) {
		final KaLoc stred = KaLoc.ofJZ(misto.toMou(), z);
		final int krok = 1 << 32 - z;
		final List<KaLoc> vysledek = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				vysledek.add(KaLoc.ofJZ(new Mou(stred.getMouJZ().xx + (i - n / 2) * krok, stred.getMouJZ().yy + (j - n / 2) * krok), z));
			}
		}
		return vysledek;
	}

	private static long ms(final long od) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - od);
	}
}
