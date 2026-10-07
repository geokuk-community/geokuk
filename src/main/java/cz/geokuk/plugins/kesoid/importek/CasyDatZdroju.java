package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Čas poslední změny dat každého zdroje; podle něj se zdroje čtou, nejnovější první, takže při duplicitě vyhraje. Zdroj, který se změnil jen na disku (jiný program databázi
 * otevřel), ale přečetl se stejný obsah, si nechá svůj dřívější čas.
 */
class CasyDatZdroju {

	static final class Zaznam {
		final long otiskObsahu;
		final long cas;

		Zaznam(final long otiskObsahu, final long cas) {
			this.otiskObsahu = otiskObsahu;
			this.cas = cas;
		}
	}

	private final Map<File, Zaznam> zaznamy = new HashMap<>();

	synchronized Zaznam get(final File zdroj) {
		return zaznamy.get(zdroj);
	}

	synchronized void put(final File zdroj, final Zaznam zaznam) {
		zaznamy.put(zdroj, zaznam);
	}

	/** Čas změny souboru i jeho WAL, kam databáze zapisuje. */
	static long casZmeny(final File soubor) {
		return Math.max(soubor.lastModified(), new File(soubor.getPath() + "-wal").lastModified());
	}

	/** Čas dat zdroje po přečtení obsahu s daným otiskem; {@code casZmeny} je čas souboru z doby před čtením. */
	synchronized long casPoPrecteni(final File zdroj, final long otiskObsahu, final long casZmeny) {
		final Zaznam z = zaznamy.get(zdroj);
		return z != null && z.otiskObsahu == otiskObsahu ? z.cas : casZmeny;
	}
}
