package cz.geokuk.plugins.kesoid.importek;

/** Druh zdroje keší podle toho, ze které datové složky soubor pochází. */
public enum TypZdroje {
	GPX("GPX"), GEOGET("GeoGet"), GSAK("GSAK"), OPENSAK("OpenSAK");

	private final String nazev;

	TypZdroje(final String nazev) {
		this.nazev = nazev;
	}

	public String getNazev() {
		return nazev;
	}
}
