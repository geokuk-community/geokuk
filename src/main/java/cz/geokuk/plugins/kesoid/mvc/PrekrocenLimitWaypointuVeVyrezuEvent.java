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
	private final int limit;

	/** @param limit překročený limit, 0 když překročen není */
	PrekrocenLimitWaypointuVeVyrezuEvent(final int limit) {
		this.limit = limit;
	}

	public int getLimit() {
		return limit;
	}

	/**
	 * @return the prekrocen
	 */
	public boolean isPrekrocen() {
		return limit > 0;
	}

}
