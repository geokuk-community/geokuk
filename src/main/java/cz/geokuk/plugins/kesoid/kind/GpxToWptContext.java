package cz.geokuk.plugins.kesoid.kind;

import java.util.Set;

import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.genetika.Alela;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.importek.GpxWpt;
import cz.geokuk.plugins.kesoid.mvc.GccomNick;

public interface GpxToWptContext {

	Genom getGenom();

	GccomNick getGccomNick();

	Set<Alela> definujUzivatslskeAlely(final GpxWpt gpxwpt);

	void expose(Wpt wpt);

	/** Waypoint se páruje s jiným podle textu {@code klic}; zdroje se stejným klíčem se ovlivňují a musí se načítat spolu. */
	default void vazba(final GpxWpt gpxwpt, final String klic) {}

}
