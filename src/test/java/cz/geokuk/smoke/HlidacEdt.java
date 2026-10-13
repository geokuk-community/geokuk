package cz.geokuk.smoke;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Měří, jak dlouho trvá obsluha jednotlivých událostí na EDT, a pamatuje si ty pomalé. Události začaté před {@link #konecStartu()} se počítají
 * zvlášť, start programu běží na EDT v jedné události. Zvlášť se počítá i přepínání vzhledu, které přestaví celé okno. U události delší než
 * {@link #PRAH_ZASOBNIKU_MS} si zapamatuje zásobník EDT, aby bylo vidět, kde obsluha visí, i u události vnořené v modálním dialogu. K pomalé
 * události připíše krok scénáře, čas GC během ní a kdy naposledy tikl vzorkovač; když stál i on, stál celý proces.
 */
public class HlidacEdt extends EventQueue {

	static final long PRAH_ZASOBNIKU_MS = 1500;
	private static final int HLOUBKA_ZASOBNIKU = 40;

	private final long prahMs;
	private final List<String> pomale = Collections.synchronizedList(new ArrayList<>());
	private volatile long nejdelsiMs;
	private volatile long nejdelsiStartMs;
	private volatile long nejdelsiVzhledMs;
	private volatile boolean vzhled;
	private volatile boolean startSkoncil;
	private volatile long konecStartuNs;
	private volatile Thread edt;
	private long cisloUdalosti;
	private final AtomicLong udalostiPoStartu = new AtomicLong();
	/** Právě obsluhované události od vnější po nejvnitřnější, dvojice číslo a začátek; mění se jen na EDT. */
	private final Deque<long[]> bezi = new ArrayDeque<>();
	/** Kopie {@link #bezi} pro vzorkovač. */
	private volatile long[][] beziProVzorkovac = new long[0][];
	private final Map<Long, String> zasobniky = new ConcurrentHashMap<>();
	private volatile long posledniTikNs = System.nanoTime();
	private volatile String krok;
	private final List<GarbageCollectorMXBean> gc = ManagementFactory.getGarbageCollectorMXBeans();
	private Thread vzorkovac;

	private HlidacEdt(final long prahMs) {
		this.prahMs = prahMs;
	}

	public static HlidacEdt zapni(final long prahMs) {
		final HlidacEdt hlidac = new HlidacEdt(prahMs);
		Toolkit.getDefaultToolkit().getSystemEventQueue().push(hlidac);
		hlidac.vzorkovac = new Thread(hlidac::vzorkuj, "HlidacEdt");
		hlidac.vzorkovac.setDaemon(true);
		hlidac.vzorkovac.start();
		return hlidac;
	}

	/** Vrátí původní frontu událostí. */
	public void vypni() {
		vzorkovac.interrupt();
		pop();
	}

	@Override
	protected void dispatchEvent(final AWTEvent event) {
		final long start = System.nanoTime();
		final long gcPred = casGcMs();
		final boolean priVzhledu = vzhled;
		final String krokUdalosti = krok;
		final long cislo = ++cisloUdalosti;
		edt = Thread.currentThread();
		bezi.push(new long[] { cislo, start });
		beziProVzorkovac = bezi.toArray(new long[0][]);
		try {
			super.dispatchEvent(event);
		} finally {
			bezi.pop();
			beziProVzorkovac = bezi.toArray(new long[0][]);
			final long konec = System.nanoTime();
			final long ms = (konec - start) / 1_000_000;
			final String zasobnikUdalosti = zasobniky.remove(cislo);
			final boolean priStartu = !startSkoncil || start - konecStartuNs < 0;
			if (priStartu) {
				nejdelsiStartMs = Math.max(nejdelsiStartMs, ms);
			} else {
				udalostiPoStartu.incrementAndGet();
				if (priVzhledu) {
					nejdelsiVzhledMs = Math.max(nejdelsiVzhledMs, ms);
				} else {
					nejdelsiMs = Math.max(nejdelsiMs, ms);
				}
			}
			if (ms >= prahMs) {
				pomale.add(ms + " ms: " + (priStartu ? "při startu: " : priVzhledu ? "při přepnutí vzhledu: " : "") + popis(event)
						+ (krokUdalosti == null ? "" : "\nKrok: " + krokUdalosti) + (bezi.isEmpty() ? "" : "\nVnořená událost (modální dialog)")
						+ "\nGC během události " + (casGcMs() - gcPred) + " ms, vzorkovač naposledy tikl před " + (konec - posledniTikNs) / 1_000_000 + " ms"
						+ (zasobnikUdalosti == null ? "" : "\nZásobník EDT po " + PRAH_ZASOBNIKU_MS + " ms:" + zasobnikUdalosti));
			}
		}
	}

	private long casGcMs() {
		long ms = 0;
		for (final GarbageCollectorMXBean g : gc) {
			ms += Math.max(0, g.getCollectionTime());
		}
		return ms;
	}

	/** Jednou za 100 ms se podívá, jestli obsluha některé probíhající události na EDT neběží déle než práh, a zapamatuje si zásobník. */
	private void vzorkuj() {
		final Set<Long> hotovo = new HashSet<>();
		while (true) {
			try {
				Thread.sleep(100);
			} catch (final InterruptedException e) {
				return;
			}
			posledniTikNs = System.nanoTime();
			final long[][] udalosti = beziProVzorkovac;
			final Thread vlakno = edt;
			final List<Long> nove = new ArrayList<>();
			for (final long[] u : udalosti) {
				if (!hotovo.contains(u[0]) && posledniTikNs - u[1] >= PRAH_ZASOBNIKU_MS * 1_000_000) {
					nove.add(u[0]);
				}
			}
			if (nove.isEmpty() || vlakno == null) {
				continue;
			}
			final StackTraceElement[] prvky = vlakno.getStackTrace();
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < Math.min(prvky.length, HLOUBKA_ZASOBNIKU); i++) {
				sb.append("\n  at ").append(prvky[i]);
			}
			final Set<Long> stale = new HashSet<>();
			for (final long[] u : beziProVzorkovac) {
				stale.add(u[0]);
			}
			for (final Long c : nove) {
				// Zásobník platí jen pro událost, která po jeho přečtení pořád běží.
				if (stale.contains(c)) {
					zasobniky.put(c, sb.toString());
					hotovo.add(c);
				}
			}
			hotovo.retainAll(stale);
		}
	}

	/** Krok scénáře (třeba položka menu), ke kterému patří následující události; null = žádný. */
	public void krok(final String popis) {
		krok = popis;
	}

	/** Celkový čas GC v programu. */
	public long getGcMs() {
		return casGcMs();
	}

	/** Další události už nepatří ke startu programu. */
	public void konecStartu() {
		konecStartuNs = System.nanoTime();
		startSkoncil = true;
	}

	/** Události mezi voláním s true a s false patří k přepínání vzhledu. */
	public void vzhled(final boolean prepina) {
		vzhled = prepina;
	}

	public long getNejdelsiVzhledMs() {
		return nejdelsiVzhledMs;
	}

	public long getNejdelsiMs() {
		return nejdelsiMs;
	}

	public long getUdalostiPoStartu() {
		return udalostiPoStartu.get();
	}

	public long getNejdelsiStartMs() {
		return nejdelsiStartMs;
	}

	public List<String> getPomale() {
		synchronized (pomale) {
			return new ArrayList<>(pomale);
		}
	}

	private static String popis(final AWTEvent event) {
		final String s = event.toString();
		return s.length() > 300 ? s.substring(0, 300) : s;
	}
}
