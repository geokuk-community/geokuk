package cz.geokuk.plugins.kesoid.mvc;

import cz.geokuk.framework.Event0;
import cz.geokuk.plugins.kesoid.importek.InformaceOZdrojich;

/** Zdroje, které se právě poprvé načítají; keše z nich ještě načtené nejsou. */
public class NacitaneZdrojeEvent extends Event0<KesoidModel> {
	private final InformaceOZdrojich zdroje;

	NacitaneZdrojeEvent(final InformaceOZdrojich zdroje) {
		this.zdroje = zdroje;
	}

	public InformaceOZdrojich getZdroje() {
		return zdroje;
	}
}
