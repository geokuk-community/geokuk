package cz.geokuk.plugins.mapy.sprava;

import cz.geokuk.framework.Event0;

/** Změna stavu vrstvy z katalogu (na disku, průběh stahování). Odpaluje se na EDT. */
public class SpravaVrstevEvent extends Event0<SpravaMapModel> {

	private final StavVrstvy stav;

	public SpravaVrstevEvent(final StavVrstvy stav) {
		this.stav = stav;
	}

	public KatalogovaVrstva getVrstva() {
		return stav.getVrstva();
	}

	public StavVrstvy getStav() {
		return stav;
	}
}
