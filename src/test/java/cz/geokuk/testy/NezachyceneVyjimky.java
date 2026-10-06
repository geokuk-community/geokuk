package cz.geokuk.testy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

/**
 * Výjimka, kterou nikdo nezachytil (ve vlákně na pozadí nebo na EDT), shodí běh testů, i když samotný test prošel. Zaregistrovaný v surefire.
 */
@RunListener.ThreadSafe
public class NezachyceneVyjimky extends RunListener {

	private final List<String> vyjimky = new ArrayList<>();
	private volatile String test = "mimo test";

	@Override
	public void testRunStarted(final Description description) {
		final Thread.UncaughtExceptionHandler puvodni = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((vlakno, t) -> {
			final StringWriter vypis = new StringWriter();
			t.printStackTrace(new PrintWriter(vypis));
			synchronized (vyjimky) {
				vyjimky.add(test + ", vlákno " + vlakno.getName() + ": " + vypis);
			}
			if (puvodni != null) {
				puvodni.uncaughtException(vlakno, t);
			}
		});
	}

	@Override
	public void testStarted(final Description description) {
		test = description.getDisplayName();
	}

	@Override
	public void testRunFinished(final Result result) {
		synchronized (vyjimky) {
			if (!vyjimky.isEmpty()) {
				throw new AssertionError("Nezachycené výjimky (" + vyjimky.size() + "):\n" + String.join("\n", vyjimky));
			}
		}
	}
}
