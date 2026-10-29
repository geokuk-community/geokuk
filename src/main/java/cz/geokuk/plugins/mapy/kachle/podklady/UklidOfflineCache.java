package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Cache offline mapy drží dlaždice a symboly jen posledních několika globálních klíčů (téma, měřítko, písmo), aby po výměně tématu nerostla; novější verze téže
 * varianty tématu nahrazuje starší. Klíče si pamatuje v souboru ve složce symbolů, nejnovější první, s otiskem sestavy souborů, pro kterou se cache naposledy
 * uklízela, a s identitou varianty.
 */
@Slf4j
final class UklidOfflineCache {

	static final int PONECHAT = 3;
	static final String SOUBOR = "klice.txt";
	/** Globální klíč offline mapy (o) a překryvu (t), viz {@link OfflineRenderer#klic(String)}. */
	static final Pattern KLIC = Pattern.compile("[ot][0-9a-f]{8}");
	/** Typ dlaždice v cache: globální klíč a oblastní část, viz {@link OblastniKlic#klic}. */
	static final Pattern TYP = Pattern.compile("([ot][0-9a-f]{8})\\.[0-9a-f]{8}");
	private static final Pattern SYMBOLY = Pattern.compile("symboly-[0-9a-f]{8}\\.bin");

	private UklidOfflineCache() {}

	/** Složky se souborem klíčů podkladu a překryvu ve složce cache (viz {@code KachleModel}). */
	static final String SLOZKA_PODKLADU = "offline-temata";
	static final String SLOZKA_PREKRYVU = "offline-trasy";

	/** Klíče, jejichž dlaždice v cache zůstávají podle souborů klíčů podkladu a překryvu ve složce cache. */
	static Set<String> ponechaneVCache(final File cache) throws IOException {
		final Set<String> klice = new LinkedHashSet<>(ponechane(new File(cache, SLOZKA_PODKLADU)));
		klice.addAll(ponechane(new File(cache, SLOZKA_PREKRYVU)));
		return klice;
	}

	/**
	 * Dlaždice offline klíče, který v souborech klíčů nezůstal. Online dlaždice a dlaždice podkladu nebo překryvu, o jehož klíčích soubor nic neví, nejsou zastaralé;
	 * bez ponechaných klíčů není nic zastaralé.
	 */
	static boolean jeZastarala(final String typ, final Set<String> ponechat) {
		if (typ == null || typ.isEmpty()) {
			return false;
		}
		final char predpona = typ.charAt(0);
		if (ponechat.stream().noneMatch(k -> k.charAt(0) == predpona)) {
			return false;
		}
		final Matcher m = TYP.matcher(typ);
		if (m.matches()) {
			return !ponechat.contains(m.group(1));
		}
		return KLIC.matcher(typ).matches() && !ponechat.contains(typ);
	}

	/** Identita varianty dlaždic ve čtvrtém sloupci souboru klíčů, viz {@link OfflineRenderer#identita(OfflineRenderer.NacteneTema, boolean)}. */
	private static final Pattern IDENTITA = Pattern.compile("[0-9a-f]{8}");

	/** Řádek souboru klíčů: klíč, soubor symbolů, otisk sestavy a od nové verze identita varianty (null u starších záznamů). */
	private static final class Zaznam {
		final String klic;
		final String symboly;
		final String sestava;
		final String identita;

		Zaznam(final String klic, final String symboly, final String sestava, final String identita) {
			this.klic = klic;
			this.symboly = symboly;
			this.sestava = sestava;
			this.identita = identita;
		}
	}

	/** Bez identity varianty; viz {@link #zaznamenej(File, String, File, String, String)}. */
	static Set<String> zaznamenej(final File slozka, final String klic, final File symboly, final String sestava) throws IOException {
		return zaznamenej(slozka, klic, symboly, sestava, null);
	}

	/**
	 * Zapíše klíč jako nejnovější a smaže soubory symbolů, které žádný z ponechaných klíčů nepoužívá. Starší klíče téže varianty ({@code identita}) vypadnou hned, protože
	 * je z žádné konfigurace už nikdo nevytvoří; ze záznamů bez identity (starší verze programu) zůstane jen nejnovější podkladu a nejnovější překryvu. Víc než {@link #PONECHAT} klíčů nezůstane.
	 *
	 * @param symboly
	 *            soubor symbolů tématu, ze kterého jsou dlaždice klíče
	 * @param sestava
	 *            otisk souborů, pro které se cache uklízí, viz {@link OblastniKlic#getSestava()}
	 * @param identita
	 *            varianta dlaždic bez obsahu souborů tématu, null = neznámá (nic se podle ní nemaže)
	 * @return klíče, jejichž dlaždice v cache zůstávají
	 */
	static Set<String> zaznamenej(final File slozka, final String klic, final File symboly, final String sestava, final String identita) throws IOException {
		return naplanuj(slozka, klic, symboly, sestava, identita).uloz();
	}

	/** Nový stav souboru klíčů, který se zapíše až {@link #uloz()}, aby se při neúspěšném smazání dlaždic úklid příště zopakoval. */
	static final class Plan {
		private final File slozka;
		private final List<Zaznam> zaznamy;
		private final Set<String> zastarale;

		private Plan(final File slozka, final List<Zaznam> zaznamy, final Set<String> zastarale) {
			this.slozka = slozka;
			this.zaznamy = zaznamy;
			this.zastarale = zastarale;
		}

		/** Klíče, které dosud soubor klíčů vedl a po zápisu v něm nebudou: jejich dlaždice v cache jsou zastaralé. */
		Set<String> zastarale() {
			return zastarale;
		}

		/** Klíče, jejichž dlaždice v cache zůstanou. */
		Set<String> klice() {
			final Set<String> klice = new LinkedHashSet<>();
			for (final Zaznam z : zaznamy) {
				klice.add(z.klic);
			}
			return klice;
		}

		/** Zapíše soubor klíčů a smaže soubory symbolů, které žádný z ponechaných klíčů nepoužívá. */
		Set<String> uloz() throws IOException {
			final List<String> radky = new ArrayList<>();
			for (final Zaznam z : zaznamy) {
				radky.add(z.klic + ' ' + z.symboly + ' ' + z.sestava + (z.identita == null ? "" : ' ' + z.identita));
			}
			Files.createDirectories(slozka.toPath());
			final Path docasny = slozka.toPath().resolve(SOUBOR + ".tmp");
			Files.write(docasny, radky, StandardCharsets.UTF_8);
			Files.move(docasny, slozka.toPath().resolve(SOUBOR), StandardCopyOption.REPLACE_EXISTING);

			final File[] soubory = slozka.listFiles(f -> SYMBOLY.matcher(f.getName()).matches() && zaznamy.stream().noneMatch(z -> z.symboly.equals(f.getName())));
			if (soubory != null) {
				for (final File f : soubory) {
					try {
						Files.deleteIfExists(f.toPath());
					} catch (final IOException e) {
						// Zamčený soubor (Windows) nesmí zabránit úklidu dlaždic.
						log.debug("Soubor symbolů {} nejde smazat: {}", f, e.toString());
					}
				}
			}
			return klice();
		}
	}

	/** Spočítá, jaké klíče zůstanou po zápisu klíče {@code klic}; nic nezapisuje ani nemaže. Parametry viz {@link #zaznamenej(File, String, File, String, String)}. */
	static Plan naplanuj(final File slozka, final String klic, final File symboly, final String sestava, final String identita) throws IOException {
		final List<Zaznam> zaznamy = new ArrayList<>();
		zaznamy.add(new Zaznam(klic, symboly.getName(), sestava, identita));
		final List<Zaznam> dosavadni = nacti(slozka.toPath().resolve(SOUBOR));
		final List<String> vypadle = new ArrayList<>();
		final Set<Character> ponechanBezIdentity = new HashSet<>();
		for (final Zaznam z : dosavadni) {
			if (z.klic.equals(klic)) {
				continue;
			}
			if (identita != null && identita.equals(z.identita)) {
				vypadle.add(z.klic);
				continue;
			}
			// Podklad (o) a překryv (t) se uklízejí každý zvlášť, proto zůstane nejnovější záznam bez identity pro každý z nich.
			if (identita != null && z.identita == null && !ponechanBezIdentity.add(z.klic.charAt(0))) {
				vypadle.add(z.klic);
				continue;
			}
			zaznamy.add(z);
		}
		while (zaznamy.size() > PONECHAT) {
			zaznamy.remove(zaznamy.size() - 1);
		}
		if (!vypadle.isEmpty()) {
			log.info("Cache offline mapy: klíče {} se nahrazují novějším {}", vypadle, klic);
		}
		final Set<String> zastarale = new LinkedHashSet<>();
		for (final Zaznam z : dosavadni) {
			if (zaznamy.stream().noneMatch(n -> n.klic.equals(z.klic))) {
				zastarale.add(z.klic);
			}
		}
		return new Plan(slozka, zaznamy, zastarale);
	}

	/** Klíče, jejichž dlaždice v cache zůstávají podle posledního zápisu. */
	static Set<String> ponechane(final File slozka) throws IOException {
		final Set<String> klice = new LinkedHashSet<>();
		for (final Zaznam z : nacti(slozka.toPath().resolve(SOUBOR))) {
			if (klice.size() < PONECHAT) {
				klice.add(z.klic);
			}
		}
		return klice;
	}

	/** Otisk sestavy souborů, pro kterou se cache klíče naposledy uklízela; null, když klíč v souboru není. */
	static String sestava(final File slozka, final String klic) throws IOException {
		for (final Zaznam z : nacti(slozka.toPath().resolve(SOUBOR))) {
			if (z.klic.equals(klic)) {
				return z.sestava;
			}
		}
		return null;
	}

	/** Záznamy souboru klíčů, nejnovější první; řádky ve starším tvaru (bez otisku sestavy) nebo poškozené se přeskočí. */
	private static List<Zaznam> nacti(final Path soubor) throws IOException {
		final List<Zaznam> zaznamy = new ArrayList<>();
		if (Files.isRegularFile(soubor)) {
			for (final String radek : Files.readAllLines(soubor, StandardCharsets.UTF_8)) {
				final String[] casti = radek.split(" ");
				if ((casti.length == 3 || casti.length == 4 && IDENTITA.matcher(casti[3]).matches()) && KLIC.matcher(casti[0]).matches()) {
					zaznamy.add(new Zaznam(casti[0], casti[1], casti[2], casti.length == 4 ? casti[3] : null));
				}
			}
		}
		return zaznamy;
	}
}
