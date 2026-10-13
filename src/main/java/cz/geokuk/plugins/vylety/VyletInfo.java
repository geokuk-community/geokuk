package cz.geokuk.plugins.vylety;

/** Pojmenovaný výlet v seznamu výletů. */
public final class VyletInfo {

	private final String id;
	private final String jmeno;
	private final boolean aktivni;
	private final int pocetLovim;
	private final int pocetTedne;

	VyletInfo(final String id, final String jmeno, final boolean aktivni, final int pocetLovim, final int pocetTedne) {
		this.id = id;
		this.jmeno = jmeno;
		this.aktivni = aktivni;
		this.pocetLovim = pocetLovim;
		this.pocetTedne = pocetTedne;
	}

	public String getId() {
		return id;
	}

	public String getJmeno() {
		return jmeno;
	}

	public boolean isAktivni() {
		return aktivni;
	}

	/** Počet keší, které jdeme lovit (lovim.ggt). */
	public int getPocetLovim() {
		return pocetLovim;
	}

	/** Počet keší, na které teď nepůjdeme (tedne.ggt). */
	public int getPocetTedne() {
		return pocetTedne;
	}

	@Override
	public String toString() {
		return jmeno + (aktivni ? " (aktivní)" : "") + " [" + id + ", " + pocetLovim + "/" + pocetTedne + "]";
	}
}
