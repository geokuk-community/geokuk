package cz.geokuk.plugins.kesoid.importek;

/** Stav jedné položky (souboru nebo databáze) zdroje. */
public enum StavZdroje {
	/** Zapnuto, v právě běžícím načítání na ni ještě nepřišla řada. */
	CEKA_NA_RADU("Čeká na načtení"),
	NACITA_SE("Načítá se"),
	/** Databázi drží zamčenou jiný program, načte se po uvolnění zámku. */
	CEKA_NA_ZAPIS("Čeká na zápis"),
	NACTENO("Načteno"),
	CHYBA("Chyba čtení"),
	VYPNUTO("Vypnuto");

	private final String text;

	StavZdroje(final String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}
}
