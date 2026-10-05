package cz.geokuk.smoke;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.*;

/**
 * Měří, jak dlouho trvá obsluha jednotlivých událostí na EDT, a pamatuje si ty pomalé. Události začaté před {@link #konecStartu()} se počítají
 * zvlášť, start programu běží na EDT v jedné události. Zvlášť se počítá i přepínání vzhledu, které přestaví celé okno. U události delší než
 * {@link #PRAH_ZASOBNIKU_MS} si zapamatuje zásobník EDT, aby bylo vidět, kde obsluha visí.
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
	private volatile long probihaOdNs;
	private volatile long cisloUdalosti;
	private volatile long cisloVnejsi;
	private volatile long cisloZasobniku;
	private volatile String zasobnik;
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
		final boolean priVzhledu = vzhled;
		final boolean vnorena = probihaOdNs != 0;
		final long cislo = ++cisloUdalosti;
		if (!vnorena) {
			edt = Thread.currentThread();
			cisloVnejsi = cislo;
			probihaOdNs = start;
		}
		try {
			super.dispatchEvent(event);
		} finally {
			if (!vnorena) {
				probihaOdNs = 0;
			}
			final long ms = (System.nanoTime() - start) / 1_000_000;
			final boolean priStartu = !startSkoncil || start - konecStartuNs < 0;
			if (priStartu) {
				nejdelsiStartMs = Math.max(nejdelsiStartMs, ms);
			} else if (priVzhledu) {
				nejdelsiVzhledMs = Math.max(nejdelsiVzhledMs, ms);
			} else {
				nejdelsiMs = Math.max(nejdelsiMs, ms);
			}
			if (ms >= prahMs) {
				final String zasobnikUdalosti = !vnorena && cisloZasobniku == cislo ? zasobnik : null;
				pomale.add(ms + " ms: " + (priStartu ? "při startu: " : priVzhledu ? "při přepnutí vzhledu: " : "") + popis(event)
						+ (zasobnikUdalosti == null ? "" : "\nZásobník EDT po " + PRAH_ZASOBNIKU_MS + " ms:" + zasobnikUdalosti));
			}
		}
	}

	/** Jednou za 100 ms se podívá, jestli obsluha události na EDT neběží déle než práh, a zapamatuje si její zásobník. */
	private void vzorkuj() {
		long hotovo = 0;
		while (true) {
			try {
				Thread.sleep(100);
			} catch (final InterruptedException e) {
				return;
			}
			final long od = probihaOdNs;
			final long cislo = cisloVnejsi;
			final Thread vlakno = edt;
			if (od == 0 || vlakno == null || cislo == hotovo || System.nanoTime() - od < PRAH_ZASOBNIKU_MS * 1_000_000) {
				continue;
			}
			final StackTraceElement[] prvky = vlakno.getStackTrace();
			if (probihaOdNs != od || cisloVnejsi != cislo) {
				continue;
			}
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < Math.min(prvky.length, HLOUBKA_ZASOBNIKU); i++) {
				sb.append("\n  at ").append(prvky[i]);
			}
			zasobnik = sb.toString();
			cisloZasobniku = cislo;
			hotovo = cislo;
		}
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
