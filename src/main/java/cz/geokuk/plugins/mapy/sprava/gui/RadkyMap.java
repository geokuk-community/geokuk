package cz.geokuk.plugins.mapy.sprava.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.KatalogVrstev;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
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

	/** Navíc stavy vrstev (trasy, vrstevnice, stínování) ke každé mapě pro sloupce popupu; řádek je ve složce i s jedinou staženou vrstvou. */
	static List<RadekMapy> sestav(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final List<RadekMapy> vlastni, final Wgs stred,
			final KatalogVrstev vrstvy, final Function<KatalogovaVrstva, StavVrstvy> stavyVrstev) {
		final List<RadekMapy> radky = new ArrayList<>();
		final Map<String, MatriceMap.Radek> matice = new HashMap<>();
		for (final MatriceMap.Radek r : MatriceMap.sestav(katalog, stavy, vrstvy, stavyVrstev)) {
			matice.put(r.getKlic(), r);
		}
		for (final KatalogovaMapa m : katalog.getMapy()) {
			final boolean vCeleZemi = katalog.celek(m).map(c -> {
				final StavMapy sc = stavy.apply(c);
				return sc.getMistni() == StavMapy.Mistni.MAME && !bezi(sc);
			}).orElse(false);
			radky.add(radek(m, stavy.apply(m), stred != null && m.pokryva(stred.lat, stred.lon), matice.get(m.getId()), vCeleZemi));
		}
		for (final RadekMapy r : vlastni) {
			if (r.isVlastni()) {
				radky.add(r);
			}
		}
		return radky;
	}

	static RadekMapy radek(final KatalogovaMapa m, final StavMapy s, final boolean proMisto) {
		return radek(m, s, proMisto, null);
	}

	static RadekMapy radek(final KatalogovaMapa m, final StavMapy s, final boolean proMisto, final MatriceMap.Radek matice) {
		return radek(m, s, proMisto, matice, false);
	}

	static RadekMapy radek(final KatalogovaMapa m, final StavMapy s, final boolean proMisto, final MatriceMap.Radek matice, final boolean vCeleZemi) {
		final boolean mame = s.getMistni() != StavMapy.Mistni.NEMAME;
		final RadekMapy.Builder b = RadekMapy.katalogova(m.getId(), m.getNazev(), stav(s)).skupina(m.getSkupina()).zdroj(m.getZdroj())
				.licence(m.getLicence() + ", " + m.getAtribuce()).proZobrazeneMisto(proMisto).vCeleZemi(vCeleZemi).veSlozce(mame || bezi(s) || s.isRozpracovano() || matice != null && matice.maCokoli()).matice(matice)
				.rozpracovano(s.isRozpracovano()).datumDat(s.getDatumDatMistni()).datumNaServeru(s.getDatumNaServeru()).zkontrolovano(s.getZkontrolovano()).chyba(s.getChyba());
		b.kontroluje(s.getFaze() == StavMapy.Faze.KONTROLUJE);
		if (bezi(s)) {
			b.overuje(s.getFaze() == StavMapy.Faze.OVERUJE).stazeno(s.getStazeno())
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

	/** Stahuje se nebo ověřuje; kontrola aktualizací stav mapy nemění, jen popis. */
	private static boolean bezi(final StavMapy s) {
		return s.isBezi() && s.getFaze() != StavMapy.Faze.KONTROLUJE;
	}

	/** Jedna ikona: běží-li něco, modrá; poškozená nebo nestažená s chybou červená; novější žlutá; stažená zelená. */
	static RadekMapy.Stav stav(final StavMapy s) {
		if (bezi(s)) {
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
