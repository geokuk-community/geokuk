package cz.geokuk.smoke;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Měří, jak dlouho trvá obsluha jednotlivých událostí na EDT, a pamatuje si ty pomalé. Události začaté před {@link #konecStartu()} se počítají
 * zvlášť, start programu běží na EDT v jedné události. Zvlášť se počítá i přepínání vzhledu, které přestaví celé okno. U události delší než
 * {@link #PRAH_ZASOBNIKU_MS} si zapamatuje zásobník EDT, aby bylo vidět, kde obsluha visí, i u události vnořené v modálním dialogu. K pomalé
 * události připíše krok scénáře, čas GC během ní a kdy naposledy tikl vzorkovač; když stál i on, stál celý proces. Dobu, kdy vzorkovač netikl
 * aspoň {@link #PAUZA_MS}, bere jako pauzu JVM (runner, GC) a do délky události ji nepočítá.
 */
public class HlidacEdt extends EventQueue {

	static final long PRAH_ZASOBNIKU_MS = 1500;
	/** Vzorkovač tiká po 100 ms; mezera aspoň takhle dlouhá znamená, že stál celý proces, ne jen EDT. */
	static final long PAUZA_MS = 1000;
	/** Spánek vzorkovače mezi tiky; ten do pauzy nepatří. */
	private static final long SPANEK_NS = 100_000_000;
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
	/** Pauzy JVM jako dvojice začátek a konec v ns. */
	private final Deque<long[]> pauzy = new ConcurrentLinkedDeque<>();
	private volatile long nejdelsiPauzaMs;
	private volatile boolean vzorkovacStoji;
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
			final long celkemMs = (konec - start) / 1_000_000;
			final long pauzaMs = pauzaBehem(start, konec) / 1_000_000;
			final long ms = Math.max(0, celkemMs - pauzaMs);
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
			if (celkemMs >= prahMs) {
				pomale.add(ms + " ms: " + (priStartu ? "při startu: " : priVzhledu ? "při přepnutí vzhledu: " : "") + popis(event)
						+ (krokUdalosti == null ? "" : "\nKrok: " + krokUdalosti) + (bezi.isEmpty() ? "" : "\nVnořená událost (modální dialog)")
						+ "\nGC během události " + (casGcMs() - gcPred) + " ms, vzorkovač naposledy tikl před " + (konec - posledniTikNs) / 1_000_000 + " ms"
						+ (pauzaMs == 0 ? "" : "\nPauza JVM " + pauzaMs + " ms se nepočítá, celkem " + celkemMs + " ms")
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

	/** Kolik ns z intervalu stál celý proces: zapsané pauzy i ta, která ještě trvá (vzorkovač od té doby netikl). */
	private long pauzaBehem(final long od, final long doNs) {
		return pauzaBehem(pauzy, posledniTikNs, od, doNs);
	}

	/**
	 * @param tik
	 *            poslední tik vzorkovače; vzorkovač zapisuje pauzu dřív, než ho posune, takže probíhající pauza začíná až po konci poslední zapsané
	 */
	static long pauzaBehem(final Iterable<long[]> pauzy, final long tik, final long od, final long doNs) {
		long ns = 0;
		long konecZapsanych = Long.MIN_VALUE;
		for (final long[] p : pauzy) {
			ns += Math.max(0, Math.min(p[1], doNs) - Math.max(p[0], od));
			konecZapsanych = Math.max(konecZapsanych, p[1]);
		}
		if (doNs - tik >= PAUZA_MS * 1_000_000) {
			ns += Math.max(0, doNs - Math.max(Math.max(tik + SPANEK_NS, konecZapsanych), od));
		}
		return ns;
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
			if (vzorkovacStoji) {
				continue;
			}
			final long ted = System.nanoTime();
			final long mezera = ted - posledniTikNs;
			if (mezera >= PAUZA_MS * 1_000_000) {
				// Zapsat dřív než nový tik, aby událost, která právě končí, pauzu neminula.
				pauzy.add(new long[] { posledniTikNs + SPANEK_NS, ted });
				nejdelsiPauzaMs = Math.max(nejdelsiPauzaMs, mezera / 1_000_000);
				while (pauzy.size() > 1000) {
					pauzy.pollFirst();
				}
			}
			posledniTikNs = ted;
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

	/** Pro testy: vzorkovač přestane tikat jako při pauze JVM. */
	void zastavVzorkovac(final boolean stoji) {
		vzorkovacStoji = stoji;
	}

	/** Kolikrát stál celý proces aspoň {@link #PAUZA_MS}. */
	public int getPocetPauz() {
		return pauzy.size();
	}

	public long getNejdelsiPauzaMs() {
		return nejdelsiPauzaMs;
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
