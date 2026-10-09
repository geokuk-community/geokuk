package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
 * Rozpad času načtení databází: {@code MerRozpad geoget=CESTA gsak=CESTA opensak=CESTA ... [kola=3] [vlakna=1,2,4] [skupiny=1] [predehrat=0|1]}.
 * <p>
 * Bez {@code vlakna}: na zdroj velikost (db + wal), SQL + tvorba GpxWpt (prázdný builder), počty waypointů, přídavných waypointů a tagů; pro všechny zdroje
 * dohromady a pro každý vynechaný čtení + builder, done() (procáci + bag) a z toho bag + index.
 * <p>
 * S {@code vlakna}: všechny zdroje se čtou do prázdného builderu současně, každý ve vlastním vlákně, nejvýš N vláken naráz; pro každé N celkový čas, součet
 * časů zdrojů, procesorový čas vláken a na konci kola zrychlení proti jednomu vláknu. Program sám dnes čte zdroje postupně v jednom vlákně.
 * <p>
 * S {@code skupiny=1}: jen kódy waypointů každé databáze (jako předpověď překryvu v programu), skupiny zdrojů sdílejících klíč jména a pro každou dvojici
 * zdrojů počet společných klíčů s ukázkou kódů. Klíče {@code cgp:} se zjistí až čtením, skupiny tedy můžou být ve skutečnosti větší.
 * <p>
 * S {@code predehrat}: zdroje se čtou postupně jako v programu (prázdný builder); s {@code predehrat=1} mezitím druhé vlákno čte sekvenčně soubor DALŠÍHO
 * zdroje (a jeho -wal) jen do cache systému, první zdroj se čte bez předehřátí. Porovnání s {@code predehrat=0} má smysl studeně (každý běh po restartu počítače).
 * <p>
 * S {@code retezce=1}: všechny zdroje se načtou jako v programu a pro každé textové pole waypointů a kešoidů se vypíše počet řetězců, počet různých hodnot,
 * kolik paměti zabírají a kolik by zabíraly, kdyby stejné texty byly jeden objekt; na konci totéž přes všechna pole dohromady.
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
		boolean skupiny = false;
		Boolean predehrat = null;
		boolean retezce = false;
		for (final String arg : a) {
			final int i = arg.indexOf('=');
			final String k = arg.substring(0, i);
			final String v = arg.substring(i + 1);
			if (k.equals("kola")) {
				kol = Integer.parseInt(v);
			} else if (k.equals("predehrat")) {
				predehrat = v.equals("1");
			} else if (k.equals("retezce")) {
				retezce = v.equals("1");
			} else if (k.equals("skupiny")) {
				skupiny = v.equals("1");
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
		if (skupiny) {
			skupiny(zdroje);
			return;
		}
		if (retezce) {
			retezce(zdroje);
			return;
		}
		if (predehrat != null) {
			for (int kolo = 0; kolo < kol; kolo++) {
				postupneSPredehratim(kolo, zdroje, predehrat);
			}
			return;
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

	static Nacitac0 nacitac(final String typ) {
		if (typ.equals("geoget")) {
			return new GeogetLoader();
		} else if (typ.equals("gsak")) {
			return new GsakDbLoader(GsakParametryNacitani::new);
		} else {
			return new OpensakDbLoader();
		}
	}

	/** Skupiny podle klíčů jmen z kódů databází, bez čtení ostatních údajů. */
	static void skupiny(final Map<File, String> zdroje) throws Exception {
		final List<File> soubory = new ArrayList<>(zdroje.keySet());
		final List<KliceZdroje> klice = new ArrayList<>();
		final List<Map<Long, String>> ukazky = new ArrayList<>();
		for (final File f : soubory) {
			final long t0 = System.nanoTime();
			final Collection<String> jmena = nacitac(zdroje.get(f)).jmenaPredem(f);
			final KliceZdroje.Sberac s = new KliceZdroje.Sberac();
			final Map<Long, String> kody = new HashMap<>();
			for (final String jmeno : jmena) {
				final long klic = KliceZdroje.klicJmena(jmeno);
				s.pridej(klic);
				kody.putIfAbsent(klic, jmeno);
			}
			final KliceZdroje k = s.hotovo();
			klice.add(k);
			ukazky.add(kody);
			System.out.println("zdroj " + zdroje.get(f) + " " + f.getName() + " | kódů " + jmena.size() + " | klíčů " + k.klice.length + " | " + (System.nanoTime() - t0) / 1_000_000 + " ms");
		}
		final int[] komponenty = SkupinyZdroju.komponenty(klice);
		final Map<Integer, List<Integer>> podleKomponenty = new LinkedHashMap<>();
		for (int i = 0; i < soubory.size(); i++) {
			podleKomponenty.computeIfAbsent(komponenty[i], x -> new ArrayList<>()).add(i);
		}
		System.out.println("skupin " + podleKomponenty.size() + " z " + soubory.size() + " zdrojů");
		int c = 0;
		for (final List<Integer> clenove : podleKomponenty.values()) {
			long pocet = 0;
			final StringBuilder sb = new StringBuilder();
			for (final int i : clenove) {
				pocet += klice.get(i).klice.length;
				sb.append(soubory.get(i).getName()).append(", ");
			}
			System.out.println("skupina " + ++c + " | zdrojů " + clenove.size() + " | klíčů celkem " + pocet + " | " + sb);
		}
		for (int i = 0; i < soubory.size(); i++) {
			for (int j = i + 1; j < soubory.size(); j++) {
				final int spolecnych = klice.get(i).spolecnych(klice.get(j));
				if (spolecnych == 0) {
					continue;
				}
				final StringBuilder sb = new StringBuilder();
				int n = 0;
				for (final long klic : klice.get(i).klice) {
					if (n < 5 && Arrays.binarySearch(klice.get(j).klice, klic) >= 0) {
						sb.append(ukazky.get(i).get(klic)).append('/').append(ukazky.get(j).get(klic)).append(' ');
						n++;
					}
				}
				System.out.println("společné " + soubory.get(i).getName() + " × " + soubory.get(j).getName() + " | klíčů " + spolecnych + " | např. " + sb);
			}
		}
	}

	/** Zdroje postupně do prázdného builderu; s předehřátím čte druhé vlákno sekvenčně soubor dalšího zdroje do cache systému. */
	static void postupneSPredehratim(final int kolo, final Map<File, String> zdroje, final boolean predehrat) throws Exception {
		final List<File> soubory = new ArrayList<>(zdroje.keySet());
		final ExecutorService vlakno = Executors.newSingleThreadExecutor(r -> {
			final Thread t = new Thread(r, "Předehřátí");
			t.setDaemon(true);
			return t;
		});
		try {
			final long t0 = System.nanoTime();
			Future<long[]> dalsi = null;
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < soubory.size(); i++) {
				final File f = soubory.get(i);
				long[] ohrato = null;
				if (dalsi != null) {
					ohrato = dalsi.get(); // zdroj se čte až po předehřátí, jinak by se o disk přetahovala dvě vlákna
				}
				final File nasledujici = predehrat && i + 1 < soubory.size() ? soubory.get(i + 1) : null;
				dalsi = nasledujici == null ? null : vlakno.submit(() -> predehrej(nasledujici));
				final int[] n = new int[3];
				final long s0 = System.nanoTime();
				cti(zdroje.get(f), f, pocitadlo(n));
				sb.append(f.getName()).append(' ').append((System.nanoTime() - s0) / 1_000_000).append(" ms");
				if (ohrato != null) {
					sb.append(" (předehřátí ").append(ohrato[0] / 1_000_000).append(" MB za ").append(ohrato[1]).append(" ms)");
				}
				sb.append(", ");
			}
			System.out.println("kolo " + kolo + " | předehřátí " + (predehrat ? "ano" : "ne") + " | celkem " + (System.nanoTime() - t0) / 1_000_000 + " ms | " + sb);
		} finally {
			vlakno.shutdownNow();
		}
	}

	/** Přečte soubor a jeho -wal sekvenčně po 4 MB, jen aby byl v cache systému; vrátí bajty a ms. */
	static long[] predehrej(final File f) throws IOException {
		final long t0 = System.nanoTime();
		long bajtu = 0;
		final java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocateDirect(4 << 20);
		for (final File soubor : new File[] { f, new File(f.getPath() + "-wal") }) {
			if (!soubor.isFile()) {
				continue;
			}
			try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(soubor.toPath(), java.nio.file.StandardOpenOption.READ)) {
				int precteno;
				while ((precteno = ch.read(buf)) >= 0) {
					bajtu += precteno;
					buf.clear();
				}
			}
		}
		return new long[] { bajtu, (System.nanoTime() - t0) / 1_000_000 };
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

	/** Řetězce v načtených waypointech a kešoidech: po polích a celkem, kolik by ušetřilo sdílení stejných textů. */
	static void retezce(final Map<File, String> zdroje) throws Exception {
		final Genom genom = new Genom();
		final KesoidImportBuilder b = new KesoidImportBuilder(genom, new GccomNick("Ja", 42), progress(), new KesoidPluginManager());
		b.init();
		final long t0 = System.nanoTime();
		for (final File f : zdroje.keySet()) {
			b.setCurrentlyLoading(new KeFile(new FileAndTime(f, f.lastModified()), new Root(f.getParentFile(), new Root.Def(0, null, null))), true);
			cti(zdroje.get(f), f, b);
		}
		b.done();
		final List<Wpt> wpty = new ArrayList<>(b.getKesBag().getWpts());
		final Set<Object> kesoidy = Collections.newSetFromMap(new IdentityHashMap<>());
		for (final Wpt w : wpty) {
			kesoidy.add(w.getKesoid());
		}
		System.out.println("načteno za " + (System.nanoTime() - t0) / 1_000_000 + " ms | wpt " + wpty.size() + " | kešoidů " + kesoidy.size());
		final Map<String, List<Field>> pole = new TreeMap<>();
		final List<Object> objekty = new ArrayList<>(wpty);
		objekty.addAll(kesoidy);
		for (final Object o : objekty) {
			for (Class<?> c = o.getClass(); c != Object.class; c = c.getSuperclass()) {
				final String jmeno = c.getSimpleName();
				if (pole.containsKey(jmeno)) {
					continue;
				}
				final List<Field> f = new ArrayList<>();
				for (final Field x : c.getDeclaredFields()) {
					if (x.getType() == String.class && !Modifier.isStatic(x.getModifiers())) {
						x.setAccessible(true);
						f.add(x);
					}
				}
				pole.put(jmeno, f);
			}
		}
		System.out.println("pole;řetězců;různých objektů;různých hodnot;MB teď;MB po sdílení;úspora MB");
		final Set<String> vseObjekty = Collections.newSetFromMap(new IdentityHashMap<>());
		final Set<String> vseHodnoty = new HashSet<>();
		long vseTed = 0;
		long vsePo = 0;
		for (final Map.Entry<String, List<Field>> e : pole.entrySet()) {
			for (final Field f : e.getValue()) {
				long pocet = 0;
				long ted = 0;
				long po = 0;
				final Set<String> obj = Collections.newSetFromMap(new IdentityHashMap<>());
				final Set<String> hodnoty = new HashSet<>();
				for (final Object o : objekty) {
					if (!f.getDeclaringClass().isInstance(o)) {
						continue;
					}
					final String t = (String) f.get(o);
					if (t == null) {
						continue;
					}
					pocet++;
					if (obj.add(t)) {
						ted += bajty(t);
					}
					if (hodnoty.add(t)) {
						po += bajty(t);
					}
					if (vseObjekty.add(t)) {
						vseTed += bajty(t);
					}
					if (vseHodnoty.add(t)) {
						vsePo += bajty(t);
					}
				}
				System.out.println(e.getKey() + "." + f.getName() + ";" + pocet + ";" + obj.size() + ";" + hodnoty.size() + ";" + ted / 1_000_000 + ";" + po / 1_000_000 + ";" + (ted - po) / 1_000_000);
			}
		}
		System.out.println("celkem;;" + vseObjekty.size() + ";" + vseHodnoty.size() + ";" + vseTed / 1_000_000 + ";" + vsePo / 1_000_000 + ";" + (vseTed - vsePo) / 1_000_000);
	}

	/** Velikost řetězce na haldě (komprimované ukazatele, kompaktní řetězce): objekt String 24 B + pole bajtů zarovnané na 8 B. */
	static long bajty(final String t) {
		boolean latin1 = true;
		for (int i = 0; i < t.length() && latin1; i++) {
			latin1 = t.charAt(i) <= 0xFF;
		}
		final long pole = 16 + (latin1 ? t.length() : 2L * t.length());
		return 24 + (pole + 7) / 8 * 8;
	}
}
