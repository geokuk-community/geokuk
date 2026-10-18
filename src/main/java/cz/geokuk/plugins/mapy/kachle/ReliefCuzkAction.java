package cz.geokuk.plugins.mapy.kachle;

import cz.geokuk.framework.ToggleAction0;

/** Zapíná stínovaný reliéf ČÚZK přes mapový podklad. */
public class ReliefCuzkAction extends ToggleAction0 {
	private static final long serialVersionUID = 1L;

	static final String POPIS = "Stínovaný reliéf České republiky z výškového modelu ČÚZK (DMR 5G) přes jakoukoli mapu. Jen s připojením k internetu, od podrobnějšího měřítka.";

	private KachleModel kachleModel;

	public ReliefCuzkAction() {
		super("Stínovaný reliéf ČÚZK");
		putValue(SHORT_DESCRIPTION, POPIS);
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	public void onEvent(final ReliefCuzkEvent event) {
		setSelected(event.isZapnuto());
	}

	@Override
	protected void onSlectedChange(final boolean nastaveno) {
		kachleModel.setReliefCuzk(nastaveno);
	}
}
