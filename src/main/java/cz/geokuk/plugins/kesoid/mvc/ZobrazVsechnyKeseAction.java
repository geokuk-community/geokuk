package cz.geokuk.plugins.kesoid.mvc;

import java.awt.event.ActionEvent;

import cz.geokuk.core.coordinates.MouRect;
import cz.geokuk.framework.Action0;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Wpt;

/** Výřez a měřítko mapy na všechny zobrazené kešoidy. */
public class ZobrazVsechnyKeseAction extends Action0 {

	private static final long serialVersionUID = 1L;

	private KesBag filtrovane;

	private boolean prvniNacteni = true;

	public ZobrazVsechnyKeseAction() {
		super("Zobrazit všechny keše");
		putValue(SHORT_DESCRIPTION, "Nastaví výřez a měřítko mapy tak, aby na ní byly všechny keše, které filtr zobrazuje.");
		setEnabled(false);
	}

	public void onEvent(final KeskyVyfiltrovanyEvent event) {
		filtrovane = event.getFiltrovane();
		final boolean neco = filtrovane != null && !filtrovane.getWpts().isEmpty();
		setEnabled(neco);
		if (neco && prvniNacteni) {
			prvniNacteni = false;
			// Nový uživatel by jinak po načtení dat koukal na prázdnou mapu a keše hledal.
			if (filtrovane.getIndexator().count(vyrezModel.getMoord().getBoundingRect()) == 0) {
				actionPerformed(null);
			}
		}
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		final MouRect mourect = new MouRect();
		for (final Wpt wpt : filtrovane.getWpts()) {
			mourect.add(wpt.getWgs().toMou());
		}
		if (!mourect.isEmpty()) {
			mourect.resize(1.2);
			vyrezModel.zoomTo(mourect);
		}
	}
}
