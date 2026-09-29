package cz.geokuk.smoke;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.*;

/**
 * Měří, jak dlouho trvá obsluha jednotlivých událostí na EDT, a pamatuje si ty pomalé.
 */
public class HlidacEdt extends EventQueue {

	private final long prahMs;
	private final List<String> pomale = Collections.synchronizedList(new ArrayList<>());
	private volatile long nejdelsiMs;

	private HlidacEdt(final long prahMs) {
		this.prahMs = prahMs;
	}

	public static HlidacEdt zapni(final long prahMs) {
		final HlidacEdt hlidac = new HlidacEdt(prahMs);
		Toolkit.getDefaultToolkit().getSystemEventQueue().push(hlidac);
		return hlidac;
	}

	@Override
	protected void dispatchEvent(final AWTEvent event) {
		final long start = System.nanoTime();
		try {
			super.dispatchEvent(event);
		} finally {
			final long ms = (System.nanoTime() - start) / 1_000_000;
			if (ms > nejdelsiMs) {
				nejdelsiMs = ms;
			}
			if (ms >= prahMs) {
				pomale.add(ms + " ms: " + popis(event));
			}
		}
	}

	public long getNejdelsiMs() {
		return nejdelsiMs;
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
