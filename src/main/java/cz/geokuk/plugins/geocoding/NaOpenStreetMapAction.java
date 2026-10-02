package cz.geokuk.plugins.geocoding;

import java.awt.event.ActionEvent;
import java.net.MalformedURLException;
import java.net.URL;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.Action0;
import cz.geokuk.util.process.BrowserOpener;

public class NaOpenStreetMapAction extends Action0 {

	private static final long serialVersionUID = -5194259213320265512L;
	private final Wgs wgs;

	public NaOpenStreetMapAction(final Wgs wgs) {
		super("Na OpenStreetMap...");
		putValue(SHORT_DESCRIPTION, "Zobrazí místo na webu OpenStreetMap s adresou a okolím.");
		this.wgs = wgs;
		setEnabled(wgs != null);
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		try {
			BrowserOpener.displayURL(new URL(Nominatim.odkazNaMapu(wgs)));
		} catch (final MalformedURLException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
