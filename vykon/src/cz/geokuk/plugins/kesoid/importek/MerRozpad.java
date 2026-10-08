package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.*;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mvc.GccomNick;
import cz.geokuk.plugins.kesoid.mvc.GsakParametryNacitani;
import cz.geokuk.util.file.FileAndTime;
import cz.geokuk.util.file.KeFile;
import cz.geokuk.util.file.Root;

/**
 * Rozpad času načtení databází: {@code MerRozpad geoget=CESTA gsak=CESTA opensak=CESTA ... [kola=3] [vlakna=1,2,4]}.
 * <p>
 * Bez {@code vlakna}: na zdroj velikost (db + wal), SQL + tvorba GpxWpt (prázdný builder), počty waypointů, přídavných waypointů a tagů; pro všechny zdroje
 * dohromady a pro každý vynechaný čtení + builder, done() (procáci + bag) a z toho bag + index.
 * <p>
 * S {@code vlakna}: všechny zdroje se čtou do prázdného builderu současně, každý ve vlastním vlákně, nejvýš N vláken naráz; pro každé N celkový čas, součet
 * časů zdrojů, procesorový čas vláken a na konci kola zrychlení proti jednomu vláknu. Program sám dnes čte zdroje postupně v jednom vlákně.
 */
public class MerRozpad {
	static ProgressModel progress() {
		final ProgressModel p = new ProgressModel();
		p.inject(u -> {});
		return p;
	}

	static void cti(final String typ, final File f, final IImportBuilder b) throws Exception {
		if (typ.equals("geoget")) {
			new GeogetLoader().nacti(f, b, null, progress());
		} else if (typ.equals("gsak")) {
			new GsakDbLoader(GsakParametryNacitani::new).nacti(f, b, null, progress());
		} else {
			new OpensakDbLoader().nacti(f, b, null, progress());
		}
	}

	static long mb(final File f) {
		return (f.length() + new File(f.getPath() + "-wal").length()) / 1_000_000;
	}

	/** Builder, který jen počítá waypointy ({@code n[0]}), z toho bez keše ({@code n[1]}) a tagy ({@code n[2]}). */
	static IImportBuilder pocitadlo(final int[] n) {
		return new IImportBuilder() {
			@Override
			public void init() {}

			@Override
			public void done() {}

			@Override
			public void addGpxWpt(final GpxWpt w) {
				n[0]++;
				if (w.groundspeak == null) {
					n[1]++;
				}
				n[2] += w.gpxg.userTags.size();
			}

			@Override
			public void addTrackWpt(final GpxWpt w) {}

			@Override
			public void begTrack() {}

			@Override
			public void begTrackSegment() {}

			@Override
			public void endTrack() {}

			@Override
			public void endTrackSegment() {}

			@Override
			public void setTrackName(final String s) {}
		};
	}

	public static void main(final String[] a) throws Exception {
		final Map<File, String> zdroje = new LinkedHashMap<>();
		int kol = 3;
		final List<Integer> vlakna = new ArrayList<>();
		for (final String arg : a) {
			final int i = arg.indexOf('=');
			final String k = arg.substring(0, i);
			final String v = arg.substring(i + 1);
			if (k.equals("kola")) {
				kol = Integer.parseInt(v);
			} else if (k.equals("vlakna")) {
				for (final String n : v.split(",")) {
					vlakna.add(Integer.parseInt(n.trim()));
				}
			} else {
				zdroje.put(new File(v), k);
			}
		}
		System.out.println("Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name") + ", CPU " + Runtime.getRuntime().availableProcessors() + ", max halda "
				+ Runtime.getRuntime().maxMemory() / 1_000_000 + " MB");
		for (final Map.Entry<File, String> z : zdroje.entrySet()) {
			System.out.println("zdroj " + z.getValue() + " " + z.getKey().getName() + " | " + mb(z.getKey()) + " MB");
		}
		for (int kolo = 0; kolo < kol; kolo++) {
			if (vlakna.isEmpty()) {
				kolo(kolo, zdroje);
			} else {
				final Map<Integer, Long> casy = new HashMap<>();
				// Pořadí se střídá, aby žádný počet vláken neměl v každém kole výhodu teplejší cache.
				final List<Integer> poradi = new ArrayList<>(vlakna);
				Collections.rotate(poradi, -kolo);
				for (final int n : poradi) {
					casy.put(n, soucasne(kolo, zdroje, n));
				}
				final Long jedno = casy.get(1);
				if (jedno != null) {
					final StringBuilder sb = new StringBuilder("kolo " + kolo + " | zrychlení proti 1 vláknu:");
					for (final int n : vlakna) {
						sb.append(String.format(Locale.ROOT, " %d vl. %.2f×", n, casy.get(n) == 0 ? 0 : (double) jedno / casy.get(n)));
					}
					System.out.println(sb);
				}
			}
		}
	}

	/** Všechny zdroje do prázdného builderu, nejvýš {@code n} naráz; vrátí celkový čas v ms. */
	static long soucasne(final int kolo, final Map<File, String> zdroje, final int n) throws Exception {
		final ThreadMXBean mx = ManagementFactory.getThreadMXBean();
		final ExecutorService pool = Executors.newFixedThreadPool(n);
		try {
			final List<Future<long[]>> vysledky = new ArrayList<>();
			final long t0 = System.nanoTime();
			for (final Map.Entry<File, String> z : zdroje.entrySet()) {
				vysledky.add(pool.submit(() -> {
					final int[] pocty = new int[3];
					final long cpu0 = mx.getCurrentThreadCpuTime();
					final long s0 = System.nanoTime();
					cti(z.getValue(), z.getKey(), pocitadlo(pocty));
					return new long[] { (System.nanoTime() - s0) / 1_000_000, (mx.getCurrentThreadCpuTime() - cpu0) / 1_000_000, pocty[0] };
				}));
			}
			long soucet = 0;
			long cpu = 0;
			long wpt = 0;
			final StringBuilder poZdrojich = new StringBuilder();
			int i = 0;
			for (final Map.Entry<File, String> z : zdroje.entrySet()) {
				final long[] r = vysledky.get(i++).get();
				soucet += r[0];
				cpu += r[1];
				wpt += r[2];
				poZdrojich.append(z.getKey().getName()).append(' ').append(r[0]).append(" ms, ");
			}
			final long celkem = (System.nanoTime() - t0) / 1_000_000;
			final int vyuzitych = Math.min(n, zdroje.size());
			System.out.println("kolo " + kolo + " | vláken " + n + " | celkem " + celkem + " ms | součet zdrojů " + soucet + " ms | CPU " + cpu + " ms = "
					+ (celkem == 0 ? 0 : cpu * 100 / (celkem * vyuzitych)) + " % využití " + vyuzitych + " vl. | wpt " + wpt + " | " + poZdrojich);
			return celkem;
		} finally {
			pool.shutdownNow();
		}
	}

	static void kolo(final int kolo, final Map<File, String> zdroje) throws Exception {
		for (final Map.Entry<File, String> z : zdroje.entrySet()) {
			final int[] n = new int[3];
			final long t0 = System.nanoTime();
			cti(z.getValue(), z.getKey(), pocitadlo(n));
			System.out.println("kolo " + kolo + " | " + z.getValue() + " " + z.getKey().getName() + " | " + mb(z.getKey()) + " MB | SQL+GpxWpt " + (System.nanoTime() - t0) / 1_000_000
					+ " ms | wpt " + n[0] + ", z toho bez keše (přídavné apod.) " + n[1] + ", tagů " + n[2]);
		}
		final List<File> vse = new ArrayList<>(zdroje.keySet());
		final List<List<File>> sady = new ArrayList<>();
		sady.add(vse);
		if (vse.size() > 1) {
			for (final File bez : vse) {
				final List<File> s = new ArrayList<>(vse);
				s.remove(bez);
				sady.add(s);
			}
		}
		for (final List<File> sada : sady) {
			final Genom genom = new Genom();
			final KesoidImportBuilder b = new KesoidImportBuilder(genom, new GccomNick("Ja", 42), progress(), new KesoidPluginManager());
			b.init();
			final long t0 = System.nanoTime();
			for (final File f : sada) {
				b.setCurrentlyLoading(new KeFile(new FileAndTime(f, f.lastModified()), new Root(f.getParentFile(), new Root.Def(0, null, null))), true);
				cti(zdroje.get(f), f, b);
			}
			final long t1 = System.nanoTime();
			b.done();
			final long t2 = System.nanoTime();
			final KesBag kopie = new KesBag(genom);
			for (final Wpt w : b.getKesBag().getWpts()) {
				kopie.add(w);
			}
			kopie.done();
			final long t3 = System.nanoTime();
			final StringBuilder jmena = new StringBuilder();
			for (final File f : sada) {
				jmena.append(zdroje.get(f)).append(' ').append(f.getName()).append(", ");
			}
			System.out.println("kolo " + kolo + " | načteno " + jmena + "| čtení+builder " + (t1 - t0) / 1_000_000 + " ms | done " + (t2 - t1) / 1_000_000 + " ms | z toho bag+index "
					+ (t3 - t2) / 1_000_000 + " ms | kešoidů " + b.getKesBag().getKesoidy().size() + ", wpt " + b.getKesBag().getWpts().size());
		}
	}
}
