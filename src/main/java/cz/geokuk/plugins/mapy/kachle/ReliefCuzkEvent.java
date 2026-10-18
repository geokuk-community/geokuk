package cz.geokuk.plugins.mapy.kachle;

import cz.geokuk.framework.Event0;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Změnilo se zapnutí překryvu Stínovaný reliéf ČÚZK. */
@AllArgsConstructor
@Getter
public class ReliefCuzkEvent extends Event0<KachleModel> {

	private final boolean zapnuto;
}
