package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Generace souborů offline dlaždic v cache. Offline dlaždice mají číslo aktuální generace ({@code tiles-offline-N.sqlite}, číslo je v {@code generace.txt}).
 * Při startu před otevřením cache se aktuální generace nad limitem stane předchozí a smaže se ta před ní. Číslo generace se zapíše dřív, než se cokoli smaže, a
 * soubory starších generací, které se nepodařilo smazat, se smažou při dalším startu. Online dlaždice a původní {@code tiles.sqlite} se nikdy nemažou.
 */
@Slf4j
final class RotaceGeneraci {

	static final String STAV = "generace.txt";

	/** Limit velikosti souboru jedné generace offline dlaždic; po smazání se vykreslí znovu. */
	static final long LIMIT_OFFLINE = 300L << 20;

	/** Soubor změněný v posledních minutách může používat druhá instance programu; nemaže se. */
	static final long NEDAVNO_MS = 10 * 60_000L;

	private static final Pattern GENERACE = Pattern.compile("tiles-offline-(\\d+)\\.sqlite");

	/** Soubor generace včetně poškozených cache odložených stranou, viz {@link KachleDBManager}. */
	private static final Pattern SOUBOR_GENERACE = Pattern.compile("tiles-offline-(\\d+)\\.sqlite(\\.vadna(\\.1)?)?");

	/** Smazání souboru; v testu nahraditelné selháním. */
	@FunctionalInterface
	interface Mazani {
		void smaz(File soubor) throws IOException;
	}

	private RotaceGeneraci() {}

	static String jmeno(final int generace) {
		return "tiles-offline-" + generace + ".sqlite";
	}

	/**
	 * Provede rotaci při startu a vrátí číslo aktuální generace; mazání starých souborů (velké soubory na Windows s antivirem trvají) běží v {@code vykonavatel}.
	 * {@code mazani} smaže soubor a vrátí se bez chyby, i když už není.
	 */
	static int rotuj(final File slozka, final long limit, final long nyni, final Executor vykonavatel, final Mazani mazani) {
		final int ulozene = nacti(new File(slozka, STAV));
		final int nejvyssi = nejvyssi(slozka);
		final int dosavadni = verPouzeBlizkemuStavu(nejvyssi, ulozene);
		final int nove = jePrilisVelka(new File(slozka, jmeno(dosavadni)), limit) ? dosavadni + 1 : dosavadni;
		if (nove != ulozene) {
			// Nejdřív zapsat nové číslo, teprve potom mazat; po pádu mezi tím zůstanou jen soubory navíc.
			if (!zapis(new File(slozka, STAV), nove)) {
				return dosavadni;
			}
		}
		if (nove != dosavadni) {
			log.info("Cache dlaždic: rotace generací offline dlaždic {} -> {}", dosavadni, nove);
		}
		smazStarsi(slozka, nove, nyni, vykonavatel, mazani);
		return nove;
	}

	/**
	 * Číslo generace ze stavu platí, jen když je o nejvýš jedna nad nejvyšším existujícím souborem (po rotaci nový soubor ještě nemusí být); vyšší číslo, třeba
	 * z poškozeného stavu, by jinak smazalo existující generace.
	 */
	private static int verPouzeBlizkemuStavu(final int nejvyssiSoubor, final int ulozene) {
		return ulozene == nejvyssiSoubor + 1 ? ulozene : nejvyssiSoubor;
	}

	private static boolean jePrilisVelka(final File soubor, final long limit) {
		return soubor.isFile() && soubor.length() > limit;
	}

	/** Nejvyšší číslo generace mezi existujícími soubory, nejméně 1. */
	private static int nejvyssi(final File slozka) {
		int nejvyssi = 1;
		final String[] jmena = slozka.list();
		if (jmena != null) {
			for (final String jmeno : jmena) {
				final Matcher m = GENERACE.matcher(jmeno);
				if (m.matches()) {
					nejvyssi = Math.max(nejvyssi, parse(m.group(1)));
				}
			}
		}
		return nejvyssi;
	}

	private static int parse(final String cislo) {
		try {
			return Integer.parseInt(cislo);
		} catch (final NumberFormatException e) {
			return 1;
		}
	}

	private static int nacti(final File soubor) {
		try {
			if (soubor.isFile()) {
				final List<String> radky = Files.readAllLines(soubor.toPath(), StandardCharsets.UTF_8);
				for (final String radek : radky) {
					if (radek.startsWith("offline=")) {
						return Math.max(1, parse(radek.substring(8).trim()));
					}
				}
			}
		} catch (final IOException | RuntimeException e) {
			log.warn("Soubor generací cache dlaždic nejde přečíst, číslo se vezme ze souborů: {}", e.toString());
		}
		return 1;
	}

	private static boolean zapis(final File soubor, final int offline) {
		final File docasny = new File(soubor.getPath() + ".new");
		try {
			Files.write(docasny.toPath(), Collections.singletonList("offline=" + offline), StandardCharsets.UTF_8);
			Files.move(docasny.toPath(), soubor.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return true;
		} catch (final IOException e) {
			log.warn("Generace cache dlaždic nejdou zapsat, rotace se odloží: {}", e.toString());
			return false;
		}
	}

	/** Smaže generace starší než předchozí (číslo menší než aktuální − 1). */
	private static void smazStarsi(final File slozka, final int aktualni, final long nyni, final Executor vykonavatel, final Mazani mazani) {
		final String[] jmena = slozka.list();
		if (jmena == null) {
			return;
		}
		for (final String jmeno : jmena) {
			final Matcher m = SOUBOR_GENERACE.matcher(jmeno);
			if (m.matches() && parse(m.group(1)) < aktualni - 1) {
				final File soubor = new File(slozka, jmeno);
				vykonavatel.execute(() -> smaz(soubor, nyni, mazani));
			}
		}
	}

	/** Smaže soubor a až potom jeho žurnál; nedávno změněný soubor ani soubor, který nejde smazat, žurnál neztratí. */
	private static void smaz(final File soubor, final long nyni, final Mazani mazani) {
		if (soubor.exists() && nyni - soubor.lastModified() < NEDAVNO_MS) {
			log.info("Soubor cache dlaždic {} se změnil nedávno, smaže se až při dalším startu.", soubor);
			return;
		}
		try {
			mazani.smaz(soubor);
		} catch (final IOException e) {
			log.warn("Starý soubor cache dlaždic {} nejde smazat, zkusí se při dalším startu: {}", soubor, e.toString());
			return;
		}
		try {
			mazani.smaz(new File(soubor.getPath() + "-journal"));
		} catch (final IOException e) {
			log.warn("Žurnál {} nejde smazat: {}", soubor, e.toString());
		}
	}
}
