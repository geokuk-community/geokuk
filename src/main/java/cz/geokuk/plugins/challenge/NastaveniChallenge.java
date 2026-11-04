package cz.geokuk.plugins.challenge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Uložení a načtení vlastních definic challenge, aktivních a zapnutí plánovače do {@link ChallengeUloziste}. Klíče: {@code format}, {@code zapnuto}, {@code aktivni}
 * (názvy po řádcích) a {@code definice.N.klíč} pro každou vlastní definici. Šablony se neukládají, jsou v kódu. Chybná nebo neznámá definice se při načtení přeskočí
 * s varováním, aby nastavení nikdy nezabránilo startu programu.
 */
@Slf4j
public final class NastaveniChallenge {

	private static final String FORMAT = "1";
	private static final String PREDPONA = "definice.";

	/** Načtený stav. */
	public static final class Nacteno {
		public final ChallengeSeznam seznam;
		public final boolean zapnuto;
		/** Kolik uložených definic se nepodařilo použít. */
		public final int preskoceno;

		Nacteno(final ChallengeSeznam seznam, final boolean zapnuto, final int preskoceno) {
			this.seznam = seznam;
			this.zapnuto = zapnuto;
			this.preskoceno = preskoceno;
		}
	}

	private NastaveniChallenge() {}

	public static void uloz(final ChallengeSeznam seznam, final boolean zapnuto, final ChallengeUloziste uloziste) {
		for (final String k : new ArrayList<>(uloziste.klice())) {
			if (k.startsWith(PREDPONA) || k.equals("aktivni") || k.equals("zapnuto") || k.equals("format")) {
				uloziste.remove(k);
			}
		}
		uloziste.put("format", FORMAT);
		uloziste.put("zapnuto", Boolean.toString(zapnuto));
		final StringBuilder aktivni = new StringBuilder();
		for (final Challenge c : seznam.aktivni()) {
			if (aktivni.length() > 0) {
				aktivni.append('\n');
			}
			aktivni.append(c.nazev());
		}
		if (aktivni.length() > 0) {
			uloziste.put("aktivni", aktivni.toString());
		}
		int i = 0;
		for (final Challenge c : seznam.vlastni()) {
			for (final Map.Entry<String, String> e : c.naMapu().entrySet()) {
				uloziste.put(PREDPONA + i + "." + e.getKey(), e.getValue());
			}
			i++;
		}
	}

	public static Nacteno nacti(final ChallengeUloziste uloziste) {
		final ChallengeSeznam seznam = new ChallengeSeznam();
		int preskoceno = 0;
		final String format = uloziste.get("format");
		if (format != null && !FORMAT.equals(format)) {
			log.warn("Nastavení challenge má neznámý formát {}, použijí se výchozí hodnoty", format);
			return new Nacteno(seznam, false, 0);
		}
		final Map<Integer, Map<String, String>> definice = new TreeMap<>();
		for (final String k : uloziste.klice()) {
			if (!k.startsWith(PREDPONA)) {
				continue;
			}
			final String zbytek = k.substring(PREDPONA.length());
			final int tecka = zbytek.indexOf('.');
			if (tecka <= 0) {
				preskoceno++;
				continue;
			}
			try {
				final int cislo = Integer.parseInt(zbytek.substring(0, tecka));
				final String hodnota = uloziste.get(k);
				if (hodnota != null) {
					definice.computeIfAbsent(cislo, x -> new TreeMap<>()).put(zbytek.substring(tecka + 1), hodnota);
				}
			} catch (final NumberFormatException e) {
				preskoceno++;
			}
		}
		for (final Map.Entry<Integer, Map<String, String>> e : definice.entrySet()) {
			try {
				seznam.ulozVlastni(Challenge.zMapy(e.getValue()));
			} catch (final IllegalArgumentException | ChallengeChyba ex) {
				log.warn("Uložená definice challenge č. {} se přeskočí: {}", e.getKey(), ex.getMessage());
				preskoceno++;
			}
		}
		final String aktivni = uloziste.get("aktivni");
		final Set<String> videne = new HashSet<>();
		if (aktivni != null) {
			for (final String nazev : aktivni.split("\n")) {
				if (nazev.isEmpty() || !videne.add(nazev)) {
					continue;
				}
				try {
					seznam.aktivuj(nazev);
				} catch (final ChallengeChyba ex) {
					log.warn("Aktivní challenge „{}“ se přeskočí: {}", nazev, ex.getMessage());
					preskoceno++;
				}
			}
		}
		return new Nacteno(seznam, Boolean.parseBoolean(uloziste.get("zapnuto")), preskoceno);
	}
}
