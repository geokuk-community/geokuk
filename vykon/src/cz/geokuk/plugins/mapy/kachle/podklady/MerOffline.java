package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.data.*;
import cz.geokuk.util.file.Filex;

/**
 * Měření vykreslování offline mapy: {@code MerOffline slozka=… [tema=…] [lat=50.08 lon=14.42] [zoomy=10,13,15,17] [n=6] [vlakna=1,2] [symboly=složka]}; se složkou
 * symbolů se vykreslené symboly tématu uloží a druhé spuštění je načte jako program. Pro každý zoom vykreslí
 * n×n dlaždic kolem místa: první průchod v jednom vlákně (data mapy ještě nejsou v paměti), pak stejné dlaždice v zadaných počtech vláken. Vypíše čas na
 * dlaždici (bez převodu do PNG, ten běží až při ukládání), stěnu na dlaždici, odhad první obrazovky 1920×1080 (40 dlaždic) a haldu. Vykreslené dlaždice z prvního průchodu uloží do cache dlaždic (SQLite v
 * dočasné složce) a změří jejich načtení z ní, tedy druhé zobrazení.
 */
public class MerOffline {

	public static void main(final String[] a) throws Exception {
		final Map<String, String> p = new HashMap<>();
		for (final String s : a) {
			final int i = s.indexOf('=');
			p.put(s.substring(0, i), s.substring(i + 1));
		}
		// Jako program: PNG se čte a zapisuje v paměti, bez dočasných souborů.
		javax.imageio.ImageIO.setUseCache(false);
		final File slozka = new File(p.get("slozka"));
		final Wgs misto = new Wgs(Double.parseDouble(p.getOrDefault("lat", "50.08")), Double.parseDouble(p.getOrDefault("lon", "14.42")));
		final int n = Integer.parseInt(p.getOrDefault("n", "6"));
		final OfflineMapy mapy = new OfflineMapy(() -> {});
		if (p.containsKey("symboly")) {
			mapy.setSlozkaSymbolu(new File(p.get("symboly")));
		}
		mapy.nastav(slozka, TemaOfflineMapy.zTextu(p.get("tema")));

		long t = System.nanoTime();
		OfflineRenderer r = mapy.pouzij();
		System.out.printf("otevření map a tématu: %d ms (z toho téma %d ms), mapy %s, téma %s%s%n", ms(t), r.getTema().nacitaniMs, OfflineMapy.mapyVeSlozce(slozka), mapy.getTema(),
				mapy.getChybaTematu() == null ? "" : " (nepoužito: " + mapy.getChybaTematu() + ")");
		final String klic = r.getKlic();
		r.skonci();
		final File slozkaCache = Files.createTempDirectory("mer-offline-cache").toFile();
		final KachleCacheFolderHolder holder = new KachleCacheFolderHolder();
		holder.setKachleCacheDir(new Filex(slozkaCache, false, true));
		final KachleDBManager cache = new KachleDBManager(holder);

		for (final String zs : p.getOrDefault("zoomy", "10,13,15,17").split(",")) {
			final int z = Integer.parseInt(zs);
			final List<KaLoc> dlazdice = mrizka(misto, z, n);
			for (final String vs : ("prvni," + p.getOrDefault("vlakna", "1,2")).split(",")) {
				final int vlaken = vs.equals("prvni") ? 1 : Integer.parseInt(vs);
				final ExecutorService ex = Executors.newFixedThreadPool(vlaken);
				final List<Long> casy = Collections.synchronizedList(new ArrayList<>());
				final Map<KaLoc, BufferedImage> obrazky = new ConcurrentHashMap<>();
				r = mapy.pouzij();
				final OfflineRenderer renderer = r;
				t = System.nanoTime();
				final List<Future<?>> f = new ArrayList<>();
				for (final KaLoc loc : dlazdice) {
					f.add(ex.submit(() -> {
						final long t0 = System.nanoTime();
						obrazky.put(loc, renderer.vyrendruj(loc));
						casy.add(System.nanoTime() - t0);
						return null;
					}));
				}
				for (final Future<?> x : f) {
					x.get();
				}
				final long stena = System.nanoTime() - t;
				ex.shutdown();
				r.skonci();
				final Map<KaLoc, byte[]> png = new HashMap<>();
				long bajtu = 0;
				t = System.nanoTime();
				for (final Map.Entry<KaLoc, BufferedImage> e : obrazky.entrySet()) {
					final byte[] data = new Ukladanec(new Ka(e.getKey(), EKaType.OFFLINE_MF), klic, e.getValue(), null).dataKUlozeni();
					png.put(e.getKey(), data);
					bajtu += data.length;
				}
				final long prevod = System.nanoTime() - t;
				final List<Long> s = new ArrayList<>(casy);
				Collections.sort(s);
				final double stenaNaDlazdici = stena / 1e6 / dlazdice.size();
				System.out.printf("z%d %s: %d dlaždic, na dlaždici průměr %.1f ms, medián %.1f ms, max %.1f ms, stěna %.1f ms/dlaždici, obrazovka 40 dlaždic ~%.1f s, PNG ⌀ %d kB (převod %.1f ms, při ukládání)%n", z,
						vs.equals("prvni") ? "první průchod" : "vláken " + vlaken, s.size(), s.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6, s.get(s.size() / 2) / 1e6, s.get(s.size() - 1) / 1e6, stenaNaDlazdici,
						stenaNaDlazdici * 40 / 1000, bajtu / s.size() / 1024, prevod / 1e6 / s.size());
				if (vs.equals("prvni")) {
					zCache(cache, klic, png, z);
				}
			}
		}
		System.gc();
		final Runtime rt = Runtime.getRuntime();
		System.out.printf("halda po GC: %d MB%n", (rt.totalMemory() - rt.freeMemory()) >> 20);
		mapy.zavri();
		System.exit(0);
	}

	/** Uloží dlaždice do cache a změří jejich načtení, jako při druhém zobrazení stejného místa. */
	private static void zCache(final KachleDBManager cache, final String klic, final Map<KaLoc, byte[]> png, final int z) {
		final List<KachleManager.ItemToSave> ulozit = new ArrayList<>();
		for (final Map.Entry<KaLoc, byte[]> e : png.entrySet()) {
			ulozit.add(new KachleManager.ItemToSave(new Ka(e.getKey(), EKaType.OFFLINE_MF), klic, e.getValue()));
		}
		cache.save(ulozit);
		final List<Long> casy = new ArrayList<>();
		final long t = System.nanoTime();
		for (final KaLoc loc : png.keySet()) {
			final long t0 = System.nanoTime();
			if (cache.load(new Ka(loc, EKaType.OFFLINE_MF), klic) == null) {
				throw new IllegalStateException("Dlaždice " + loc + " není v cache");
			}
			casy.add(System.nanoTime() - t0);
		}
		final double naDlazdici = (System.nanoTime() - t) / 1e6 / png.size();
		Collections.sort(casy);
		System.out.printf("z%d z cache: %d dlaždic, na dlaždici průměr %.1f ms, medián %.1f ms, obrazovka 40 dlaždic ~%.2f s%n", z, casy.size(), naDlazdici, casy.get(casy.size() / 2) / 1e6,
				naDlazdici * 40 / 1000);
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
