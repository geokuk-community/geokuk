package cz.geokuk.core.napoveda;

import cz.geokuk.framework.ToggleAction0;

/** Zapíná štítek s výkonem kreslení na mapě (jen v beta kanálu). */
public class UkazatelVykonuAction extends ToggleAction0 {

	private static final long serialVersionUID = 1L;

	public UkazatelVykonuAction() {
		super("Ukazatel výkonu na mapě");
		putValue(SHORT_DESCRIPTION, "V rohu mapy ukazuje dobu překreslení, nejdelší událost a dlaždice za sekundu.");
		setSelected(UkazatelVykonu.ulozeno());
	}

	@Override
	protected void onSlectedChange(final boolean nastaveno) {
		UkazatelVykonu.zapni(nastaveno);
	}
}
