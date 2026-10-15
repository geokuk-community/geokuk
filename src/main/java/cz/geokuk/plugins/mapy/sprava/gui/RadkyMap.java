package cz.geokuk.plugins.mapy.sprava.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.KatalogVrstev;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva;
import cz.geokuk.plugins.mapy.sprava.StavVrstvy;
import cz.geokuk.plugins.mapy.sprava.StavMapy;

/** Řádky popupu Mapy ze stavu katalogových map a ze souborů uživatele. */
final class RadkyMap {

	static final String POSKOZENA = "Poškozená mapa";

	private RadkyMap() {}

	/**
	 * @param vlastni
	 *            soubory ve složce, které nejsou v katalogu
	 * @param stred
	 *            střed zobrazené mapy, null když není znám
	 */
	static List<RadekMapy> sestav(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final List<RadekMapy> vlastni, final Wgs stred) {
		return sestav(katalog, stavy, vlastni, stred, null, null);
	}

	/** Navíc řádky vrstev (trasy, vrstevnice, stínování) k mapám, které jsou stažené; každá hned za svou mapou. */
	static List<RadekMapy> sestav(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final List<RadekMapy> vlastni, final Wgs stred,
			final KatalogVrstev vrstvy, final Function<KatalogovaVrstva, StavVrstvy> stavyVrstev) {
		final List<RadekMapy> radky = new ArrayList<>();
		for (final KatalogovaMapa m : katalog.getMapy()) {
			final StavMapy s = stavy.apply(m);
			radky.add(radek(m, s, stred != null && m.getPokryti().obsahuje(stred)));
			if (vrstvy != null && s.getMistni() == StavMapy.Mistni.MAME) {
				for (final KatalogovaVrstva v : vrstvy.vrstvyProMapu(m)) {
					radky.add(radekVrstvy(m, v, stavyVrstev.apply(v)));
				}
			}
		}
		for (final RadekMapy r : vlastni) {
			if (r.isVlastni()) {
				radky.add(r);
			}
		}
		return radky;
	}

	static RadekMapy radek(final KatalogovaMapa m, final StavMapy s, final boolean proMisto) {
		final boolean mame = s.getMistni() != StavMapy.Mistni.NEMAME;
		final RadekMapy.Builder b = RadekMapy.katalogova(m.getId(), m.getNazev(), stav(s)).skupina(m.getSkupina()).zdroj(m.getZdroj())
				.licence(m.getLicence() + ", " + m.getAtribuce()).proZobrazeneMisto(proMisto).veSlozce(mame).rozpracovano(s.isRozpracovano())
				.datumDat(s.getDatumDatMistni()).datumNaServeru(s.getDatumNaServeru()).zkontrolovano(s.getZkontrolovano()).chyba(s.getChyba());
		if (s.isBezi()) {
			b.kontroluje(s.getFaze() == StavMapy.Faze.KONTROLUJE).overuje(s.getFaze() == StavMapy.Faze.OVERUJE).stazeno(s.getStazeno())
					.velikost(s.getCelkem() > 0 ? s.getCelkem() : velikostNaServeru(m, s));
		} else if (mame) {
			b.velikost(s.getVelikostMistni());
		} else {
			b.velikost(velikostNaServeru(m, s)).stazeno(s.getStazeno());
		}
		if (s.getMistni() == StavMapy.Mistni.POSKOZENA && s.getChyba() == null) {
			b.chyba(POSKOZENA);
		}
		return b.build();
	}

	/** Řádek vrstvy k mapě: stejné stavy jako u mapy, „jiná verze“ je novější. */
	static RadekMapy radekVrstvy(final KatalogovaMapa mapa, final KatalogovaVrstva v, final StavVrstvy s) {
		final RadekMapy.Stav stav;
		if (s.isBezi()) {
			stav = RadekMapy.Stav.STAHUJE;
		} else if (s.getMistni() == StavVrstvy.Mistni.MAME) {
			stav = RadekMapy.Stav.AKTUALNI;
		} else if (s.getMistni() == StavVrstvy.Mistni.JINA_VERZE) {
			stav = RadekMapy.Stav.NOVEJSI;
		} else {
			stav = s.getFaze() == StavMapy.Faze.CHYBA ? RadekMapy.Stav.CHYBA : RadekMapy.Stav.NEMAME;
		}
		final boolean mame = s.getMistni() != StavVrstvy.Mistni.NEMAME;
		final RadekMapy.Builder b = RadekMapy.vrstva(v.getId(), nazevVrstvy(v), stav).vrstva(nazevVrstvy(v), mapa.getId()).velikost(v.getVelikost()).stazeno(s.getStazeno())
				.rozpracovano(s.isRozpracovano()).veSlozce(mame).zdroj(v.getZdrojDat()).licence(v.getLicence() + (v.getAtribuce() == null ? "" : ", " + v.getAtribuce()))
				.chyba(s.getChyba());
		if (v.getDatumDat() != null) {
			b.datumDat(v.getDatumDat().atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
		}
		return b.build();
	}

	static String nazevVrstvy(final KatalogovaVrstva v) {
		switch (v.getTyp()) {
		case TRASY:
			return "Turistické trasy";
		case VRSTEVNICE:
			return "Vrstevnice";
		case DEM:
		default:
			return "Stínování terénu";
		}
	}

	private static long velikostNaServeru(final KatalogovaMapa m, final StavMapy s) {
		return s.getVelikostNaServeru() > 0 ? s.getVelikostNaServeru() : m.getPribliznaVelikost();
	}

	/** Jedna ikona: běží-li něco, modrá; poškozená nebo nestažená s chybou červená; novější žlutá; stažená zelená. */
	static RadekMapy.Stav stav(final StavMapy s) {
		if (s.isBezi()) {
			return RadekMapy.Stav.STAHUJE;
		}
		switch (s.getMistni()) {
		case POSKOZENA:
			return RadekMapy.Stav.CHYBA;
		case MAME:
			// Nepovedená kontrola nebo aktualizace staženou mapu nezneplatní; chyba je v popisu.
			return s.getServer() == StavMapy.Server.NOVEJSI ? RadekMapy.Stav.NOVEJSI : RadekMapy.Stav.AKTUALNI;
		case NEMAME:
		default:
			return s.getFaze() == StavMapy.Faze.CHYBA ? RadekMapy.Stav.CHYBA : RadekMapy.Stav.NEMAME;
		}
	}
}
