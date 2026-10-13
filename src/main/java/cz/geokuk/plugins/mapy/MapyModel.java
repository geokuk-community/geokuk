package cz.geokuk.plugins.mapy;

import java.io.File;
import java.util.Locale;

import cz.geokuk.core.program.FPref;
import cz.geokuk.core.program.PrehledovaMapa;
import cz.geokuk.framework.Model0;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;

public class MapyModel extends Model0 {

	private EKaType podklad;

	public EKaType getKaSet() {
		return podklad;
	}

	public EKaType getPodklad() {
		return podklad;
	}

	public void setPodklad(final EKaType podklad) {
		if (podklad == this.podklad) {
			return;
		}
		this.podklad = podklad;
		currPrefe().node(FPref.NODE_KTERE_MAPY_node).put(FPref.VALUE_MAPOVE_PODKLADY_value, podklad.name());
		fajruj();
	}

	@Override
	protected void initAndFire() {
		final String ulozeny = currPrefe().node(FPref.NODE_KTERE_MAPY_node).get(FPref.VALUE_MAPOVE_PODKLADY_value, null);
		final EKaType podklad = ulozeny == null ? vychoziPodklad(KachleModel.offlineMapyDir(currPrefe()).getEffectiveFile()) : EKaType.podleJmena(ulozeny);
		setPodklad(podklad == null ? EKaType.TURIST_M : podklad);
	}

	/** Podklad nové instalace: offline mapa, když je ve složce podrobná mapa (ne jen přehledová mapa světa), jinak ortofoto ČÚZK. */
	static EKaType vychoziPodklad(final File slozkaOfflineMap) {
		final File[] mapy = slozkaOfflineMap == null ? null
				: slozkaOfflineMap.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".map") && !f.getName().equalsIgnoreCase(PrehledovaMapa.SOUBOR));
		return mapy != null && mapy.length > 0 ? EKaType.OFFLINE_MF : EKaType.CUZK_ORTO;
	}

	private void fajruj() {
		if (podklad != null) {
			fire(new ZmenaMapNastalaEvent(getKaSet()));
		}
	}

}
