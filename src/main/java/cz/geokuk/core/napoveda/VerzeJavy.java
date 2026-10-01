package cz.geokuk.core.napoveda;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import cz.geokuk.core.program.FConst;

/** Doporučená a nejnižší verze Javy z {@code java.properties} a porovnání s Javou, ve které program běží. */
public final class VerzeJavy {

	static final String SOUBOR = "java.properties";

	private VerzeJavy() {}

	/** Program běží v Javě přibalené v přenosné verzi. */
	public static boolean jePribalena() {
		final File runtime = new File(FConst.JAR_DIR, "runtime");
		try {
			return FConst.JAR_DIR_EXISTUJE && new File(System.getProperty("java.home")).getCanonicalFile().equals(runtime.getCanonicalFile());
		} catch (final IOException e) {
			return false;
		}
	}

	public static String aktualni() {
		return System.getProperty("java.version", "0");
	}

	/** Vlastnosti {@code java.properties} z tohoto programu. */
	public static Properties vlastni() {
		final Properties p = new Properties();
		try (InputStream in = VerzeJavy.class.getResourceAsStream("/" + SOUBOR)) {
			if (in != null) {
				p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
			}
		} catch (final IOException e) {
			// bez souboru se nic neporovnává
		}
		return p;
	}

	/** Je verze Javy {@code verze} starší než {@code oproti}? Chybějící údaj nevadí. */
	public static boolean jeStarsi(final String verze, final String oproti) {
		if (verze == null || oproti == null || oproti.trim().isEmpty()) {
			return false;
		}
		final int[] a = cisla(verze);
		final int[] b = cisla(oproti);
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			final int x = i < a.length ? a[i] : 0;
			final int y = i < b.length ? b[i] : 0;
			if (x != y) {
				return x < y;
			}
		}
		return false;
	}

	/** „1.8.0_392“ → 8, 0, 392; „21.0.12+7“ → 21, 0, 12. */
	static int[] cisla(final String verze) {
		String v = verze.trim();
		if (v.startsWith("1.")) {
			v = v.substring(2);
		}
		final String[] casti = v.split("[^0-9]+");
		int n = 0;
		final int[] vysledek = new int[Math.min(casti.length, 3)];
		for (final String c : casti) {
			if (n == vysledek.length) {
				break;
			}
			if (!c.isEmpty()) {
				vysledek[n++] = Integer.parseInt(c);
			}
		}
		final int[] oriznute = new int[n];
		System.arraycopy(vysledek, 0, oriznute, 0, n);
		return oriznute;
	}
}
