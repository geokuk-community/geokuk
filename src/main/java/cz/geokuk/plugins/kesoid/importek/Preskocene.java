package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.io.IOException;

import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import lombok.extern.slf4j.Slf4j;

/**
 * Počítá záznamy, které se nepodařilo načíst. Vadný záznam nesmí shodit celý
 * soubor a nesmí ani zahltit log, proto se na konci ohlásí jednou souhrnně.
 */
@Slf4j
class Preskocene {

	/** Od kolika vadných keší se většina bere jako jiná struktura databáze, ne jako pár poškozených záznamů. */
	static final int MIN_KESI_PRO_HLASKU = 100;

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

	/** Když nejde přečíst většina keší, má databáze nejspíš jinou strukturu, než GeoKuk zná; načtené keše zůstanou. */
	void ohlasVetsinuKesi(final int nacteno, final File soubor, final String program) {
		if (pocet >= MIN_KESI_PRO_HLASKU && pocet > nacteno) {
			FExceptionDumper.dump(new IOException("Z databáze " + program + " \"" + soubor + "\" nejde přečíst " + pocet + " z " + (pocet + nacteno)
					+ " keší, databáze má nejspíš jinou strukturu, než GeoKuk zná. Načetlo se jen " + nacteno + " keší.", prvniChyba), EExceptionSeverity.DISPLAY,
					"Databáze " + program + " se načetla jen zčásti");
		}
	}
}
