package cz.geokuk.plugins.kesoid.importek;

import java.io.File;


/** Soubor, který se právě kopíruje nebo stahuje, a proto ho nejde přečíst celý. Načte se, až se dopíše. */
final class RozepsanySoubor {

	/** Soubor změněný v posledních vteřinách je asi rozepsaný; zápis ho po dokončení změní znovu a načítání se spustí samo. */
	static final long STARI_MS = 15_000;

	/** Čerstvě změněný soubor, který nejde přečíst: čeká se na dokončení zápisu a načte se znovu; když je pořád vadný, hlásí se chyba. */
	static final class Rozepsany extends DatabazeJinehoProgramu.Zamcena {
		private static final long serialVersionUID = 1L;

		Rozepsany(final File soubor, final Throwable pricina) {
			super("Soubor \"" + soubor.getName() + "\" se právě zapisuje, načte se po dokončení zápisu.", pricina);
		}
	}

	private RozepsanySoubor() {
	}

	static boolean jeRozepsany(final File soubor) {
		return jeRozepsany(soubor, System.currentTimeMillis());
	}

	static boolean jeRozepsany(final File soubor, final long ted) {
		final long zmenen = soubor.lastModified();
		return zmenen != 0 && ted - zmenen < STARI_MS;
	}
}
