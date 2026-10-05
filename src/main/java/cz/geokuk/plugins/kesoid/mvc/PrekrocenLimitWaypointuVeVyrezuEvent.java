/**
 *
 */
package cz.geokuk.plugins.kesoid.mvc;

import cz.geokuk.framework.Event0;

/**
 * @author Martin Veverka
 *
 */
public class PrekrocenLimitWaypointuVeVyrezuEvent extends Event0<KesoidModel> {
	private final boolean prekrocen;
	private final boolean tecky;
	private final int limit;

	PrekrocenLimitWaypointuVeVyrezuEvent(final boolean prekrocen, final boolean tecky, final int limit) {
		this.prekrocen = prekrocen;
		this.tecky = tecky;
		this.limit = limit;
	}

	/** Překročen limit teček, ne ikon. */
	public boolean isTecky() {
		return tecky;
	}

	public int getLimit() {
		return limit;
	}

	/**
	 * @return the prekrocen
	 */
	public boolean isPrekrocen() {
		return prekrocen;
	}

}
