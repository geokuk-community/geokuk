package cz.geokuk.plugins.mapy.kachle.data;

import java.net.MalformedURLException;
import java.net.URL;

/** Adresa dlaždice ze vzoru s {z}, {x} a {y}. */
class UzivatelskyUrlBuilder implements KachleUrlBuilder {

	private final String vzor;

	UzivatelskyUrlBuilder(final String vzor) {
		this.vzor = vzor;
	}

	@Override
	public URL buildUrl(final Ka kaOne) throws MalformedURLException {
		final KaLoc kaloc = kaOne.getLoc();
		return new URL(vzor.replace("{z}", String.valueOf(kaloc.getMoumer())).replace("{x}", String.valueOf(kaloc.getFromSzUnsignedX())).replace("{y}", String.valueOf(kaloc.getFromSzUnsignedY())));
	}
}
