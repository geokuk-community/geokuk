package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Generace souborů cache dlaždic. Online a offline dlaždice mají každé své číslo aktuální generace ({@code tiles-online-N.sqlite}, {@code tiles-offline-N.sqlite}).
 * Při startu před otevřením cache se aktuální generace nad limitem stane předchozí a smaže se ta před ní. Číslo generace se zapíše dřív, než se cokoli smaže, a
 * soubory starších generací, které se nepodařilo smazat, se smažou při dalším startu. Starý společný {@code tiles.sqlite} se smaže, až obě role aspoň jednou
 * rotovaly, tedy až je starší než všechny živé generace.
 */
@Slf4j
final class RotaceGeneraci {

	static final String STAV = "generace.txt";
	static final String STARY_SOUBOR = KachleDBManager.FILE_NAME;

	/** Online dlaždice se nerotují, takže se nemažou. */
	static final long LIMIT_ONLINE = Long.MAX_VALUE;

	/** Limit velikosti souboru jedné generace offline dlaždic; vykreslí se znovu. */
	static final long LIMIT_OFFLINE = 300L << 20;

	/** Soubor změněný v posledních minutách může používat druhá instance programu; nemaže se. */
	static final long NEDAVNO_MS = 10 * 60_000L;

	private static final Pattern GENERACE = Pattern.compile("tiles-(online|offline)-(\\d+)\\.sqlite");

	/** Čísla aktuálních generací. */
	static final class Stav {
		final int online;
		final int offline;

		Stav(final int online, final int offline) {
			this.online = online;
			this.offline = offline;
		}

		int cislo(final boolean jeOffline) {
			return jeOffline ? offline : online;
		}
	}

	/** Smazání souboru; v testu nahraditelné selháním. */
	@FunctionalInterface
	interface Mazani {
		void smaz(File soubor) throws IOException;
	}

	private RotaceGeneraci() {}

	static String jmeno(final boolean offline, final int generace) {
		return "tiles-" + (offline ? "offline" : "online") + "-" + generace + ".sqlite";
	}

	/** Provede rotaci při startu a vrátí čísla aktuálních generací. */
	static Stav rotuj(final File slozka, final long limitOnline, final long limitOffline) {
		return rotuj(slozka, limitOnline, limitOffline, System.currentTimeMillis(), f -> Files.deleteIfExists(f.toPath()));
	}

	/** Viz {@link #rotuj(File, long, long)}; {@code mazani} smaže soubor a vrátí se bez chyby, i když už není. */
	static Stav rotuj(final File slozka, final long limitOnline, final long limitOffline, final long nyni, final Mazani mazani) {
		final Stav ulozeny = nacti(new File(slozka, STAV));
		final int online = verPouzeBlizkemuStavu(nejvyssi(slozka, false), ulozeny.online);
		final int offline = verPouzeBlizkemuStavu(nejvyssi(slozka, true), ulozeny.offline);
		final int onlineNove = jePrilisVelka(new File(slozka, jmeno(false, online)), limitOnline) ? online + 1 : online;
		final int offlineNove = jePrilisVelka(new File(slozka, jmeno(true, offline)), limitOffline) ? offline + 1 : offline;
		if (onlineNove != ulozeny.online || offlineNove != ulozeny.offline) {
			// Nejdřív zapsat nové číslo, teprve potom mazat; po pádu mezi tím zůstanou jen soubory navíc.
			if (!zapis(new File(slozka, STAV), onlineNove, offlineNove)) {
				return new Stav(online, offline);
			}
		}
		if (onlineNove != online || offlineNove != offline) {
			log.info("Cache dlaždic: rotace generací, online {} -> {}, offline {} -> {}", online, onlineNove, offline, offlineNove);
		}
		smazStarsi(slozka, false, onlineNove, nyni, mazani);
		smazStarsi(slozka, true, offlineNove, nyni, mazani);
		if (onlineNove >= 2 && offlineNove >= 2) {
			smaz(new File(slozka, STARY_SOUBOR), nyni, mazani);
		}
		return new Stav(onlineNove, offlineNove);
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
	private static int nejvyssi(final File slozka, final boolean offline) {
		int nejvyssi = 1;
		final String[] jmena = slozka.list();
		if (jmena != null) {
			for (final String jmeno : jmena) {
				final Matcher m = GENERACE.matcher(jmeno);
				if (m.matches() && m.group(1).equals(offline ? "offline" : "online")) {
					nejvyssi = Math.max(nejvyssi, parse(m.group(2)));
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

	private static Stav nacti(final File soubor) {
		int online = 1;
		int offline = 1;
		try {
			if (soubor.isFile()) {
				final List<String> radky = Files.readAllLines(soubor.toPath(), StandardCharsets.UTF_8);
				for (final String radek : radky) {
					if (radek.startsWith("online=")) {
						online = Math.max(1, parse(radek.substring(7).trim()));
					} else if (radek.startsWith("offline=")) {
						offline = Math.max(1, parse(radek.substring(8).trim()));
					}
				}
			}
		} catch (final IOException | RuntimeException e) {
			log.warn("Soubor generací cache dlaždic nejde přečíst, čísla se vezmou ze souborů: {}", e.toString());
		}
		return new Stav(online, offline);
	}

	private static boolean zapis(final File soubor, final int online, final int offline) {
		final File docasny = new File(soubor.getPath() + ".new");
		try {
			Files.write(docasny.toPath(), Arrays.asList("online=" + online, "offline=" + offline), StandardCharsets.UTF_8);
			Files.move(docasny.toPath(), soubor.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return true;
		} catch (final IOException e) {
			log.warn("Generace cache dlaždic nejdou zapsat, rotace se odloží: {}", e.toString());
			return false;
		}
	}

	/** Smaže generace starší než předchozí (číslo menší než aktuální − 1). */
	private static void smazStarsi(final File slozka, final boolean offline, final int aktualni, final long nyni, final Mazani mazani) {
		final String[] jmena = slozka.list();
		if (jmena == null) {
			return;
		}
		for (final String jmeno : jmena) {
			final Matcher m = GENERACE.matcher(jmeno);
			if (m.matches() && m.group(1).equals(offline ? "offline" : "online") && parse(m.group(2)) < aktualni - 1) {
				smaz(new File(slozka, jmeno), nyni, mazani);
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
