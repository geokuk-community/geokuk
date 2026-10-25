package cz.geokuk.core.program;

import java.util.Map;
import java.util.TreeMap;

import lombok.extern.slf4j.Slf4j;

/** Dočasné měření: vzorkuje zásobník EDT při startu a po skončení vypíše součty po fázích. */
@Slf4j
public final class StartProfil {
	public static volatile Thread edt;
	public static volatile String faze = "před EDT";
	private static final Map<String, Integer> pocty = new TreeMap<>();
	private static volatile boolean konec;

	private StartProfil() {
	}

	public static void spust() {
		final Thread t = new Thread(() -> {
			final long konecCas = System.currentTimeMillis() + 6000;
			while (!konec && System.currentTimeMillis() < konecCas) {
				final Thread e = edt;
				if (e != null) {
					final StackTraceElement[] st = e.getStackTrace();
					if (st.length > 0 && !(st[0].getMethodName().equals("wait0") || st[0].getMethodName().equals("park") || st[0].getMethodName().equals("poll"))) {
						final StringBuilder sb = new StringBuilder(faze).append(" | ");
						int n = 0;
						for (final StackTraceElement s : st) {
							if (s.getClassName().startsWith("cz.geokuk") && !s.getClassName().contains("Vykon")) {
								sb.append(zkrat(s)).append(" < ");
								if (++n >= 4) {
									break;
								}
							}
						}
						synchronized (pocty) {
							pocty.merge(sb.toString(), 1, Integer::sum);
						}
					}
				}
				try {
					Thread.sleep(4);
				} catch (final InterruptedException ex) {
					return;
				}
			}
			vypis();
		}, "Profil startu");
		t.setDaemon(true);
		t.start();
	}

	private static String zkrat(final StackTraceElement s) {
		return s.getClassName().replaceAll(".*\\.", "") + "." + s.getMethodName();
	}

	public static void konec() {
		konec = true;
	}

	private static void vypis() {
		synchronized (pocty) {
			pocty.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(120).forEach(e -> log.info("PROF {} x {}", e.getValue(), e.getKey()));
		}
	}
}
