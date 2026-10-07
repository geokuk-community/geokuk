package cz.geokuk.plugins.kesoid.importek;

import java.io.File;

/** Neměnný snímek stavu jedné položky zdroje. */
public final class StavPolozky {
	/** Počet waypointů, který zatím není znám (položka se od startu programu nenačetla). */
	public static final int NEZNAMO = -1;

	private final File soubor;
	private final String nazev;
	private final TypZdroje typ;
	private final StavZdroje stav;
	private final int postup;
	private final long velikostNaDisku;
	private final int wpCelkem;
	private final int wpBrano;
	private final boolean zapnuto;
	private final boolean typVypnut;
	private final String chyba;

	StavPolozky(final File soubor, final String nazev, final TypZdroje typ, final StavZdroje stav, final int postup, final long velikostNaDisku, final int wpCelkem, final int wpBrano, final boolean zapnuto,
			final boolean typVypnut, final String chyba) {
		this.soubor = soubor;
		this.nazev = nazev;
		this.typ = typ;
		this.stav = stav;
		this.postup = postup;
		this.velikostNaDisku = velikostNaDisku;
		this.wpCelkem = wpCelkem;
		this.wpBrano = wpBrano;
		this.zapnuto = zapnuto;
		this.typVypnut = typVypnut;
		this.chyba = chyba;
	}

	public File getSoubor() {
		return soubor;
	}

	/** Jméno k zobrazení: cesta vůči složce zdroje (u souboru přímo ve složce jen jeho jméno). */
	public String getNazev() {
		return nazev;
	}

	/** Úplná cesta, třeba pro tooltip. */
	public String getCesta() {
		return soubor.getPath();
	}

	public TypZdroje getTyp() {
		return typ;
	}

	public StavZdroje getStav() {
		return stav;
	}

	/** Průběh načítání 0–100, jen ve stavu {@link StavZdroje#NACITA_SE}. */
	public int getPostup() {
		return postup;
	}

	/** Velikost souboru i jeho -wal. */
	public long getVelikostNaDisku() {
		return velikostNaDisku;
	}

	/** Počet waypointů ve zdroji, nebo {@link #NEZNAMO}. */
	public int getWpCelkem() {
		return wpCelkem;
	}

	/** Počet waypointů, které se ze zdroje opravdu berou; zbytek jsou duplicity z jiných zdrojů. */
	public int getWpBrano() {
		return wpBrano;
	}

	public int getPocetDuplicit() {
		return wpCelkem == NEZNAMO ? 0 : wpCelkem - wpBrano;
	}

	/** Vlastní volba položky; zůstává, i když je celý typ vypnutý. */
	public boolean isZapnuto() {
		return zapnuto;
	}

	/** Celý typ zdroje je vypnutý, položky zachovávají svou volbu, ale nenačítají se. */
	public boolean isTypVypnut() {
		return typVypnut;
	}

	/** Položka se opravdu načítá: zapnutá a její typ není vypnutý. */
	public boolean isNacitat() {
		return zapnuto && !typVypnut;
	}

	/** Krátká věta pro stav {@link StavZdroje#CHYBA}, jinak null. */
	public String getChyba() {
		return chyba;
	}

	StavPolozky s(final StavZdroje novyStav, final int novyPostup, final String novaChyba) {
		return new StavPolozky(soubor, nazev, typ, novyStav, novyPostup, velikostNaDisku, wpCelkem, wpBrano, zapnuto, typVypnut, novaChyba);
	}

	StavPolozky sPocty(final int celkem, final int brano) {
		return new StavPolozky(soubor, nazev, typ, stav, postup, velikostNaDisku, celkem, brano, zapnuto, typVypnut, chyba);
	}

	StavPolozky sVelikosti(final long velikost) {
		return new StavPolozky(soubor, nazev, typ, stav, postup, velikost, wpCelkem, wpBrano, zapnuto, typVypnut, chyba);
	}

	StavPolozky sTypem(final TypZdroje novyTyp) {
		return new StavPolozky(soubor, nazev, novyTyp, stav, postup, velikostNaDisku, wpCelkem, wpBrano, zapnuto, typVypnut, chyba);
	}

	StavPolozky sTypVypnut(final boolean nove) {
		return new StavPolozky(soubor, nazev, typ, stav, postup, velikostNaDisku, wpCelkem, wpBrano, zapnuto, nove, chyba);
	}

	StavPolozky sZapnutim(final boolean nove) {
		return new StavPolozky(soubor, nazev, typ, stav, postup, velikostNaDisku, wpCelkem, wpBrano, nove, typVypnut, chyba);
	}

	@Override
	public String toString() {
		return nazev + " " + typ + " " + stav + (stav == StavZdroje.NACITA_SE ? " " + postup + "%" : "");
	}
}
