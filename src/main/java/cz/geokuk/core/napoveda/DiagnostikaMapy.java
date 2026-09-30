package cz.geokuk.core.napoveda;

import cz.geokuk.core.coord.VyrezChangedEvent;
import cz.geokuk.plugins.mapy.ZmenaMapNastalaEvent;

/** Předává práci s mapou do informací pro hlášení chyby. */
public class DiagnostikaMapy {

	public void onEvent(final VyrezChangedEvent event) {
		Diagnostika.zaznamenejVyrez(event.getMoord().getMoumer(), event.getMoord().getMoustred());
	}

	public void onEvent(final ZmenaMapNastalaEvent event) {
		if (event.getModel().getPodklad() != null) {
			Diagnostika.zaznamenej("Mapa: podklad " + event.getModel().getPodklad().getNazev());
		}
	}
}
