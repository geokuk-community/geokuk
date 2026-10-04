package cz.geokuk.smoke;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.*;

/**
 * Měří, jak dlouho trvá obsluha jednotlivých událostí na EDT, a pamatuje si ty pomalé. Události začaté před {@link #konecStartu()} se počítají
 * zvlášť, start programu běží na EDT v jedné události. Zvlášť se počítá i přepínání vzhledu, které přestaví celé okno.
 */
public class HlidacEdt extends EventQueue {

	private final long prahMs;
	private final List<String> pomale = Collections.synchronizedList(new ArrayList<>());
	private volatile long nejdelsiMs;
	private volatile long nejdelsiStartMs;
	private volatile long nejdelsiVzhledMs;
	private volatile boolean vzhled;
	private volatile boolean startSkoncil;
	private volatile long konecStartuNs;

	// Měření (dočasné): vzorkování zásobníku EDT během událostí při startu.
	private volatile Thread edt;
	private volatile long udalostOdNs;
	private final Map<String, Integer> vzorkyVcetne = new HashMap<>();
	private final Map<String, Integer> vzorkyVrchol = new HashMap<>();
	private volatile int vzorku;

	private HlidacEdt(final long prahMs) {
		this.prahMs = prahMs;
		final Thread vzorkovac = new Thread(this::vzorkuj, "Vzorkovač EDT");
		vzorkovac.setDaemon(true);
		vzorkovac.start();
	}

	private void vzorkuj() {
		while (true) {
			try {
				Thread.sleep(10);
			} catch (final InterruptedException e) {
				return;
			}
			final Thread t = edt;
			final long od = udalostOdNs;
			if (t == null || od == 0 || startSkoncil || System.nanoTime() - od < 50_000_000L) {
				continue;
			}
			final StackTraceElement[] z = t.getStackTrace();
			if (udalostOdNs != od || z.length == 0) {
				continue;
			}
			synchronized (vzorkyVcetne) {
				vzorku++;
				final Set<String> videne = new HashSet<>();
				for (final StackTraceElement e : z) {
					if (e.getClassName().startsWith("cz.geokuk.") && !e.getClassName().startsWith("cz.geokuk.smoke.")) {
						final String m = e.getClassName().substring(10) + "." + e.getMethodName();
						if (videne.add(m)) {
							vzorkyVcetne.merge(m, 1, Integer::sum);
						}
					}
				}
				String geokuk = "-";
				for (final StackTraceElement e : z) {
					if (e.getClassName().startsWith("cz.geokuk.")) {
						geokuk = e.getClassName().substring(10) + "." + e.getMethodName() + ":" + e.getLineNumber();
						break;
					}
				}
				vzorkyVrchol.merge(z[0].getClassName() + "." + z[0].getMethodName() + " <- " + geokuk, 1, Integer::sum);
			}
		}
	}

	/** Profil startu: vzorky po 10 ms, „včetně“ = metoda je kdekoli v zásobníku, „vrchol“ = nejvrchnější rámec a nejbližší rámec GeoKuku. */
	public List<String> getProfilStartu() {
		final List<String> vysledek = new ArrayList<>();
		synchronized (vzorkyVcetne) {
			vysledek.add("vzorku " + vzorku + " (po 10 ms)");
			vzorkyVcetne.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(40).forEach(e -> vysledek.add("vcetne " + e.getValue() + " " + e.getKey()));
			vzorkyVrchol.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(25).forEach(e -> vysledek.add("vrchol " + e.getValue() + " " + e.getKey()));
		}
		return vysledek;
	}

	public static HlidacEdt zapni(final long prahMs) {
		final HlidacEdt hlidac = new HlidacEdt(prahMs);
		Toolkit.getDefaultToolkit().getSystemEventQueue().push(hlidac);
		return hlidac;
	}

	@Override
	protected void dispatchEvent(final AWTEvent event) {
		final long start = System.nanoTime();
		final boolean priVzhledu = vzhled;
		edt = Thread.currentThread();
		final long predchozi = udalostOdNs;
		udalostOdNs = start;
		try {
			super.dispatchEvent(event);
		} finally {
			udalostOdNs = predchozi;
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
				pomale.add(ms + " ms: " + (priStartu ? "při startu: " : priVzhledu ? "při přepnutí vzhledu: " : "") + popis(event));
			}
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
