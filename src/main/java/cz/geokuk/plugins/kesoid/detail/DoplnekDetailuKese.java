package cz.geokuk.plugins.kesoid.detail;

import cz.geokuk.plugins.kesoid.Wpt;

/** Řádek, který jiný modul doplní do detailu kešoidu; volá se ve vlákně Swingu. */
public interface DoplnekDetailuKese {

	/** Text řádku k waypointu, null = řádek se neukáže. */
	String radek(Wpt wpt);
}
