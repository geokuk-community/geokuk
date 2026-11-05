package cz.geokuk.plugins.challenge;

import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Výpočet challenge kalendář: nález v každém dni roku, počítá se podle lokálního data nálezu. */
public final class VypocetKalendare {

	private static final MonthDay PRESTUPNY = MonthDay.of(2, 29);

	private VypocetKalendare() {}

	/** Lokální datum okamžiku nálezu v pásmu uživatele. */
	public static LocalDate lokalniDatum(final Instant okamzik, final ZoneId pasmo) {
		return okamzik.atZone(pasmo).toLocalDate();
	}

	/**
	 * @param dnes
	 *            dnešní datum v pásmu uživatele; podle něj se počítá „za kolik dní“ a které eventy ještě nenastaly
	 */
	public static VysledekPolicek spocti(final PravidlaKalendare pravidla, final LocalDate dnes, final Iterable<? extends VstupKalendar> kese) {
		final Set<MonthDay> nalezene = new HashSet<>();
		final List<VstupKalendar> hledatelne = new ArrayList<>();
		for (final VstupKalendar k : kese) {
			if (!pravidla.typy().lzePocitat(k)) {
				continue;
			}
			if (PravidlaTypu.platnyNalez(k)) {
				if (k.datumNalezu() != null) {
					nalezene.add(MonthDay.from(k.datumNalezu()));
				}
			} else if (PravidlaTypu.hledatelna(k)) {
				hledatelne.add(k);
			}
		}
		final List<MonthDay> dny = chybejiciDny(pravidla, dnes, nalezene);
		final List<String> stitky = new ArrayList<>();
		for (final MonthDay d : dny) {
			stitky.add(stitek(d, dniDoDalsiho(d, dnes)));
		}
		final List<KandidatPolicka> kandidati = new ArrayList<>();
		for (final VstupKalendar k : hledatelne) {
			final LocalDate datum = k.datum();
			if (datum == null || !dny.contains(MonthDay.from(datum))) {
				continue;
			}
			final MonthDay den = MonthDay.from(datum);
			final Integer dni;
			if (k.event()) {
				if (datum.isBefore(dnes)) {
					continue;
				}
				dni = (int) ChronoUnit.DAYS.between(dnes, datum);
			} else if (pravidla.kandidati() == PravidlaKalendare.Kandidati.EVENTY_A_VYROCI) {
				dni = dniDoDalsiho(den, dnes);
			} else {
				continue;
			}
			kandidati.add(new KandidatPolicka(k.id(), k.lat(), k.lon(), stitek(den, dniDoDalsiho(den, dnes)), dni));
		}
		return new VysledekPolicek(pravidla.pozadovatPrestupny() ? 366 : 365, stitky, kandidati);
	}

	private static List<MonthDay> chybejiciDny(final PravidlaKalendare pravidla, final LocalDate dnes, final Set<MonthDay> nalezene) {
		final List<MonthDay> dny = new ArrayList<>();
		for (LocalDate d = LocalDate.of(2025, 1, 1); d.getYear() == 2025; d = d.plusDays(1)) {
			final MonthDay md = MonthDay.from(d);
			if (!nalezene.contains(md)) {
				dny.add(md);
			}
		}
		if (pravidla.pozadovatPrestupny() && !nalezene.contains(PRESTUPNY)) {
			dny.add(PRESTUPNY);
		}
		Collections.sort(dny, Comparator.comparingInt((final MonthDay d) -> dniDoDalsiho(d, dnes)));
		return dny;
	}

	/** Za kolik dní nastane den poprvé (0 = dnes); 29. 2. až v nejbližším přestupném roce. */
	static int dniDoDalsiho(final MonthDay den, final LocalDate dnes) {
		for (int rok = dnes.getYear();; rok++) {
			if (den.isValidYear(rok)) {
				final LocalDate datum = den.atYear(rok);
				if (!datum.isBefore(dnes)) {
					return (int) ChronoUnit.DAYS.between(dnes, datum);
				}
			}
		}
	}

	/** Štítek dne, například „14. 3. (za 131 dní)“. */
	static String stitek(final MonthDay den, final int dni) {
		return den.getDayOfMonth() + ". " + den.getMonthValue() + ". (" + za(dni) + ")";
	}

	private static String za(final int dni) {
		if (dni == 0) {
			return "dnes";
		}
		return "za " + dni + (dni == 1 ? " den" : dni < 5 ? " dny" : " dní");
	}
}
