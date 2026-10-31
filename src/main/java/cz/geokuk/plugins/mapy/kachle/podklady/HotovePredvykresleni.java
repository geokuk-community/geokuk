package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/**
 * Klíče offline map, jejichž přehledové zoomy jsou předvykreslené v cache, a u každého vzorek jeho dlaždic (řádek {@code klíč z/x/y z/x/y …}). Soubor leží vedle
 * souboru klíčů cache offline mapy. Samotný záznam nestačí: po restartu se musí vzorek najít v cache, jinak se přehled předvykresluje znovu. Cokoli
 * nečitelné nebo neúplné neplatí.
 */
final class HotovePredvykresleni {

	static final String SOUBOR = "predvykresleno.txt";
	/** Kolik klíčů se pamatuje, stejně jako souborů klíčů cache. */
	static final int ZAPAMATOVAT = UklidOfflineCache.PONECHAT;
	private static final Pattern DLAZDICE = Pattern.compile("(\\d{1,2})/(\\d{1,9})/(\\d{1,9})");

	private HotovePredvykresleni() {}

	/** Zapamatovaný vzorek dlaždic klíče; null, když klíč není zapsán nebo je záznam poškozený. */
	static List<KaLoc> vzorek(final File slozka, final String klic) {
		try {
			return nacti(slozka).get(klic);
		} catch (final IOException | RuntimeException e) {
			return null;
		}
	}

	/** Zapíše klíč s vzorkem jako nejnovější. */
	static void zapis(final File slozka, final String klic, final List<KaLoc> vzorek) throws IOException {
		final Map<String, List<KaLoc>> zaznamy = new LinkedHashMap<>();
		zaznamy.put(klic, vzorek);
		Map<String, List<KaLoc>> dosavadni;
		try {
			dosavadni = nacti(slozka);
		} catch (final IOException | RuntimeException e) {
			dosavadni = new LinkedHashMap<>();
		}
		for (final Map.Entry<String, List<KaLoc>> e : dosavadni.entrySet()) {
			if (zaznamy.size() < ZAPAMATOVAT) {
				zaznamy.putIfAbsent(e.getKey(), e.getValue());
			}
		}
		final List<String> radky = new ArrayList<>();
		for (final Map.Entry<String, List<KaLoc>> e : zaznamy.entrySet()) {
			final StringBuilder radek = new StringBuilder(e.getKey());
			for (final KaLoc loc : e.getValue()) {
				radek.append(' ').append(loc.getMoumer()).append('/').append(loc.getFromSzUnsignedX()).append('/').append(loc.getFromSzUnsignedY());
			}
			radky.add(radek.toString());
		}
		Files.createDirectories(slozka.toPath());
		final Path docasny = slozka.toPath().resolve(SOUBOR + ".tmp");
		Files.write(docasny, radky, StandardCharsets.UTF_8);
		Files.move(docasny, slozka.toPath().resolve(SOUBOR), StandardCopyOption.REPLACE_EXISTING);
	}

	/** Neplatný řádek se přeskočí, nečitelný soubor je výjimka. */
	private static Map<String, List<KaLoc>> nacti(final File slozka) throws IOException {
		final Map<String, List<KaLoc>> zaznamy = new LinkedHashMap<>();
		final Path soubor = slozka.toPath().resolve(SOUBOR);
		if (!Files.isRegularFile(soubor)) {
			return zaznamy;
		}
		for (final String radek : Files.readAllLines(soubor, StandardCharsets.UTF_8)) {
			final String[] casti = radek.trim().split(" +");
			if (casti.length < 2) {
				continue;
			}
			final List<KaLoc> vzorek = new ArrayList<>();
			boolean platny = true;
			for (int i = 1; i < casti.length && platny; i++) {
				final Matcher m = DLAZDICE.matcher(casti[i]);
				try {
					if (m.matches()) {
						vzorek.add(KaLoc.ofXyz(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(1))));
					} else {
						platny = false;
					}
				} catch (final NumberFormatException e) {
					platny = false;
				}
			}
			if (platny) {
				zaznamy.putIfAbsent(casti[0], vzorek);
			}
		}
		return zaznamy;
	}
}
