package cz.geokuk.testy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

/**
 * Zaseknutý test neblokuje celý běh: po {@link #LIMIT_VYCHOZI_S} sekundách (nebo {@code geokuk.testy.limitSekund}) vypíše zásobníky všech vláken a ukončí JVM. Navíc před prvním
 * testem inicializuje třídy AWT událostí: {@code MouseEvent.<clinit>} sahá na zámek AWT, který pod X11 drží vlákno AWT-XAWT při doručování události okna, a to zase potřebuje
 * {@code MouseEvent}; když ji poprvé inicializuje EDT nebo test s viditelným oknem, obě vlákna na sebe čekají navždy. Zaregistrovaný v surefire.
 */
@RunListener.ThreadSafe
public class ZabranaZaseknuti extends RunListener {

	static final long LIMIT_VYCHOZI_S = 900;
	private static final AtomicBoolean SPUSTENO = new AtomicBoolean();
	private static final String[] TRIDY_AWT = { "java.awt.event.InputEvent", "java.awt.event.MouseEvent", "java.awt.event.MouseWheelEvent", "java.awt.event.KeyEvent" };

	private static volatile String test = "mimo test";
	private static volatile long zacatekTestu = Long.MAX_VALUE;

	@Override
	public void testRunStarted(final Description description) throws Exception {
		if (!SPUSTENO.compareAndSet(false, true)) {
			return;
		}
		for (final String trida : TRIDY_AWT) {
			Class.forName(trida);
		}
		final long limitMs = Long.getLong("geokuk.testy.limitSekund", LIMIT_VYCHOZI_S) * 1000;
		final Thread hlidac = new Thread(() -> hlidej(limitMs), "Hlídač zaseknutých testů");
		hlidac.setDaemon(true);
		hlidac.start();
	}

	@Override
	public void testStarted(final Description description) {
		test = description.getDisplayName();
		zacatekTestu = System.nanoTime();
	}

	@Override
	public void testFinished(final Description description) {
		zacatekTestu = Long.MAX_VALUE;
	}

	private static void hlidej(final long limitMs) {
		try {
			for (;;) {
				Thread.sleep(1000);
				final long zacatek = zacatekTestu;
				if (zacatek != Long.MAX_VALUE && (System.nanoTime() - zacatek) / 1_000_000 > limitMs) {
					System.err.println("Test " + test + " běží déle než " + limitMs / 1000 + " s, výpis vláken a konec:\n" + vypisVlaken());
					System.err.flush();
					Runtime.getRuntime().halt(1);
				}
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	static String vypisVlaken() {
		final StringBuilder sb = new StringBuilder();
		for (final Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
			final Thread t = e.getKey();
			sb.append('"').append(t.getName()).append("\" ").append(t.getState()).append(t.isDaemon() ? " daemon" : "").append('\n');
			for (final StackTraceElement prvek : e.getValue()) {
				sb.append("\tat ").append(prvek).append('\n');
			}
			sb.append('\n');
		}
		return sb.toString();
	}
}
