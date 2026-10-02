/**
 *
 */
package cz.geokuk.plugins.geocoding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import cz.geokuk.core.hledani.Hledac0;
import cz.geokuk.core.hledani.HledaciPodminka0;

/**
 * @author Martin Veverka
 *
 */
public class Hledac extends Hledac0<Nalezenec> {

	@Override
	public List<Nalezenec> hledej(final HledaciPodminka0 aPodm) {
		final HledaciPodminka podm = (HledaciPodminka) aPodm;
		try {
			return podm.getZpetne() != null ? Nominatim.zpetne(podm.getZpetne()) : Nominatim.hledej(podm.getVzorek(), podm.getStredHledani());
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
