package cz.geokuk.plugins.mapy.sprava;

import cz.geokuk.framework.Event0;

/** Katalog vrstev se změnil (stažený ze serveru, načtený ze složky vrstev), nebo stažení selhalo. Odpaluje se na EDT. */
public class KatalogVrstevEvent extends Event0<SpravaMapModel> {

	private final KatalogVrstev katalog;
	private final String chyba;

	public KatalogVrstevEvent(final KatalogVrstev katalog, final String chyba) {
		this.katalog = katalog;
		this.chyba = chyba;
	}

	/** Katalog, který teď platí. */
	public KatalogVrstev getKatalog() {
		return katalog;
	}

	/** Proč katalog nejde stáhnout (pro uživatele); null = povedlo se. */
	public String getChyba() {
		return chyba;
	}
}
