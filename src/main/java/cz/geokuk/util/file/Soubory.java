package cz.geokuk.util.file;

import java.io.File;

import lombok.extern.slf4j.Slf4j;

/** Mazání a zakládání složek tam, kde neúspěch ohlásí až další krok; tady se jen zaloguje. */
@Slf4j
public final class Soubory {

	private Soubory() {}

	public static void smaz(final File soubor) {
		if (!soubor.delete() && soubor.exists()) {
			log.debug("Soubor {} nejde smazat", soubor);
		}
	}

	public static void zalozSlozky(final File slozka) {
		if (!slozka.mkdirs() && !slozka.isDirectory()) {
			log.warn("Složku {} nejde vytvořit", slozka);
		}
	}

	public static void nastavCas(final File soubor, final long cas) {
		if (!soubor.setLastModified(cas)) {
			log.debug("Souboru {} nejde nastavit čas", soubor);
		}
	}
}
