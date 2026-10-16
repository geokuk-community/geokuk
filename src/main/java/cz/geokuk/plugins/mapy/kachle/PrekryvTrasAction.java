package cz.geokuk.plugins.mapy.kachle;

import cz.geokuk.framework.ToggleAction0;

/** Zapíná překryv s turistickými a cyklotrasami přes mapový podklad. */
public class PrekryvTrasAction extends ToggleAction0 {
	private static final long serialVersionUID = 1L;

	static final String POPIS = "Turistické a cyklotrasy přes jakoukoli mapu. Kreslí se z vrstvy tras nebo z offline map z osm.paws.cz ve složce offline map.";
	static final String POPIS_NADBYTECNY = "Trasy kreslí téma offline mapy.";

	private KachleModel kachleModel;

	public PrekryvTrasAction() {
		super("Turistické a cyklotrasy");
		putValue(SHORT_DESCRIPTION, POPIS);
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	public void onEvent(final PrekryvTrasEvent event) {
		setSelected(event.isZapnuto());
		setEnabled(!event.isNadbytecny());
		putValue(SHORT_DESCRIPTION, event.isNadbytecny() ? POPIS_NADBYTECNY : POPIS);
	}

	@Override
	protected void onSlectedChange(final boolean nastaveno) {
		kachleModel.setPrekryvTras(nastaveno);
	}
}
