package cz.geokuk.util.process;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

/** Otevře složku ve správci souborů systému (Průzkumník, Finder…), ne v internetovém prohlížeči. */
@Slf4j
public final class OtevreniSlozky {

	/** Čím se složka otevře. */
	enum Zpusob {
		/** {@link Desktop#open(File)}. */
		DESKTOP,
		EXPLORER,
		OPEN,
		XDG_OPEN
	}

	private OtevreniSlozky() {}

	/** Otevře složku; nevolat z EDT (spouští proces). */
	public static void otevri(final File slozka) {
		final Zpusob zpusob = zvolZpusob(System.getProperty("os.name", ""), Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN));
		try {
			if (zpusob == Zpusob.DESKTOP) {
				Desktop.getDesktop().open(slozka);
				return;
			}
		} catch (final IOException | RuntimeException e) {
			log.warn("Složku {} nejde otevřít přes Desktop, zkouší se příkaz systému", slozka, e);
		}
		final Zpusob nahrada = zpusob == Zpusob.DESKTOP ? zvolZpusob(System.getProperty("os.name", ""), false) : zpusob;
		try {
			// Argumenty se předávají jako pole, takže cesta s diakritikou a mezerami nepotřebuje uvozovky.
			new ProcessBuilder(prikaz(nahrada, slozka)).start();
		} catch (final IOException e) {
			log.warn("Složku {} se nepodařilo otevřít", slozka, e);
		}
	}

	/** Desktop, když umí otevřít složku, jinak příkaz podle systému. */
	static Zpusob zvolZpusob(final String os, final boolean desktopUmiOpen) {
		if (desktopUmiOpen) {
			return Zpusob.DESKTOP;
		}
		final String s = os.toLowerCase(Locale.ROOT);
		if (s.contains("win")) {
			return Zpusob.EXPLORER;
		}
		if (s.contains("mac") || s.contains("darwin")) {
			return Zpusob.OPEN;
		}
		return Zpusob.XDG_OPEN;
	}

	static List<String> prikaz(final Zpusob zpusob, final File slozka) {
		final String cesta = slozka.getAbsolutePath();
		switch (zpusob) {
		case EXPLORER:
			return Arrays.asList("explorer.exe", cesta);
		case OPEN:
			return Arrays.asList("open", cesta);
		case XDG_OPEN:
		case DESKTOP:
		default:
			return Arrays.asList("xdg-open", cesta);
		}
	}
}
