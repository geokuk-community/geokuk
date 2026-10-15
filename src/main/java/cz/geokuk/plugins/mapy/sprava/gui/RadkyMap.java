package cz.geokuk.plugins.mapy.sprava.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
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
		final List<RadekMapy> radky = new ArrayList<>();
		for (final KatalogovaMapa m : katalog.getMapy()) {
			radky.add(radek(m, stavy.apply(m), stred != null && m.getPokryti().obsahuje(stred)));
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
