package cz.geokuk.plugins.mapy.pruvodce;

import java.io.File;
import java.util.List;

import cz.geokuk.plugins.mapy.MapyModel;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.podklady.TemaOfflineMapy;
import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce.Polozka;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;

/** Průvodce napojený na modely programu: Správa map (katalogy, stavy, stahování), podklad a nastavení kachlí. */
public final class ProgramPruvodceKontext implements PruvodceKontext {

	private final SpravaMapModel spravaMap;
	private final MapyModel mapyModel;
	private final KachleModel kachleModel;

	public ProgramPruvodceKontext(final SpravaMapModel spravaMap, final MapyModel mapyModel, final KachleModel kachleModel) {
		this.spravaMap = spravaMap;
		this.mapyModel = mapyModel;
		this.kachleModel = kachleModel;
	}

	@Override
	public KatalogMap getKatalog() {
		return spravaMap.getKatalog();
	}

	@Override
	public List<MatriceMap.Radek> matice() {
		return MatriceMap.sestav(spravaMap.getKatalog(), m -> spravaMap.getStav(m), spravaMap.getKatalogVrstev(), v -> spravaMap.getStav(v));
	}

	@Override
	public long volneMisto() {
		final File slozka = spravaMap.getSlozkaVrstev();
		File f = slozka == null ? null : slozka.getParentFile();
		while (f != null && !f.exists()) {
			f = f.getParentFile();
		}
		final long volne = f == null ? 0 : f.getUsableSpace();
		return volne <= 0 ? -1 : volne;
	}

	@Override
	public void stahni(final Polozka polozka) {
		if (polozka.isVrstva()) {
			spravaMap.getKatalogVrstev().podleId(polozka.getId()).ifPresent(spravaMap::stahni);
		} else {
			spravaMap.getKatalog().podleId(polozka.getId()).ifPresent(spravaMap::stahni);
		}
	}

	@Override
	public void zrusStahovani() {
		spravaMap.zrusStahovani();
	}

	@Override
	public void prepni(final PruvodceVolba volba) {
		final PodkladPruvodce podklad = volba.getPodklad();
		if (podklad.isOffline()) {
			kachleModel.setTemaOfflineMapy(TemaOfflineMapy.TURISTICKA);
			if (volba.getVrstvy().contains(Cast.STINOVANI)) {
				kachleModel.setStinovaniTerenu(true);
			}
		} else if (volba.getVrstvy().contains(Cast.TRASY)) {
			kachleModel.setPrekryvTras(true);
		}
		mapyModel.setPodklad(podklad.getPodklad());
	}
}
