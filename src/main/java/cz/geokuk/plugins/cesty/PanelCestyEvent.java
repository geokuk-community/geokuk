package cz.geokuk.plugins.cesty;

import cz.geokuk.framework.Event0;

public class PanelCestyEvent extends Event0<CestyModel> {
	private final boolean zobrazit;

	PanelCestyEvent(final boolean zobrazit) {
		this.zobrazit = zobrazit;
	}

	public boolean isZobrazit() {
		return zobrazit;
	}
}
