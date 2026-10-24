package cz.geokuk.plugins.mapy.sprava.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
import cz.geokuk.plugins.mapy.sprava.NabidkaRadku;
import cz.geokuk.plugins.mapy.sprava.PolygonPokryti;
import cz.geokuk.plugins.mapy.sprava.StavMapy;

/** Rada v mapě, když pro zobrazené místo není podrobná offline mapa: nabídne stažení mapy z katalogu, která místo pokrývá. */
public final class RadaOfflineMapy {

	public static final String NADPIS = TextyMap.RADA_NADPIS;
	static final String BEZ_KATALOGU = "Další offline mapy najdete v bloku Mapy vpravo dole ve stavovém řádku.";

	/** Text pod nadpisem, null když není. */
	public final String text;
	/** Text odkazu, null když se nic nenabízí. */
	public final String odkaz;
	/** Mapa, kterou odkaz stáhne. */
	public final KatalogovaMapa mapa;
	/** Název mapy v prvním sloupci tabulky rady („Česko“), null když se nic nenabízí. */
	public final String predpona;
	/** Druhý odkaz „Stáhnout mapu a vrstvy (977 MB)“ na stejné mapě, null když se nenabízí. */
	public final String odkaz2;
	/** Pravidlo plánu pro druhý odkaz. */
	public final PlanPruvodce.Rezim rezim2;
	/** Další mapy, které zasahují do zobrazeného výřezu a nepokrývají jeho střed (sousední země); prázdné, když se jiná mapa stahuje. */
	public final List<RadaOfflineMapy> sousedni;
	/** Nabídka celé země, když je mapa jejím regionem a celou zemi jde stáhnout; jinak null. */
	public final RadaOfflineMapy zeme;

	static final int NEJVIC_SOUSEDNICH = 2;
	public static final String SOUSEDNI = TextyMap.RADA_SOUSEDNI;

	/** Zeměpisný obdélník zobrazeného výřezu (ohraničení jeho rohů). */
	public static final class Vyrez {
		final double jih;
		final double zapad;
		final double sever;
		final double vychod;

		public Vyrez(final double jih, final double zapad, final double sever, final double vychod) {
			this.jih = jih;
			this.zapad = zapad;
			this.sever = sever;
			this.vychod = vychod;
		}

		boolean obsahuje(final Wgs w) {
			return w.lat >= jih && w.lat <= sever && w.lon >= zapad && w.lon <= vychod;
		}

		/** Obrys mapy zasahuje do výřezu: některý bod výřezu leží v obrysu, nebo některý bod obrysu ve výřezu. */
		boolean protina(final PolygonPokryti p) {
			if (p.getJih() > sever || p.getSever() < jih || p.getZapad() > vychod || p.getVychod() < zapad) {
				return false;
			}
			final int n = 6;
			for (int i = 0; i <= n; i++) {
				for (int j = 0; j <= n; j++) {
					if (p.obsahuje(jih + (sever - jih) * i / n, zapad + (vychod - zapad) * j / n)) {
						return true;
					}
				}
			}
			for (final List<Wgs> kruh : p.getObrysy()) {
				for (final Wgs w : kruh) {
					if (obsahuje(w)) {
						return true;
					}
				}
			}
			return false;
		}
	}

	private RadaOfflineMapy(final String text, final String odkaz, final KatalogovaMapa mapa) {
		this(text, odkaz, mapa, Collections.emptyList());
	}

	private RadaOfflineMapy(final String text, final String odkaz, final KatalogovaMapa mapa, final List<RadaOfflineMapy> sousedni) {
		this(text, odkaz, mapa, sousedni, null, null, null);
	}

	private RadaOfflineMapy(final String text, final String odkaz, final KatalogovaMapa mapa, final List<RadaOfflineMapy> sousedni, final String predpona, final String odkaz2,
			final PlanPruvodce.Rezim rezim2) {
		this(text, odkaz, mapa, sousedni, predpona, odkaz2, rezim2, null);
	}

	private RadaOfflineMapy(final String text, final String odkaz, final KatalogovaMapa mapa, final List<RadaOfflineMapy> sousedni, final String predpona, final String odkaz2,
			final PlanPruvodce.Rezim rezim2, final RadaOfflineMapy zeme) {
		this.text = text;
		this.odkaz = odkaz;
		this.mapa = mapa;
		this.sousedni = sousedni;
		this.predpona = predpona;
		this.odkaz2 = odkaz2;
		this.rezim2 = rezim2;
		this.zeme = zeme;
	}

	public static RadaOfflineMapy pro(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final Wgs stred) {
		return pro(katalog, stavy, stred, null);
	}

	/** Když se stahuje jiná mapa (stahovana), nabídka počká, stahuje se vždy jedna. */
	public static RadaOfflineMapy pro(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final Wgs stred, final KatalogovaMapa stahovana) {
		return pro(katalog, stavy, stred, stahovana, null);
	}

	/** Navíc nabídne mapy, které zasahují do výřezu a nepokrývají jeho střed, třeba sousední země. */
	public static RadaOfflineMapy pro(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final Wgs stred, final KatalogovaMapa stahovana,
			final Vyrez vyrez) {
		return pro(katalog, stavy, stred, stahovana, vyrez, null);
	}

	/**
	 * Totéž s dvěma tlačítky u map, které jde stáhnout: „Česko: Stáhnout mapu (606 MB) · Stáhnout mapu a vrstvy (977 MB)“ podle nabídky řádku (null = jen jeden odkaz bez vrstev).
	 */
	public static RadaOfflineMapy pro(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final Wgs stred, final KatalogovaMapa stahovana,
			final Vyrez vyrez, final Function<KatalogovaMapa, NabidkaRadku> nabidky) {
		final RadaOfflineMapy hlavni = sZemi(katalog, stavy, hlavni(katalog, stavy, stred, stahovana, nabidky), nabidky);
		if (vyrez == null || stahovana != null) {
			return hlavni;
		}
		final List<RadaOfflineMapy> sousedni = sousedni(katalog, stavy, stred, vyrez, nabidky);
		return sousedni.isEmpty() ? hlavni
				: new RadaOfflineMapy(hlavni.text, hlavni.odkaz, hlavni.mapa, Collections.unmodifiableList(sousedni), hlavni.predpona, hlavni.odkaz2, hlavni.rezim2, hlavni.zeme);
	}

	/** U regionu přidá nabídku celé země, když jde stáhnout a nemáme z ní nic. */
	private static RadaOfflineMapy sZemi(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final RadaOfflineMapy r,
			final Function<KatalogovaMapa, NabidkaRadku> nabidky) {
		if (r.odkaz == null || r.mapa == null) {
			return r;
		}
		final KatalogovaMapa celek = katalog.celek(r.mapa).orElse(null);
		if (celek == null || mameUzemi(katalog, stavy, celek)) {
			return r;
		}
		final RadaOfflineMapy z = pro(celek, stavy.apply(celek), nabidky);
		if (z.odkaz == null) {
			return r;
		}
		return new RadaOfflineMapy(r.text, r.odkaz, r.mapa, r.sousedni, r.predpona, r.odkaz2, r.rezim2, z);
	}

	/**
	 * Mapy do výřezu zasahující, které nepokrývají střed, od nejbližší ke středu. Nenabízí se území, které už máme (region stažené země, ani celá země, když
	 * máme některý její region), a celek spolu se svými regiony.
	 */
	private static List<RadaOfflineMapy> sousedni(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final Wgs stred, final Vyrez vyrez,
			final Function<KatalogovaMapa, NabidkaRadku> nabidky) {
		final List<KatalogovaMapa> kandidati = new ArrayList<>();
		for (final KatalogovaMapa m : katalog.getMapy()) {
			if (!m.getPokryti().obsahuje(stred) && vyrez.protina(m.getPokryti()) && !mameUzemi(katalog, stavy, m)) {
				kandidati.add(m);
			}
		}
		// Celek se nabídne, jen když se nenabízí žádný z jeho regionů.
		kandidati.removeIf(m -> !katalog.regiony(m).isEmpty() && katalog.regiony(m).stream().anyMatch(kandidati::contains));
		final java.util.Map<KatalogovaMapa, Double> vzdalenost = new java.util.HashMap<>();
		for (final KatalogovaMapa m : kandidati) {
			vzdalenost.put(m, vzdalenost(m.getPokryti(), stred));
		}
		kandidati.sort(java.util.Comparator.comparingDouble(vzdalenost::get));
		final List<RadaOfflineMapy> sousedni = new ArrayList<>();
		for (final KatalogovaMapa m : kandidati) {
			if (sousedni.size() >= NEJVIC_SOUSEDNICH) {
				break;
			}
			final StavMapy s = stavy.apply(m);
			final RadaOfflineMapy r = sZemi(katalog, stavy, pro(m, s, nabidky), nabidky);
			if (r.odkaz == null) {
				continue;
			}
			sousedni.add(r);
		}
		return sousedni;
	}

	/** Mapa je stažená, nebo je stažená její země (region), nebo některý její region (celá země). */
	private static boolean mameUzemi(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final KatalogovaMapa m) {
		if (mame(stavy, m)) {
			return true;
		}
		if (katalog.celek(m).filter(c -> mame(stavy, c)).isPresent()) {
			return true;
		}
		return katalog.regiony(m).stream().anyMatch(r -> mame(stavy, r));
	}

	private static boolean mame(final Function<KatalogovaMapa, StavMapy> stavy, final KatalogovaMapa m) {
		final StavMapy s = stavy.apply(m);
		return s.getMistni() == StavMapy.Mistni.MAME && !s.isBezi();
	}

	/** Přibližná vzdálenost středu od obrysu (nejbližší bod obrysu), ve stupních s ohledem na zeměpisnou šířku. */
	static double vzdalenost(final PolygonPokryti p, final Wgs stred) {
		final double kos = Math.cos(Math.toRadians(stred.lat));
		double nejmene = Double.MAX_VALUE;
		for (final List<Wgs> kruh : p.getObrysy()) {
			for (final Wgs w : kruh) {
				final double dy = w.lat - stred.lat;
				final double dx = (w.lon - stred.lon) * kos;
				nejmene = Math.min(nejmene, dx * dx + dy * dy);
			}
		}
		return nejmene;
	}

	private static RadaOfflineMapy hlavni(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final Wgs stred, final KatalogovaMapa stahovana,
			final Function<KatalogovaMapa, NabidkaRadku> nabidky) {
		for (final KatalogovaMapa m : katalog.pokryvajiBod(stred)) {
			final StavMapy s = stavy.apply(m);
			if (s.getMistni() == StavMapy.Mistni.MAME && !s.isBezi() || katalog.celek(m).filter(c -> mame(stavy, c)).isPresent()) {
				continue;
			}
			final RadaOfflineMapy r = pro(m, s, nabidky);
			if (r.odkaz != null && stahovana != null && !stahovana.getId().equals(m.getId())) {
				return new RadaOfflineMapy("Stahuje se mapa " + stahovana.getNazev() + ". Mapu " + m.getNazev() + " stáhnete, až skončí.", null, m);
			}
			return r;
		}
		return new RadaOfflineMapy(BEZ_KATALOGU, null, null);
	}

	static RadaOfflineMapy pro(final KatalogovaMapa m, final StavMapy s) {
		return pro(m, s, null);
	}

	static RadaOfflineMapy pro(final KatalogovaMapa m, final StavMapy s, final Function<KatalogovaMapa, NabidkaRadku> nabidky) {
		final String nazev = m.getNazev();
		switch (s.getFaze()) {
		case STAHUJE:
			return new RadaOfflineMapy(s.getCelkem() > 0 ? "Stahuje se mapa " + nazev + ": " + Math.min(100, s.getStazeno() * 100 / s.getCelkem()) + " %."
					: "Stahuje se mapa " + nazev + ".", null, m);
		case OVERUJE:
			return new RadaOfflineMapy("Mapa " + nazev + " je stažená, ověřuji ji.", null, m);
		case CHYBA:
			return jedenOdkaz("Zkusit znovu", "Stažení mapy " + nazev + " se nepovedlo.", m);
		default:
			final NabidkaRadku n = nabidky == null ? null : nabidky.apply(m);
			if (n != null && n.getMapa().getDruh() == NabidkaRadku.Druh.TLACITKO && n.getVrstvy().getDruh() == NabidkaRadku.Druh.TLACITKO) {
				return new RadaOfflineMapy(null, n.getMapa().getText(), m, Collections.emptyList(), nazev, n.getVrstvy().getText(), n.getVrstvy().getRezim());
			}
			if (s.isRozpracovano()) {
				return jedenOdkaz("Dokončit stažení", null, m);
			}
			final long velikost = s.getVelikostNaServeru() > 0 ? s.getVelikostNaServeru() : m.getPribliznaVelikost();
			return jedenOdkaz("Stáhnout mapu" + (velikost > 0 ? " (~" + RadekMapy.velikost(velikost) + ")" : ""), null, m);
		}
	}

	private static RadaOfflineMapy jedenOdkaz(final String odkaz, final String text, final KatalogovaMapa m) {
		return new RadaOfflineMapy(text, odkaz, m, Collections.emptyList(), m.getNazev(), null, null);
	}

	/** Řádek tabulky rady: mapa s odkazy, hlavička země s regiony pod ní (rada null), nebo štítek skupiny (stitek). */
	public static final class RadekRady {
		public final String nazev;
		/** Region pod zemí: název se odsadí. */
		public final boolean region;
		/** Odkazy řádku, null u hlavičky země bez vlastní mapy a u štítku. */
		public final RadaOfflineMapy rada;
		public final boolean stitek;

		RadekRady(final String nazev, final boolean region, final RadaOfflineMapy rada, final boolean stitek) {
			this.nazev = nazev;
			this.region = region;
			this.rada = rada;
			this.stitek = stitek;
		}
	}

	/** Tabulka rady: hlavní mapa a pod štítkem „V záběru je i:“ sousední; každá země jednou s celou zemí a regiony pod ní. */
	public List<RadekRady> tabulka() {
		final java.util.Map<String, List<RadaOfflineMapy>> hlavniZeme = podleZeme(Collections.singletonList(this));
		final java.util.Map<String, List<RadaOfflineMapy>> dalsiZeme = podleZeme(sousedni);
		for (final java.util.Iterator<java.util.Map.Entry<String, List<RadaOfflineMapy>>> it = dalsiZeme.entrySet().iterator(); it.hasNext();) {
			final java.util.Map.Entry<String, List<RadaOfflineMapy>> e = it.next();
			if (hlavniZeme.containsKey(e.getKey())) {
				hlavniZeme.get(e.getKey()).addAll(e.getValue());
				it.remove();
			}
		}
		final List<RadekRady> radky = new ArrayList<>(radky(hlavniZeme));
		final List<RadekRady> dalsi = radky(dalsiZeme);
		if (!dalsi.isEmpty()) {
			radky.add(new RadekRady(SOUSEDNI, false, null, true));
			radky.addAll(dalsi);
		}
		return radky;
	}

	/** Nabídky po zemích; celá země (je-li) před svými regiony, každá mapa jednou. */
	private static java.util.Map<String, List<RadaOfflineMapy>> podleZeme(final List<RadaOfflineMapy> rady) {
		final java.util.Map<String, List<RadaOfflineMapy>> podleZeme = new java.util.LinkedHashMap<>();
		for (final RadaOfflineMapy r : rady) {
			if (r.odkaz != null && r.mapa != null) {
				final List<RadaOfflineMapy> skupina = podleZeme.computeIfAbsent(r.mapa.getSkupina() != null ? r.mapa.getSkupina() : r.mapa.getNazev(), k -> new ArrayList<>());
				if (r.zeme != null) {
					skupina.add(r.zeme);
				}
				skupina.add(r);
			}
		}
		return podleZeme;
	}

	private static List<RadekRady> radky(final java.util.Map<String, List<RadaOfflineMapy>> podleZeme) {
		final List<RadekRady> radky = new ArrayList<>();
		for (final java.util.Map.Entry<String, List<RadaOfflineMapy>> e : podleZeme.entrySet()) {
			final java.util.Set<String> videno = new java.util.HashSet<>();
			final List<RadaOfflineMapy> rady = new ArrayList<>();
			for (final RadaOfflineMapy r : e.getValue()) {
				if (videno.add(r.mapa.getId())) {
					rady.add(r);
				}
			}
			final RadaOfflineMapy celek = rady.stream().filter(r -> r.mapa.getSkupina() == null).findFirst().orElse(null);
			radky.add(new RadekRady(e.getKey(), false, celek, false));
			for (final RadaOfflineMapy r : rady) {
				if (r != celek) {
					radky.add(new RadekRady(r.mapa.getNazev(), true, r, false));
				}
			}
		}
		return radky;
	}
}
