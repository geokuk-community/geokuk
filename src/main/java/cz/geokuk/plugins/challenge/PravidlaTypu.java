package cz.geokuk.plugins.challenge;

/** Které druhy keší se do challenge počítají (nálezy i kandidáti). */
public final class PravidlaTypu {

	private final boolean lab;
	private final boolean locationless;

	private PravidlaTypu(final boolean lab, final boolean locationless) {
		this.lab = lab;
		this.locationless = locationless;
	}

	/** D/T matice: Locationless se počítá, Lab nemá D/T. */
	public static PravidlaTypu proMatici() {
		return new PravidlaTypu(false, true);
	}

	/** Kalendář: bez Lab a Locationless. */
	public static PravidlaTypu proKalendar() {
		return new PravidlaTypu(false, false);
	}

	public PravidlaTypu sLab(final boolean zapocitat) {
		return new PravidlaTypu(zapocitat, locationless);
	}

	public PravidlaTypu sLocationless(final boolean zapocitat) {
		return new PravidlaTypu(lab, zapocitat);
	}

	boolean lzePocitat(final VstupKes k) {
		return (lab || !k.lab()) && (locationless || !k.locationless());
	}

	/** Nález se počítá, pokud gc.com nevylučuje jeho log. */
	static boolean platnyNalez(final VstupKes k) {
		return k.nalezena() && k.logNaGc() != VstupKes.StavLogu.NE;
	}

	/** Nenalezená keš, kterou lze jít hledat. */
	static boolean hledatelna(final VstupKes k) {
		return !k.nalezena() && !k.vlastni() && !k.neaktivni();
	}
}
