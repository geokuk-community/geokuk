package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.util.*;

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

	/** Převezme záznamy uložené minulým během programu; vadné přeskočí, platné z tohoto běhu nepřepíše. */
	synchronized void nacti(final Collection<String> ulozene) {
		for (final String radek : ulozene) {
			final String[] casti = radek.split(";", 3);
			if (casti.length < 3 || casti[2].isEmpty()) {
				continue;
			}
			try {
				zaznamy.putIfAbsent(new File(casti[2]), new Zaznam(Long.parseUnsignedLong(casti[0], 16), Long.parseLong(casti[1])));
			} catch (final NumberFormatException e) {
				// poškozený záznam: zdroj dostane čas souboru jako bez záznamu
			}
		}
	}

	/** Zapomene zdroje, které nejsou mezi ponechanými, a vrátí záznamy k uložení seřazené podle cesty. */
	synchronized Set<String> ponechej(final java.util.function.Predicate<File> ponechat) {
		zaznamy.keySet().removeIf(ponechat.negate());
		final Set<String> vysledek = new TreeSet<>(Comparator.comparing((final String r) -> r.substring(r.indexOf(';', r.indexOf(';') + 1) + 1)));
		for (final Map.Entry<File, Zaznam> e : zaznamy.entrySet()) {
			vysledek.add(Long.toHexString(e.getValue().otiskObsahu) + ";" + e.getValue().cas + ";" + e.getKey().getPath());
		}
		return new LinkedHashSet<>(vysledek);
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
