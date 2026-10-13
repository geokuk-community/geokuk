package cz.geokuk.plugins.mapy.kachle;

import cz.geokuk.framework.Event0;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Změnilo se zapnutí překryvu Turistické a cyklotrasy, nebo podklad, nad kterým se kreslí. */
@AllArgsConstructor
@Getter
public class PrekryvTrasEvent extends Event0<KachleModel> {

	/** Uživatel překryv zapnul. */
	private final boolean zapnuto;
	/** Podklad je offline mapa s tématem, které trasy kreslí samo; překryv se nekreslí. */
	private final boolean nadbytecny;

	public boolean isViditelny() {
		return zapnuto && !nadbytecny;
	}
}
