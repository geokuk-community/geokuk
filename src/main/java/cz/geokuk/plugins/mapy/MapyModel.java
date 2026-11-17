package cz.geokuk.plugins.mapy;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import cz.geokuk.core.program.FPref;
import cz.geokuk.core.program.PrehledovaMapa;
import cz.geokuk.framework.Model0;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;

public class MapyModel extends Model0 {

	/** Jména map verze 5.2, které mají obdobu mezi vestavěnými mapami; mapy Mapy.cz se jmenují stejně jako vestavěné. */
	private static final Map<String, EKaType> PODKLADY_5_2 = new HashMap<>();
	static {
		PODKLADY_5_2.put("osm", EKaType.OPEN_STREET);
		PODKLADY_5_2.put("cuzksat", EKaType.CUZK_ORTO);
		PODKLADY_5_2.put("cuzktop", EKaType.CUZK_ZTM);
	}

	private EKaType podklad;
	private boolean prvniSpusteni;

	/** Nová instalace: podklad ještě nikdy nebyl uložený v nastavení. */
	public boolean isPrvniSpusteni() {
		return prvniSpusteni;
	}

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
		prvniSpusteni = ulozeny == null;
		setPodklad(podkladPriStartu(ulozeny, () -> KachleModel.offlineMapyDir(currPrefe()).getEffectiveFile()));
	}

	/** Podklad při startu: uložený, jinak (nová instalace nebo mapa, kterou tato verze nezná) výchozí. */
	static EKaType podkladPriStartu(final String ulozeny, final Supplier<File> slozkaOfflineMap) {
		final EKaType podklad = ulozeny == null ? null : ulozenyPodklad(ulozeny);
		return podklad != null ? podklad : vychoziPodklad(slozkaOfflineMap.get());
	}

	/** Uložený podklad podle jména, i jména mapy ze starší verze; null, když takový podklad není. */
	static EKaType ulozenyPodklad(final String ulozeny) {
		final EKaType podklad = EKaType.podleJmena(ulozeny);
		return podklad != null ? podklad : PODKLADY_5_2.get(ulozeny);
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
