package cz.geokuk.plugins.mapy.kachle.data;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

/** Adresa dlaždice ze služby WMS: požadavek GetMap na obdélník dlaždice v EPSG:3857, 256 × 256 px. */
class WmsUrlBuilder implements KachleUrlBuilder {

	/** Polovina obvodu Země v EPSG:3857. */
	static final double SVET = 20_037_508.342789244;

	private final String sluzba;
	private final String vrstvy;

	WmsUrlBuilder(final String sluzba, final String vrstvy) {
		this.sluzba = sluzba;
		this.vrstvy = vrstvy;
	}

	@Override
	public URL buildUrl(final Ka kaOne) throws MalformedURLException {
		final KaLoc loc = kaOne.getLoc();
		final double velikost = 2 * SVET / (1L << loc.getMoumer());
		final double minX = -SVET + loc.getFromSzUnsignedX() * velikost;
		final double maxY = SVET - loc.getFromSzUnsignedY() * velikost;
		return new URL(String.format(Locale.ROOT,
				"%s?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=%s&STYLES=&CRS=EPSG:3857&BBOX=%.3f,%.3f,%.3f,%.3f&WIDTH=256&HEIGHT=256&FORMAT=image/png&TRANSPARENT=TRUE",
				sluzba, vrstvy, minX, maxY - velikost, minX + velikost, maxY));
	}
}
