/**
 *
 */
package cz.geokuk.plugins.geocoding;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.hledani.HledaciPodminka0;

/**
 * @author Martin Veverka
 *
 */
public class HledaciPodminka extends HledaciPodminka0 {

	/** Místo pro zpětný geocoding, jinak se hledá podle vzorku. */
	private Wgs zpetne;

	public Wgs getZpetne() {
		return zpetne;
	}

	public void setZpetne(final Wgs zpetne) {
		this.zpetne = zpetne;
	}
}
