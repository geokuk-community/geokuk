package cz.geokuk.plugins.mapy.sprava;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

/** Stažené vrstvy ve složce vrstev; leží v podsložkách podle cesty základní mapy na serveru (europe/germany/bayern.tourist.map). */
@Slf4j
public final class SouboryVrstev {

	private static final int HLOUBKA = 8;

	private SouboryVrstev() {
	}

	/**
	 * Vrstvy ve formátu .map (trasy, vrstevnice) ve složce i v podsložkách, seřazené podle cesty bez ohledu na velikost písmen. Starší verze vrstvy
	 * ({@link KatalogovaVrstva.Typ#getStarsiKod()}) se vynechá, když je vedle ní současná.
	 */
	public static List<File> mapy(final File slozkaVrstev) {
		final List<File> mapy = soubory(slozkaVrstev, ".map");
		final List<File> vysledek = new ArrayList<>(mapy.size());
		for (final File f : mapy) {
			if (!maSoucasnouVerzi(f, mapy)) {
				vysledek.add(f);
			}
		}
		return vysledek;
	}

	/**
	 * Soubory starší verze vrstvy (například europe/czech-republic.routes.map u vrstvy tourist) ve složce vrstev, i s kontrolním součtem a rozpracovaným
	 * stahováním.
	 */
	public static List<File> starsiSoubory(final File slozkaVrstev, final KatalogovaVrstva vrstva) {
		final String starsi = vrstva.getTyp().getStarsiKod();
		if (slozkaVrstev == null || starsi == null) {
			return Collections.emptyList();
		}
		final File cesta = new File(slozkaVrstev, vrstva.getRegion());
		final Pattern vzor = Pattern.compile(vzor(Pattern.quote(cesta.getName()), starsi).pattern() + "(" + Pattern.quote(StahovacVrstev.PRIPONA_SOUCTU) + "|"
				+ Pattern.quote(StahovacMapy.PRIPONA) + "|" + Pattern.quote(StahovacMapy.PRIPONA_INFO) + "|" + Pattern.quote(StahovacVrstev.PRIPONA_STARA) + ")?",
				Pattern.CASE_INSENSITIVE);
		final File[] soubory = cesta.getParentFile().listFiles(f -> f.isFile() && vzor.matcher(f.getName()).matches());
		if (soubory == null) {
			return Collections.emptyList();
		}
		final List<File> vysledek = new ArrayList<>(Arrays.asList(soubory));
		vysledek.sort(Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
		return vysledek;
	}

	private static boolean maSoucasnouVerzi(final File f, final List<File> mapy) {
		for (final KatalogovaVrstva.Typ t : KatalogovaVrstva.Typ.values()) {
			if (t.getStarsiKod() != null) {
				final Matcher m = vzor("(.+)", t.getStarsiKod()).matcher(f.getName());
				if (m.matches()) {
					final Pattern soucasna = vzor(Pattern.quote(m.group(1)), t.getKod());
					return mapy.stream().anyMatch(g -> g.getParentFile().equals(f.getParentFile()) && soucasna.matcher(g.getName()).matches());
				}
			}
		}
		return false;
	}

	/** Jméno souboru vrstvy: region (regulární výraz), kód typu, případně díl a .map. */
	private static Pattern vzor(final String region, final String kod) {
		return Pattern.compile(region + "\\." + Pattern.quote(kod) + "(\\.[a-z0-9_-]+)?\\.map", Pattern.CASE_INSENSITIVE);
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
