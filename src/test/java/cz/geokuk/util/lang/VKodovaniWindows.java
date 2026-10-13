package cz.geokuk.util.lang;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;

/**
 * Spustí testy ve zvláštní JVM s výchozím kódováním windows-1250 (Java 8 na českých Windows). Testy v surefire běží s -Dfile.encoding=UTF-8, takže závislost na
 * výchozím kódování jinak neodhalí.
 */
public final class VKodovaniWindows {

	public static final String KODOVANI = "windows-1250";

	private VKodovaniWindows() {}

	public static void spust(final Class<?> testy) throws Exception {
		final Process p = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(), "-Dfile.encoding=" + KODOVANI, "-Djava.awt.headless=true", "-cp",
				System.getProperty("java.class.path"), "org.junit.runner.JUnitCore", testy.getName()).redirectErrorStream(true).start();
		final StringBuilder vystup = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			for (String radek = r.readLine(); radek != null; radek = r.readLine()) {
				vystup.append(radek).append('\n');
			}
		}
		Assert.assertTrue("JVM s testy neskončila", p.waitFor(2, TimeUnit.MINUTES));
		Assert.assertEquals(vystup.toString(), 0, p.exitValue());
	}
}
