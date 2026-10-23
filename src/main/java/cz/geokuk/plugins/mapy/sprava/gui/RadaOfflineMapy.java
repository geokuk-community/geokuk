package cz.geokuk.plugins.mapy.sprava.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
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
	/** Další mapy, které zasahují do zobrazeného výřezu a nepokrývají jeho střed (sousední země); prázdné, když se jiná mapa stahuje. */
	public final List<RadaOfflineMapy> sousedni;

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
		this.text = text;
		this.odkaz = odkaz;
		this.mapa = mapa;
		this.sousedni = sousedni;
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
		final RadaOfflineMapy hlavni = hlavni(katalog, stavy, stred, stahovana);
		if (vyrez == null || stahovana != null) {
			return hlavni;
		}
		final List<RadaOfflineMapy> sousedni = sousedni(katalog, stavy, stred, vyrez);
		return sousedni.isEmpty() ? hlavni : new RadaOfflineMapy(hlavni.text, hlavni.odkaz, hlavni.mapa, Collections.unmodifiableList(sousedni));
	}

	/**
	 * Mapy do výřezu zasahující, které nepokrývají střed, od nejbližší ke středu. Nenabízí se území, které už máme (region stažené země, ani celá země, když
	 * máme některý její region), a celek spolu se svými regiony.
	 */
	private static List<RadaOfflineMapy> sousedni(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final Wgs stred, final Vyrez vyrez) {
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
			final RadaOfflineMapy r = pro(m, s);
			if (r.odkaz == null) {
				continue;
			}
			// Odkaz „Zkusit znovu“ říká, kterou mapu.
			sousedni.add(s.getFaze() == StavMapy.Faze.CHYBA ? new RadaOfflineMapy(r.text, "Zkusit znovu stáhnout mapu " + m.getNazev(), m) : r);
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

	private static RadaOfflineMapy hlavni(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final Wgs stred, final KatalogovaMapa stahovana) {
		for (final KatalogovaMapa m : katalog.pokryvajiBod(stred)) {
			final StavMapy s = stavy.apply(m);
			if (s.getMistni() == StavMapy.Mistni.MAME && !s.isBezi()) {
				continue;
			}
			final RadaOfflineMapy r = pro(m, s);
			if (r.odkaz != null && stahovana != null && !stahovana.getId().equals(m.getId())) {
				return new RadaOfflineMapy("Stahuje se mapa " + stahovana.getNazev() + ". Mapu " + m.getNazev() + " stáhnete, až skončí.", null, m);
			}
			return r;
		}
		return new RadaOfflineMapy(BEZ_KATALOGU, null, null);
	}

	static RadaOfflineMapy pro(final KatalogovaMapa m, final StavMapy s) {
		final String nazev = m.getNazev();
		switch (s.getFaze()) {
		case STAHUJE:
			return new RadaOfflineMapy(s.getCelkem() > 0 ? "Stahuje se mapa " + nazev + ": " + Math.min(100, s.getStazeno() * 100 / s.getCelkem()) + " %."
					: "Stahuje se mapa " + nazev + ".", null, m);
		case OVERUJE:
			return new RadaOfflineMapy("Mapa " + nazev + " je stažená, ověřuji ji.", null, m);
		case CHYBA:
			return new RadaOfflineMapy("Stažení mapy " + nazev + " se nepovedlo.", "Zkusit znovu", m);
		default:
			if (s.isRozpracovano()) {
				return new RadaOfflineMapy(null, "Dokončit stažení mapy " + nazev, m);
			}
			final long velikost = s.getVelikostNaServeru() > 0 ? s.getVelikostNaServeru() : m.getPribliznaVelikost();
			return new RadaOfflineMapy(null, "Stáhnout mapu " + nazev + (velikost > 0 ? " (~" + RadekMapy.velikost(velikost) + ")" : ""), m);
		}
	}
}
