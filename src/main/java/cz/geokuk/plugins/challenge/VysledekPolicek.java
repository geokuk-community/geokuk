package cz.geokuk.plugins.challenge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Neměnný výsledek challenge s políčky (D/T matice, kalendář): chybějící políčka a kandidáti. */
public final class VysledekPolicek {

	private final int celkem;
	private final List<String> stitky;
	private final List<KandidatPolicka> kandidati;
	private final Object vstup;
	private final int limitKandidatu;
	private final int cil;
	/** Počty nálezů po políčkách matice v pořadí {@link VypocetMatice#stitek(int, int)}; null, když výsledek počty nenese. */
	private final int[] nalezu;
	private final int splnenoNalezu;
	/** Polohy hledatelných keší chybějících políček pro počet ve výřezu; null, když je výsledek nenese. */
	private final PolohyHledatelnych polohy;

	VysledekPolicek(final int celkem, final List<String> chybejici, final List<KandidatPolicka> kandidati) {
		this(celkem, chybejici, kandidati, 0);
	}

	/** {@code limitKandidatu}: nejvíc kandidátů na políčko, které výpočet ponechal; 0 = neznámý. */
	VysledekPolicek(final int celkem, final List<String> chybejici, final List<KandidatPolicka> kandidati, final int limitKandidatu) {
		this(celkem, chybejici, kandidati, null, limitKandidatu, 1, null, null);
	}

	/**
	 * Výsledek matice, kterou je potřeba naplnit {@code cil}-krát.
	 *
	 * @param nalezu
	 *            počet nálezů každého ze 81 políček v pořadí {@link VypocetMatice#stitek(int, int)}; políčko s počtem pod {@code cil} je chybějící
	 */
	VysledekPolicek(final int celkem, final List<String> chybejici, final List<KandidatPolicka> kandidati, final int limitKandidatu, final int cil, final int[] nalezu) {
		this(celkem, chybejici, kandidati, null, limitKandidatu, cil, nalezu, null);
	}

	private VysledekPolicek(final int celkem, final List<String> chybejici, final List<KandidatPolicka> kandidati, final Object vstup, final int limitKandidatu, final int cil,
			final int[] nalezu, final PolohyHledatelnych polohy) {
		if (cil < 1) {
			throw new IllegalArgumentException("Cíl musí být alespoň 1: " + cil);
		}
		this.cil = cil;
		this.polohy = polohy;
		this.nalezu = nalezu == null ? null : nalezu.clone();
		int splneno = 0;
		if (nalezu == null) {
			splneno = (celkem - chybejici.size()) * cil;
		} else {
			for (final int n : nalezu) {
				splneno += Math.min(n, cil);
			}
		}
		this.splnenoNalezu = splneno;
		this.limitKandidatu = limitKandidatu;
		this.celkem = celkem;
		this.stitky = Collections.unmodifiableList(new ArrayList<>(chybejici));
		this.kandidati = Collections.unmodifiableList(new ArrayList<>(kandidati));
		this.vstup = vstup == null ? this : vstup;
	}

	/**
	 * Tentýž výsledek označený vstupem, ze kterého vznikl. Výsledky téhož vstupu (třeba přepočet podle jiného středu mapy) mají stejný klíč a {@link DalsiPolicko} u nich
	 * pokračuje v kole; výsledek bez označení je vlastním vstupem.
	 */
	VysledekPolicek sVstupem(final Object klic) {
		return new VysledekPolicek(celkem, stitky, kandidati, Objects.requireNonNull(klic, "klic"), limitKandidatu, cil, nalezu, polohy);
	}

	VysledekPolicek sPolohami(final PolohyHledatelnych nove) {
		return new VysledekPolicek(celkem, stitky, kandidati, vstup == this ? null : vstup, limitKandidatu, cil, nalezu, nove);
	}

	/**
	 * Počet hledatelných keší každého chybějícího políčka v obdélníku (třeba výřezu mapy); štítek → počet. Prázdná mapa, když výsledek polohy nenese. Počítá se jedním průchodem polohami, ne
	 * daty.
	 */
	Map<String, Integer> pocetVOkne(final double minLat, final double maxLat, final double minLon, final double maxLon) {
		final Map<String, Integer> m = new HashMap<>();
		if (polohy == null) {
			return m;
		}
		final int[] n = polohy.vOkne(minLat, maxLat, minLon, maxLon);
		for (final String s : stitky) {
			final int i = VypocetMatice.indexStitku(s);
			if (i >= 0) {
				m.put(s, n[i]);
			}
		}
		return m;
	}

	boolean maPolohy() {
		return polohy != null;
	}

	Object vstup() {
		return vstup;
	}

	private volatile Map<String, KandidatPolicka> index;

	/** Štítek kandidáta pro chybějící políčko; null, když keš není mezi kandidáty. Index kódů se staví při prvním dotazu, potom jde o čtení z mapy bez alokace. */
	public StitekKese stitek(final String idKese) {
		Map<String, KandidatPolicka> m = index;
		if (m == null) {
			synchronized (this) {
				m = index;
				if (m == null) {
					m = new HashMap<>(Math.max(16, kandidati.size() * 4 / 3 + 1));
					for (final KandidatPolicka k : kandidati) {
						m.putIfAbsent(k.id(), k);
					}
					index = m;
				}
			}
		}
		return m.get(idKese);
	}

	/** Kolikrát se má každé políčko naplnit (u matice D/T nastavená násobnost, jinak 1). */
	public int cil() {
		return cil;
	}

	/**
	 * Kolik nalezených keší leží v políčku matice (skutečný počet, i nad {@link #cil()}); 0 pro neznámý štítek, -1 když výsledek počty nenese (kalendář).
	 */
	public int pocetNalezu(final String stitek) {
		if (nalezu == null) {
			return -1;
		}
		final int i = VypocetMatice.indexStitku(stitek);
		return i < 0 ? 0 : nalezu[i];
	}

	/** Součet {@code min(počet, cíl)} přes políčka: kolik z {@link #celkemNalezu()} je splněno. */
	public int splnenoNalezu() {
		return splnenoNalezu;
	}

	/** {@code celkem × cíl}: kolik nálezů je potřeba. */
	public int celkemNalezu() {
		return celkem * cil;
	}

	/** Kolik nálezů ještě chybí do {@link #celkemNalezu()}. */
	public int chybiNalezu() {
		return celkemNalezu() - splnenoNalezu;
	}

	/** Kolik políček je už úplně splněno (počet nálezů aspoň {@link #cil()}). */
	public int splnenoPolicek() {
		return celkem - stitky.size();
	}

	/** Kolik políček je potřeba zaplnit. */
	public int celkem() {
		return celkem;
	}

	public int pocetChybejicich() {
		return stitky.size();
	}

	/** Štítky chybějících políček v pořadí podle challenge (matice: D, pak T; kalendář: nejbližší den první). */
	public List<String> stitkyChybejicich() {
		return stitky;
	}

	private volatile Map<String, Integer> pocty;

	/** Kolik kandidátů výsledek nese pro políčko; 0 pro políčko bez kandidátů nebo neznámý štítek. Nejvýš limit na políčko, takže plné políčko znamená „aspoň tolik“. */
	public int pocetKandidatu(final String stitek) {
		final Integer n = pocetKandidatuPoPolickach().get(stitek);
		return n == null ? 0 : n;
	}

	/** Vzdálenost nejbližšího kandidáta políčka od středu výpočtu v metrech; NaN, když políčko kandidáty nemá nebo nenesou vzdálenost. */
	public double nejblizsiM(final String stitek) {
		double nejmene = Double.NaN;
		for (final KandidatPolicka k : kandidati) {
			final double d = k.vzdalenostM();
			if (!Double.isNaN(d) && k.stitek().equals(stitek) && (Double.isNaN(nejmene) || d < nejmene)) {
				nejmene = d;
			}
		}
		return nejmene;
	}

	/** Políčko má tolik kandidátů, kolik výpočet nejvýš ponechává; skutečný počet nenalezených keší může být větší („50+“). Při neznámém limitu vždy false. */
	public boolean jePlne(final String stitek) {
		return limitKandidatu > 0 && pocetKandidatu(stitek) >= limitKandidatu;
	}

	/**
	 * Vzdálenost od středu výpočtu, do které je výčet kandidátů úplný: nejmenší vzdálenost nejvzdálenějšího kandidáta přes plná políčka ({@link #jePlne}). Když žádné políčko plné není
	 * (nebo kandidáti nenesou vzdálenost), je výčet úplný všude a vrací {@link Double#POSITIVE_INFINITY}.
	 */
	public double dosahM() {
		final Map<String, Double> nejdal = new HashMap<>();
		for (final KandidatPolicka k : kandidati) {
			final double d = k.vzdalenostM();
			if (!Double.isNaN(d) && jePlne(k.stitek())) {
				nejdal.merge(k.stitek(), d, Math::max);
			}
		}
		double dosah = Double.POSITIVE_INFINITY;
		for (final double d : nejdal.values()) {
			dosah = Math.min(dosah, d);
		}
		return dosah;
	}

	/**
	 * Dosah ({@link #dosahM()}), pokud některý z rohů výřezu leží od středu výpočtu dál; jinak {@link Double#NaN}. Tedy hodnota jen tehdy, když omezení kandidátů na políčko
	 * skutečně platí a výřez za ním pokračuje.
	 */
	public double dosahPresazenyM(final cz.geokuk.core.coordinates.Wgs stred, final cz.geokuk.core.coordinates.Wgs... rohy) {
		final double dosah = dosahM();
		if (Double.isInfinite(dosah) || stred == null) {
			return Double.NaN;
		}
		for (final cz.geokuk.core.coordinates.Wgs roh : rohy) {
			if (cz.geokuk.core.coordinates.Wgs.vzdalenost(stred, roh) > dosah) {
				return dosah;
			}
		}
		return Double.NaN;
	}

	/** Počty kandidátů podle štítků chybějících políček v pořadí {@link #stitkyChybejicich()}; neměnná. */
	public Map<String, Integer> pocetKandidatuPoPolickach() {
		Map<String, Integer> m = pocty;
		if (m == null) {
			final Map<String, Integer> nove = new LinkedHashMap<>();
			for (final String s : stitky) {
				nove.put(s, 0);
			}
			for (final KandidatPolicka k : kandidati) {
				nove.merge(k.stitek(), 1, Integer::sum);
			}
			m = Collections.unmodifiableMap(nove);
			pocty = m;
		}
		return m;
	}

	/** Všichni kandidáti pro chybějící políčka. */
	public List<KandidatPolicka> kandidati() {
		return kandidati;
	}
}
