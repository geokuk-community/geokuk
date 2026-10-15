package cz.geokuk.plugins.mapy.kachle;

import cz.geokuk.framework.Event0;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Zapnutí obrysu území pokrytého offline mapami. */
@AllArgsConstructor
@Getter
public class ObrysPokrytiEvent extends Event0<KachleModel> {
	private final boolean zapnuto;
}
