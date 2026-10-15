package cz.geokuk.plugins.mapy.sprava;

import cz.geokuk.plugins.mapy.sprava.StavMapy.Faze;

/** Stav jedné vrstvy z katalogu: na disku a rozpracované stahování. Neměnný. */
public final class StavVrstvy {

	public enum Mistni {
		NEMAME,
		/** Všechny soubory vrstvy jsou na disku a sedí s katalogem. */
		MAME,
		/** Některé soubory chybí nebo neodpovídají katalogu (jiná verze); stažením se nahradí. */
		JINA_VERZE
	}

	private final KatalogovaVrstva vrstva;
	private final Mistni mistni;
	private final Faze faze;
	private final long stazeno;
	private final boolean rozpracovano;
	private final String chyba;

	private StavVrstvy(final KatalogovaVrstva vrstva, final Mistni mistni, final Faze faze, final long stazeno, final boolean rozpracovano, final String chyba) {
		this.vrstva = vrstva;
		this.mistni = mistni;
		this.faze = faze;
		this.stazeno = stazeno;
		this.rozpracovano = rozpracovano;
		this.chyba = chyba;
	}

	public static StavVrstvy novy(final KatalogovaVrstva vrstva) {
		return new StavVrstvy(vrstva, Mistni.NEMAME, Faze.NIC, 0, false, null);
	}

	StavVrstvy sMistni(final Mistni m, final long st, final boolean rozpr) {
		return new StavVrstvy(vrstva, m, faze, st, rozpr, chyba);
	}

	StavVrstvy sFazi(final Faze f, final String ch) {
		return new StavVrstvy(vrstva, mistni, f, stazeno, rozpracovano, ch);
	}

	StavVrstvy sPrubehem(final long st) {
		return new StavVrstvy(vrstva, mistni, faze, st, true, chyba);
	}

	public KatalogovaVrstva getVrstva() {
		return vrstva;
	}

	public Mistni getMistni() {
		return mistni;
	}

	/** NIC, STAHUJE, OVERUJE, HOTOVO, ZRUSENO nebo CHYBA. */
	public Faze getFaze() {
		return faze;
	}

	/** Bajtů na disku včetně dřív rozpracovaných, ze všech souborů vrstvy. */
	public long getStazeno() {
		return stazeno;
	}

	/** Velikost celé vrstvy podle katalogu. */
	public long getCelkem() {
		return vrstva.getVelikost();
	}

	/** Na disku je rozpracované stahování, které lze dokončit. */
	public boolean isRozpracovano() {
		return rozpracovano;
	}

	/** Chyba pro uživatele, null bez chyby. */
	public String getChyba() {
		return chyba;
	}

	public boolean isBezi() {
		return faze == Faze.STAHUJE || faze == Faze.OVERUJE;
	}

	@Override
	public String toString() {
		return vrstva + " " + mistni + "/" + faze + " " + stazeno + "/" + getCelkem() + (rozpracovano ? " rozpracováno" : "") + (chyba == null ? "" : " " + chyba);
	}
}
