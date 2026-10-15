package cz.geokuk.plugins.mapy.kachle;

import cz.geokuk.framework.Event0;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Myš je nad řádkem mapy v popupu Mapy: její obrys se ukáže v mapě. Klíč je id mapy z katalogu nebo „soubor:“ a jméno souboru, null když nic. */
@AllArgsConstructor
@Getter
public class ZvyrazneniMapyEvent extends Event0<KachleModel> {
	private final String klic;
}
