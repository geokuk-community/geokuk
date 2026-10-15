package cz.geokuk.plugins.mapy;

import java.io.File;
import java.util.Locale;

import cz.geokuk.core.program.FPref;
import cz.geokuk.core.program.PrehledovaMapa;
import cz.geokuk.framework.Model0;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.sprava.SpravaMapEvent;
import cz.geokuk.plugins.mapy.sprava.StavMapy;

public class MapyModel extends Model0 {

	private EKaType podklad;

	public EKaType getKaSet() {
		return podklad;
	}

	public EKaType getPodklad() {
		return podklad;
	}

	/** Klíč nastavení: nová instalace bez podrobné mapy nabízí stažení a po něm se přepne na offline mapu. */
	private static final String NABIDKA_PRVNIHO_STAZENI = "nabidkaPrvnihoStazeni";

	/** Nová instalace začíná ortofotem a nabízí stažení první mapy; po stažení se přepne na offline mapu. */
	public boolean isNabidkaPrvnihoStazeni() {
		return podklad == EKaType.CUZK_ORTO && currPrefe().node(FPref.NODE_KTERE_MAPY_node).getBoolean(NABIDKA_PRVNIHO_STAZENI, false);
	}

	/** Uživatel nabídku odmítl; příště se nenabídne. */
	public void zrusNabidkuPrvnihoStazeni() {
		currPrefe().node(FPref.NODE_KTERE_MAPY_node).putBoolean(NABIDKA_PRVNIHO_STAZENI, false);
		fajruj();
	}

	/** Stažená mapa v nové instalaci: přepne se na offline mapu (s výchozím tématem Turistická), pokud si uživatel nezvolil jiný podklad. */
	public void onEvent(final SpravaMapEvent event) {
		if (event.getStav().getFaze() == StavMapy.Faze.HOTOVO && isNabidkaPrvnihoStazeni()) {
			setPodklad(EKaType.OFFLINE_MF);
		}
	}

	public void setPodklad(final EKaType podklad) {
		if (podklad == this.podklad) {
			return;
		}
		if (podklad != EKaType.CUZK_ORTO) {
			currPrefe().node(FPref.NODE_KTERE_MAPY_node).putBoolean(NABIDKA_PRVNIHO_STAZENI, false);
		}
		this.podklad = podklad;
		currPrefe().node(FPref.NODE_KTERE_MAPY_node).put(FPref.VALUE_MAPOVE_PODKLADY_value, podklad.name());
		fajruj();
	}

	@Override
	protected void initAndFire() {
		final String ulozeny = currPrefe().node(FPref.NODE_KTERE_MAPY_node).get(FPref.VALUE_MAPOVE_PODKLADY_value, null);
		nastavPodklad(ulozeny, KachleModel.offlineMapyDir(currPrefe()).getEffectiveFile());
	}

	/** Uložený podklad, nebo u nové instalace výchozí podle map ve složce; bez podrobné mapy se nabídne stažení první. */
	void nastavPodklad(final String ulozeny, final File slozkaOfflineMap) {
		final EKaType podklad = ulozeny == null ? vychoziPodklad(slozkaOfflineMap) : EKaType.podleJmena(ulozeny);
		if (ulozeny == null && podklad == EKaType.CUZK_ORTO) {
			currPrefe().node(FPref.NODE_KTERE_MAPY_node).putBoolean(NABIDKA_PRVNIHO_STAZENI, true);
		}
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
