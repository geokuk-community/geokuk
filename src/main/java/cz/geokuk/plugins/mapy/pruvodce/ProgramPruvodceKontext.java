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
import cz.geokuk.plugins.mapy.sprava.StavVrstvy;

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
		if (volne <= 0) {
			return -1;
		}
		long dostahuje = spravaMap.getBeziciStahovani().filter(m -> m.getCelkem() > 0).map(m -> Math.max(0, m.getCelkem() - m.getStazeno())).orElse(0L);
		for (final StavVrstvy v : spravaMap.getBeziciStahovaniVrstev()) {
			dostahuje += Math.max(0, v.getCelkem() - v.getStazeno());
		}
		return Math.max(0, volne - dostahuje);
	}

	@Override
	public boolean stahni(final Polozka polozka) {
		if (polozka.isVrstva()) {
			return spravaMap.getKatalogVrstev().podleId(polozka.getId()).map(spravaMap::stahni).orElse(false);
		}
		return spravaMap.getKatalog().podleId(polozka.getId()).map(spravaMap::stahni).orElse(false);
	}

	@Override
	public void zrusStahovani() {
		spravaMap.zrusStahovani();
	}

	@Override
	public void prepni(final PruvodceVolba volba) {
		final PodkladPruvodce podklad = volba.getPodklad();
		if (podklad.isOffline()) {
			kachleModel.setTemaOfflineMapy(TemaOfflineMapy.GEOKUK);
			if (volba.getVrstvy().contains(Cast.STINOVANI)) {
				kachleModel.setStinovaniTerenu(true);
			}
		} else if (volba.getVrstvy().contains(Cast.TRASY)) {
			kachleModel.setPrekryvTras(true);
		}
		mapyModel.setPodklad(podklad.getPodklad());
	}
}
