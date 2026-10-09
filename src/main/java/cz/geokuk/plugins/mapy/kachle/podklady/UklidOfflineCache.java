package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Cache offline mapy drží dlaždice a symboly jen posledních několika klíčů (map, tématu a měřítka), aby po výměně mapy nebo tématu nerostla. Klíče si pamatuje
 * v souboru ve složce symbolů, nejnovější první.
 */
final class UklidOfflineCache {

	static final int PONECHAT = 3;
	static final String SOUBOR = "klice.txt";
	/** Typ dlaždice offline mapy v cache, viz {@link OfflineRenderer#klic(String)}. */
	static final Pattern KLIC = Pattern.compile("o[0-9a-f]{8}");
	private static final Pattern SYMBOLY = Pattern.compile("symboly-[0-9a-f]{8}\\.bin");

	private UklidOfflineCache() {}

	/**
	 * Zapíše klíč jako nejnovější a smaže soubory symbolů, které žádný z posledních klíčů nepoužívá.
	 *
	 * @param symboly
	 *            soubor symbolů tématu, ze kterého jsou dlaždice klíče
	 * @return klíče, jejichž dlaždice v cache zůstávají
	 */
	static Set<String> zaznamenej(final File slozka, final String klic, final File symboly) throws IOException {
		final Path soubor = slozka.toPath().resolve(SOUBOR);
		final LinkedHashMap<String, String> klice = new LinkedHashMap<>();
		klice.put(klic, symboly.getName());
		if (Files.isRegularFile(soubor)) {
			for (final String radek : Files.readAllLines(soubor, StandardCharsets.UTF_8)) {
				final String[] casti = radek.split(" ");
				if (klice.size() < PONECHAT && casti.length == 2 && KLIC.matcher(casti[0]).matches()) {
					klice.putIfAbsent(casti[0], casti[1]);
				}
			}
		}
		final List<String> radky = new ArrayList<>();
		for (final Map.Entry<String, String> e : klice.entrySet()) {
			radky.add(e.getKey() + ' ' + e.getValue());
		}
		Files.createDirectories(slozka.toPath());
		final Path docasny = slozka.toPath().resolve(SOUBOR + ".tmp");
		Files.write(docasny, radky, StandardCharsets.UTF_8);
		Files.move(docasny, soubor, StandardCopyOption.REPLACE_EXISTING);

		final File[] soubory = slozka.listFiles(f -> SYMBOLY.matcher(f.getName()).matches() && !klice.containsValue(f.getName()));
		if (soubory != null) {
			for (final File f : soubory) {
				Files.deleteIfExists(f.toPath());
			}
		}
		return klice.keySet();
	}
}
