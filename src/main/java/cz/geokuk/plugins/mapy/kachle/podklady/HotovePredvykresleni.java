package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Klíče offline map, jejichž přehledové zoomy už jsou v cache předvykreslené. Soubor leží vedle souboru klíčů cache offline mapy, takže zmizí s cache; klíč se
 * mění s každou změnou sestavy souborů nebo tématu, a starý záznam proto nikdy nevyhoví.
 */
final class HotovePredvykresleni {

	static final String SOUBOR = "predvykresleno.txt";
	/** Kolik klíčů se pamatuje, stejně jako souborů klíčů cache. */
	static final int ZAPAMATOVAT = UklidOfflineCache.PONECHAT;

	private HotovePredvykresleni() {}

	static boolean jeHotovo(final File slozka, final String klic) {
		try {
			return nacti(slozka).contains(klic);
		} catch (final IOException | RuntimeException e) {
			return false;
		}
	}

	/** Zapíše klíč jako nejnovější. */
	static void zapis(final File slozka, final String klic) throws IOException {
		final List<String> klice = new ArrayList<>();
		klice.add(klic);
		for (final String k : nacti(slozka)) {
			if (!klic.equals(k) && klice.size() < ZAPAMATOVAT) {
				klice.add(k);
			}
		}
		Files.createDirectories(slozka.toPath());
		final Path docasny = slozka.toPath().resolve(SOUBOR + ".tmp");
		Files.write(docasny, klice, StandardCharsets.UTF_8);
		Files.move(docasny, slozka.toPath().resolve(SOUBOR), StandardCopyOption.REPLACE_EXISTING);
	}

	private static List<String> nacti(final File slozka) throws IOException {
		final Path soubor = slozka.toPath().resolve(SOUBOR);
		if (!Files.isRegularFile(soubor)) {
			return new ArrayList<>();
		}
		final List<String> klice = new ArrayList<>();
		for (final String radek : Files.readAllLines(soubor, StandardCharsets.UTF_8)) {
			if (!radek.trim().isEmpty()) {
				klice.add(radek.trim());
			}
		}
		return klice;
	}
}
