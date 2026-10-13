package cz.geokuk.plugins.cesty.akce;

import cz.geokuk.framework.ToggleAction0;
import cz.geokuk.plugins.cesty.CestyModel;
import cz.geokuk.plugins.cesty.PanelCestyEvent;

public class PanelCestyAction extends ToggleAction0 {

	private static final long serialVersionUID = 1L;
	private CestyModel cestyModel;

	public PanelCestyAction() {
		super("Panel cesty");
		putValue(SHORT_DESCRIPTION, "Údaje o vybrané cestě vpravo nahoře v mapě: délka, body a keše.");
	}

	public void inject(final CestyModel cestyModel) {
		this.cestyModel = cestyModel;
	}

	public void onEvent(final PanelCestyEvent event) {
		setSelected(event.isZobrazit());
	}

	@Override
	protected void onSlectedChange(final boolean nastaveno) {
		cestyModel.setPanelCesty(nastaveno);
	}
}
