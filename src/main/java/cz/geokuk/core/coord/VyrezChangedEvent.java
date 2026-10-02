package cz.geokuk.core.coord;

import cz.geokuk.framework.Event0;

public class VyrezChangedEvent extends Event0<VyrezModel> {

	private final Coord moord;

	public VyrezChangedEvent(final Coord moord) {
		this.moord = moord;
	}

	/**
	 * @return the coord
	 */
	public Coord getMoord() {
		return moord;
	}

}
