package cz.geokuk.plugins.kesoid.mvc;

import cz.geokuk.framework.Event0;
import cz.geokuk.plugins.kesoid.EZobrazeniKesi;

public class ZobrazeniKesiEvent extends Event0<KesoidModel> {
	private final EZobrazeniKesi zobrazeni;

	ZobrazeniKesiEvent(final EZobrazeniKesi zobrazeni) {
		this.zobrazeni = zobrazeni;
	}

	public EZobrazeniKesi getZobrazeni() {
		return zobrazeni;
	}
}
