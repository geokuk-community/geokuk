package cz.geokuk.core.program;

import java.io.File;
import java.lang.management.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;

import cz.geokuk.core.napoveda.Vykon;
import cz.geokuk.util.pocitadla.Pocitadlo;
import cz.geokuk.util.pocitadla.SpravcePocitadel;
import jdk.jfr.Recording;
import jdk.jfr.consumer.*;

/**
 * Počítání práce prvních N sekund po startu programu: {@code MerStart mapa=…/x.map [mapy=8] [sekund=30] [lat=…] [lon=…] [meritko=15] [top=15]}.
 * Program běží v téže JVM, čítače se čtou přes ThreadMXBean, počítadla programu, {@link Vykon} a JFR. Vypíše řádky {@code klíč=hodnota}.
 */
public class MerStart {

	private static final int PORT = 47831;

	private static final class Vlakno {
		String jmeno;
		long cpu;
		long alokace;
	}

	public static void main(final String[] a) throws Exception {
		final Map<String, String> p = new HashMap<>();
		for (final String s : a) {
			final int i = s.indexOf('=');
			p.put(s.substring(0, i), s.substring(i + 1));
		}
		final int sekund = Integer.parseInt(p.getOrDefault("sekund", "30"));
		final int mapy = Integer.parseInt(p.getOrDefault("mapy", "8"));
		final String lat = p.getOrDefault("lat", "50.003");
		final String lon = p.getOrDefault("lon", "14.405");
		final String meritko = p.getOrDefault("meritko", "15");
		final Path data = Files.createTempDirectory("mer-start");
		final Path offline = data.resolve("data").resolve("offline-mapy");
		Files.createDirectories(offline);
		for (int i = 0; i < mapy; i++) {
			Files.copy(Paths.get(p.get("mapa")), offline.resolve("mapa" + i + ".map"), StandardCopyOption.REPLACE_EXISTING);
		}
		System.setProperty("geokuk.data", data.resolve("data").toString());
		System.setProperty("user.home", data.toString());
		System.setProperty("java.awt.headless", "false");

		final com.sun.management.ThreadMXBean tmx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		final com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		final Map<Long, Vlakno> vlakna = new HashMap<>();
		final Recording jfr = new Recording();
		jfr.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5));
		jfr.enable("jdk.Compilation").withThreshold(Duration.ZERO);
		jfr.start();

		final long cpu0 = os.getProcessCpuTime();
		final long t0 = System.nanoTime();
		final Thread spust = new Thread(() -> GeokukMain.main(new String[] { "--ovladani=" + PORT }), "MerStart spuštění");
		spust.setDaemon(true);
		spust.start();

		boolean scenar = false;
		long okno = 0;
		while (System.nanoTime() - t0 < sekund * 1_000_000_000L) {
			vzorek(tmx, vlakna);
			if (okno == 0 && Arrays.stream(java.awt.Window.getWindows()).anyMatch(w -> w.isShowing() && w instanceof java.awt.Frame)) {
				okno = System.nanoTime();
			}
			if (!scenar && okno != 0 && System.nanoTime() - okno > 1_000_000_000L) {
				// Pevný scénář po zobrazení okna: offline podklad a místo na zoomu 15 (počítá se jen úspěšný požadavek).
				scenar = posli("/podklad?jmeno=OFFLINE_MF") && posli("/pozice?lat=" + lat + "&lon=" + lon + "&meritko=" + meritko);
			}
			Thread.sleep(50);
		}
		vzorek(tmx, vlakna);
		final long cpuProces = os.getProcessCpuTime() - cpu0;
		final Map<String, long[]> skupiny = new TreeMap<>();
		long cpuJava = 0;
		long alokaceJava = 0;
		for (final Vlakno v : vlakna.values()) {
			final long[] s = skupiny.computeIfAbsent(skupina(v.jmeno), k -> new long[3]);
			s[0] += v.cpu;
			s[1] += v.alokace;
			s[2]++;
			cpuJava += v.cpu;
			alokaceJava += v.alokace;
		}
		System.out.println("scenar=" + scenar);
		System.out.printf(Locale.ROOT, "okno_zobrazeno_ms=%d%n", okno == 0 ? -1 : (okno - t0) / 1_000_000);
		for (final Map.Entry<String, long[]> e : skupiny.entrySet()) {
			System.out.printf(Locale.ROOT, "cpu_ms.%s=%d%nalokace_kB.%s=%d%n", e.getKey(), e.getValue()[0] / 1_000_000, e.getKey(), e.getValue()[1] / 1024);
		}
		System.out.printf(Locale.ROOT, "cpu_ms.CELKEM_proces=%d%ncpu_ms.CELKEM_javavlakna=%d%ncpu_ms.JVM_mimo_javavlakna=%d%nalokace_kB.CELKEM=%d%n", cpuProces / 1_000_000,
				cpuJava / 1_000_000, (cpuProces - cpuJava) / 1_000_000, alokaceJava / 1024);
		long gc = 0;
		long gcMs = 0;
		for (final GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) {
			gc += g.getCollectionCount();
			gcMs += g.getCollectionTime();
		}
		System.out.printf(Locale.ROOT, "gc.pocet=%d%ngc.ms=%d%njit.ms=%d%n", gc, gcMs, ManagementFactory.getCompilationMXBean().getTotalCompilationTime());
		final TreeMap<String, Integer> poc = new TreeMap<>();
		for (final Pocitadlo c : new ArrayList<>(SpravcePocitadel.getPocitadla())) {
			if (c.getName().matches("^ka\\d\\d .*") || c.getName().startsWith("Downloadlé")) {
				poc.merge("pocitadlo." + c.getName().replaceAll("[^\\p{L}\\p{N}]+", "_"), c.get(), Integer::sum);
			}
		}
		poc.forEach((k, v) -> System.out.println(k + "=" + v));
		for (final Vykon.Velicina v : Vykon.Velicina.values()) {
			System.out.println("vykon." + v.name() + ".pocet=" + Vykon.souhrn(v).pocet);
		}
		jfr.stop();
		final Path soubor = data.resolve("mer.jfr");
		jfr.dump(soubor);
		jfr.close();
		jfrVystup(soubor, Integer.parseInt(p.getOrDefault("top", "15")));
		System.out.flush();
		System.exit(0);
	}

	private static void vzorek(final com.sun.management.ThreadMXBean tmx, final Map<Long, Vlakno> vlakna) {
		for (final ThreadInfo ti : tmx.getThreadInfo(tmx.getAllThreadIds())) {
			if (ti == null) {
				continue;
			}
			final long id = ti.getThreadId();
			final Vlakno v = vlakna.computeIfAbsent(id, k -> new Vlakno());
			v.jmeno = ti.getThreadName();
			v.cpu = Math.max(v.cpu, tmx.getThreadCpuTime(id));
			v.alokace = Math.max(v.alokace, tmx.getThreadAllocatedBytes(id));
		}
	}

	static String skupina(final String j) {
		if (j.startsWith("AWT-EventQueue")) {
			return "EDT";
		}
		if (j.startsWith("Offline mapa")) {
			return "Offline_mapa";
		}
		if (j.contains("edvykreslen")) {
			return "Predvykresleni_prehledu";
		}
		if (j.contains("Příprava offline") || j.contains("Hlídání složky")) {
			return "Priprava_offline_map";
		}
		if (j.startsWith("MerStart") || j.equals("main")) {
			return "main_a_harness";
		}
		if (j.startsWith("pool-") || j.startsWith("ForkJoinPool")) {
			return "pooly";
		}
		return "ostatni";
	}

	private static boolean posli(final String cesta) {
		try {
			final HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + PORT + cesta).openConnection();
			c.setRequestMethod("POST");
			c.setConnectTimeout(1000);
			c.setReadTimeout(5000);
			return c.getResponseCode() == 200;
		} catch (final Exception e) {
			return false;
		}
	}

	private static void jfrVystup(final Path soubor, final int top) throws Exception {
		final Map<String, Integer> metody = new HashMap<>();
		final Map<String, Integer> vlaknaSkupiny = new TreeMap<>();
		int vzorku = 0;
		int obrys = 0;
		int png = 0;
		int jit = 0;
		long jitMs = 0;
		for (final RecordedEvent e : RecordingFile.readAllEvents(soubor)) {
			if (e.getEventType().getName().equals("jdk.Compilation")) {
				jit++;
				jitMs += e.getDuration().toMillis();
				continue;
			}
			final RecordedStackTrace st = e.getStackTrace();
			if (st == null || st.getFrames().isEmpty()) {
				continue;
			}
			vzorku++;
			final RecordedFrame f = st.getFrames().get(0);
			metody.merge(f.getMethod().getType().getName() + "." + f.getMethod().getName(), 1, Integer::sum);
			final RecordedThread t = e.getThread("sampledThread");
			vlaknaSkupiny.merge(skupina(t == null ? "?" : t.getJavaName()), 1, Integer::sum);
			boolean o = false;
			boolean pn = false;
			for (final RecordedFrame fr : st.getFrames()) {
				final String typ = fr.getMethod().getType().getName();
				o |= typ.endsWith("ObrysRaster");
				pn |= typ.startsWith("javax.imageio") || typ.startsWith("com.sun.imageio");
			}
			obrys += o ? 1 : 0;
			png += pn ? 1 : 0;
		}
		System.out.println("jfr.vzorku=" + vzorku);
		System.out.println("jfr.vzorku_s_ObrysRaster=" + obrys);
		System.out.println("jfr.vzorku_s_imageio=" + png);
		System.out.println("jfr.kompilaci=" + jit);
		System.out.println("jfr.kompilace_ms=" + jitMs);
		vlaknaSkupiny.forEach((k, v) -> System.out.println("jfr.vzorku_skupina." + k + "=" + v));
		final List<Map.Entry<String, Integer>> serazene = new ArrayList<>(metody.entrySet());
		serazene.sort((x, y) -> y.getValue() - x.getValue());
		for (int i = 0; i < Math.min(top, serazene.size()); i++) {
			System.out.println("top." + String.format("%02d", i + 1) + "." + serazene.get(i).getKey() + "=" + serazene.get(i).getValue());
		}
	}
}
