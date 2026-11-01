package cz.geokuk.plugins.kesoid.mvc;

import cz.geokuk.framework.OnoffEvent0;

/** Změnilo se, zda se sleduje schránka kvůli kódům kešoidů. */
public class SledovaniSchrankyEvent extends OnoffEvent0<KesoidModel> {

	SledovaniSchrankyEvent(final boolean onoff) {
		this.onoff = onoff;
	}
}
