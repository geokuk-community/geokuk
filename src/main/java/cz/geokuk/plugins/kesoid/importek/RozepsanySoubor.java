package cz.geokuk.plugins.kesoid.importek;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.geokuk.core.napoveda.Diagnostika;

/** Soubor, který se právě kopíruje nebo stahuje, a proto ho nejde přečíst celý. Načte se, až se dopíše. */
final class RozepsanySoubor {

	private static final Logger log = LoggerFactory.getLogger(RozepsanySoubor.class);

	/** Soubor změněný v posledních vteřinách je asi rozepsaný; zápis ho po dokončení změní znovu a načítání se spustí samo. */
	static final long STARI_MS = 15_000;

	private RozepsanySoubor() {
	}

	static boolean jeRozepsany(final File soubor) {
		return jeRozepsany(soubor, System.currentTimeMillis());
	}

	static boolean jeRozepsany(final File soubor, final long ted) {
		final long zmenen = soubor.lastModified();
		return zmenen != 0 && ted - zmenen < STARI_MS;
	}

	static void ohlas(final File soubor, final Exception chyba) {
		log.info("Soubor {} se právě zapisuje, načte se po dokončení zápisu: {}", soubor, chyba.toString());
		Diagnostika.zaznamenej("Soubor \"" + soubor.getName() + "\" se právě zapisuje, načte se po dokončení");
	}
}
