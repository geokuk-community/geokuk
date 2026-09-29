package cz.geokuk.plugins.kesoid.importek;

import lombok.extern.slf4j.Slf4j;

/**
 * Počítá záznamy, které se nepodařilo načíst. Vadný záznam nesmí shodit celý
 * soubor a nesmí ani zahltit log, proto se na konci ohlásí jednou souhrnně.
 */
@Slf4j
class Preskocene {

	private final String co;

	private int pocet;

	private String prvniZaznam;

	private Exception prvniChyba;

	Preskocene(final String co) {
		this.co = co;
	}

	void preskoc(final String zaznam, final Exception chyba) {
		if (pocet == 0) {
			prvniZaznam = zaznam;
			prvniChyba = chyba;
		}
		pocet++;
	}

	void ohlas() {
		if (pocet > 0) {
			log.warn("Nepodařilo se načíst {} ({}), první je {}.", co, pocet, prvniZaznam, prvniChyba);
		}
	}
}
