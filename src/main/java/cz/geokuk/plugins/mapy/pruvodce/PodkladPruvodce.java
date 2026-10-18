package cz.geokuk.plugins.mapy.pruvodce;

import java.util.Collection;

import cz.geokuk.plugins.mapy.kachle.data.EKaType;

/** Podklad, který průvodce nastavením map nabízí; offline podklad se kreslí z map ve složce, online ze serveru. */
public enum PodkladPruvodce {

	/** Offline mapy z katalogu s tématem GeoKuk. */
	TURISTICKA_OFFLINE("GeoKuk", true, null, EKaType.OFFLINE_MF),
	CUZK_TOPO("ČÚZK topo", false, "europe/czech-republic", EKaType.CUZK_ZTM),
	CUZK_ORTO("ČÚZK ortofoto", false, "europe/czech-republic", EKaType.CUZK_ORTO),
	SK_ORTO("ZBGIS ortofoto", false, "europe/slovakia", EKaType.SK_ZBGIS_ORTO),
	MAPY_CZ("Mapy.cz turistická", false, null, EKaType.TURIST_M),
	OSM("OpenStreetMap", false, null, EKaType.OPEN_STREET),
	LETECKA("Letecká", false, null, EKaType.OPHOTO_M);

	private final String nazev;
	private final boolean offline;
	private final String zeme;
	private final EKaType podklad;

	PodkladPruvodce(final String nazev, final boolean offline, final String zeme, final EKaType podklad) {
		this.nazev = nazev;
		this.offline = offline;
		this.zeme = zeme;
		this.podklad = podklad;
	}

	public String getNazev() {
		return nazev;
	}

	/** Kreslí se z map ve složce, k použití je potřeba je stáhnout. */
	public boolean isOffline() {
		return offline;
	}

	public EKaType getPodklad() {
		return podklad;
	}

	/** Podklad je nabídnutý, když pokrývá aspoň jednu z vybraných map (podklady jedné země jen u ní); ostatní podklady vždy. */
	public boolean jeDostupny(final Collection<String> klice) {
		return zeme == null || klice.stream().anyMatch(k -> k.equals(zeme) || k.startsWith(zeme + "/"));
	}
}
