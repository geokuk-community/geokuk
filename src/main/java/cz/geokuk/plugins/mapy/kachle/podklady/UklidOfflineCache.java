package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Cache offline mapy drží dlaždice a symboly jen posledních několika globálních klíčů (téma, měřítko, písmo), aby po výměně tématu nerostla. Klíče si pamatuje
 * v souboru ve složce symbolů, nejnovější první, s otiskem sestavy souborů, pro kterou se cache naposledy uklízela.
 */
final class UklidOfflineCache {

	static final int PONECHAT = 3;
	static final String SOUBOR = "klice.txt";
	/** Globální klíč offline mapy (o) a překryvu (t), viz {@link OfflineRenderer#klic(String)}. */
	static final Pattern KLIC = Pattern.compile("[ot][0-9a-f]{8}");
	/** Typ dlaždice v cache: globální klíč a oblastní část, viz {@link OblastniKlic#klic}. */
	static final Pattern TYP = Pattern.compile("([ot][0-9a-f]{8})\\.[0-9a-f]{8}");
	private static final Pattern SYMBOLY = Pattern.compile("symboly-[0-9a-f]{8}\\.bin");

	private UklidOfflineCache() {}

	/**
	 * Zapíše klíč jako nejnovější a smaže soubory symbolů, které žádný z posledních klíčů nepoužívá.
	 *
	 * @param symboly
	 *            soubor symbolů tématu, ze kterého jsou dlaždice klíče
	 * @param sestava
	 *            otisk souborů, pro které se cache uklízí, viz {@link OblastniKlic#getSestava()}
	 * @return klíče, jejichž dlaždice v cache zůstávají
	 */
	static Set<String> zaznamenej(final File slozka, final String klic, final File symboly, final String sestava) throws IOException {
		final Path soubor = slozka.toPath().resolve(SOUBOR);
		final LinkedHashMap<String, String> klice = new LinkedHashMap<>();
		klice.put(klic, symboly.getName() + ' ' + sestava);
		nacti(soubor, klice);
		final List<String> radky = new ArrayList<>();
		for (final Map.Entry<String, String> e : klice.entrySet()) {
			radky.add(e.getKey() + ' ' + e.getValue());
		}
		Files.createDirectories(slozka.toPath());
		final Path docasny = slozka.toPath().resolve(SOUBOR + ".tmp");
		Files.write(docasny, radky, StandardCharsets.UTF_8);
		Files.move(docasny, soubor, StandardCopyOption.REPLACE_EXISTING);

		final File[] soubory = slozka.listFiles(f -> SYMBOLY.matcher(f.getName()).matches() && klice.values().stream().noneMatch(v -> v.startsWith(f.getName() + ' ')));
		if (soubory != null) {
			for (final File f : soubory) {
				Files.deleteIfExists(f.toPath());
			}
		}
		return klice.keySet();
	}

	/** Klíče, jejichž dlaždice v cache zůstávají podle posledního zápisu. */
	static Set<String> ponechane(final File slozka) throws IOException {
		final LinkedHashMap<String, String> klice = new LinkedHashMap<>();
		nacti(slozka.toPath().resolve(SOUBOR), klice);
		return klice.keySet();
	}

	/** Otisk sestavy souborů, pro kterou se cache klíče naposledy uklízela; null, když klíč v souboru není. */
	static String sestava(final File slozka, final String klic) throws IOException {
		final LinkedHashMap<String, String> klice = new LinkedHashMap<>();
		nacti(slozka.toPath().resolve(SOUBOR), klice);
		final String hodnota = klice.get(klic);
		return hodnota == null ? null : hodnota.substring(hodnota.indexOf(' ') + 1);
	}

	private static void nacti(final Path soubor, final LinkedHashMap<String, String> klice) throws IOException {
		if (Files.isRegularFile(soubor)) {
			for (final String radek : Files.readAllLines(soubor, StandardCharsets.UTF_8)) {
				final String[] casti = radek.split(" ");
				if (klice.size() < PONECHAT && casti.length == 3 && KLIC.matcher(casti[0]).matches()) {
					klice.putIfAbsent(casti[0], casti[1] + ' ' + casti[2]);
				}
			}
		}
	}
}
