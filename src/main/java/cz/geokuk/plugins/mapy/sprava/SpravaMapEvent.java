package cz.geokuk.plugins.mapy.sprava;

import cz.geokuk.framework.Event0;

/** Změna stavu katalogové mapy (na disku, na serveru, průběh stahování). Odpaluje se na EDT. */
public class SpravaMapEvent extends Event0<SpravaMapModel> {

	private final StavMapy stav;

	public SpravaMapEvent(final StavMapy stav) {
		this.stav = stav;
	}

	public KatalogovaMapa getMapa() {
		return stav.getMapa();
	}

	public StavMapy getStav() {
		return stav;
	}
}
