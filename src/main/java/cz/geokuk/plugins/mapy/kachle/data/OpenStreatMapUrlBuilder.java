package cz.geokuk.plugins.mapy.kachle.data;

import java.net.MalformedURLException;
import java.net.URL;

class OpenStreatMapUrlBuilder implements KachleUrlBuilder {

	private final String urlBase;
	private final String extension;

	public OpenStreatMapUrlBuilder(final String urlBase, final String extension) {
		this.urlBase = urlBase;
		this.extension = extension;
	}

	@Override
	public URL buildUrl(final Ka kaOne) throws MalformedURLException {

		final StringBuilder sb = new StringBuilder();
		sb.append(urlBase);
		final KaLoc kaloc = kaOne.getLoc();
		sb.append(kaloc.getMoumer());
		sb.append('/');
		sb.append(kaloc.getFromSzUnsignedX());
		sb.append('/');
		sb.append(kaloc.getFromSzUnsignedY());
		sb.append(extension);
		final URL url = new URL(sb.toString());
		return url;
	}

}
