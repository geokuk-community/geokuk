package cz.geokuk.plugins.mapy.sprava;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

/** Stažené vrstvy ve složce vrstev; leží v podsložkách podle cesty základní mapy na serveru (europe/germany/bayern.tourist.map). */
@Slf4j
public final class SouboryVrstev {

	private static final int HLOUBKA = 8;

	private SouboryVrstev() {
	}

	/** Vrstvy ve formátu .map (trasy, vrstevnice) ve složce i v podsložkách, seřazené podle cesty bez ohledu na velikost písmen. */
	public static List<File> mapy(final File slozkaVrstev) {
		return soubory(slozkaVrstev, ".map");
	}

	/** Výškové modely pro stínování (.zip s dlaždicemi .hgt i samotné .hgt) ve složce i v podsložkách, seřazené podle cesty. */
	public static List<File> dem(final File slozkaVrstev) {
		return soubory(slozkaVrstev, ".zip", ".hgt");
	}

	private static List<File> soubory(final File slozka, final String... pripony) {
		if (slozka == null || !slozka.isDirectory()) {
			return Collections.emptyList();
		}
		try (Stream<Path> s = Files.walk(slozka.toPath(), HLOUBKA)) {
			return s.filter(Files::isRegularFile).filter(p -> maPriponu(p.getFileName().toString(), pripony))
					.sorted(Comparator.comparing(p -> p.toString().toLowerCase(Locale.ROOT))).map(Path::toFile).collect(Collectors.toList());
		} catch (final IOException | RuntimeException e) {
			log.info("Složku vrstev {} nejde projít: {}", slozka, e.toString());
			return Collections.emptyList();
		}
	}

	private static boolean maPriponu(final String jmeno, final String... pripony) {
		final String male = jmeno.toLowerCase(Locale.ROOT);
		for (final String p : pripony) {
			if (male.endsWith(p) && !male.startsWith("._")) {
				return true;
			}
		}
		return false;
	}
}
