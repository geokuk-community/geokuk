package cz.geokuk.plugins.kesoid.detail;

import cz.geokuk.framework.Event0;

/** Změnil se doplněk detailu kešoidu nebo jeho data. */
public class DoplnekDetailuEvent extends Event0<DoplnekDetailuModel> {

	private final DoplnekDetailuKese doplnek;

	DoplnekDetailuEvent(final DoplnekDetailuKese doplnek) {
		this.doplnek = doplnek;
	}

	/** Doplněk, nebo null, když žádný není. */
	public DoplnekDetailuKese getDoplnek() {
		return doplnek;
	}
}
