package cz.geokuk.plugins.mapy.sprava.gui;

import java.util.function.Function;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
import cz.geokuk.plugins.mapy.sprava.StavMapy;

/** Rada v mapě, když pro zobrazené místo není podrobná offline mapa: nabídne stažení mapy z katalogu, která místo pokrývá. */
public final class RadaOfflineMapy {

	public static final String NADPIS = "Pro toto místo nemáte podrobnou offline mapu.";
	static final String BEZ_KATALOGU = "Další offline mapy najdete v bloku Mapy vpravo dole ve stavovém řádku.";

	/** Text pod nadpisem, null když není. */
	public final String text;
	/** Text odkazu, null když se nic nenabízí. */
	public final String odkaz;
	/** Mapa, kterou odkaz stáhne. */
	public final KatalogovaMapa mapa;

	private RadaOfflineMapy(final String text, final String odkaz, final KatalogovaMapa mapa) {
		this.text = text;
		this.odkaz = odkaz;
		this.mapa = mapa;
	}

	public static RadaOfflineMapy pro(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavy, final Wgs stred) {
		for (final KatalogovaMapa m : katalog.pokryvajiBod(stred)) {
			final StavMapy s = stavy.apply(m);
			if (s.getMistni() == StavMapy.Mistni.MAME && !s.isBezi()) {
				continue;
			}
			return pro(m, s);
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
