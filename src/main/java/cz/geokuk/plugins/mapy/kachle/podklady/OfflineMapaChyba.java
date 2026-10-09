package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.IOException;

/** Offline mapu nejde vykreslit: chybí soubory map nebo je nejde otevřít. Nese krátký popis na dlaždici. */
public class OfflineMapaChyba extends IOException {

	private static final long serialVersionUID = 1L;

	private final String kratce;

	public OfflineMapaChyba(final String zprava, final String kratce, final Throwable pricina) {
		super(zprava, pricina);
		this.kratce = kratce;
	}

	/** Popis, který se vejde na dlaždici. */
	public String getKratce() {
		return kratce;
	}
}
