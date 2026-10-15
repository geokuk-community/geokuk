package cz.geokuk.plugins.mapy.sprava;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.podklady.OfflineMapy;
import lombok.extern.slf4j.Slf4j;

/** Které stažené offline mapy pokrývají místo. Čte hlavičky souborů, proto ne na EDT. */
@Slf4j
public final class PokrytiMap {

	private PokrytiMap() {}

	/** Mapy ve složce, které pokrývají bod: katalogové podle obrysu, ostatní podle obdélníku z hlavičky. Neplatné mapy vynechá. */
	public static List<File> stazeneMapyProBod(final File slozkaMap, final Wgs bod, final KatalogMap katalog) {
		final List<File> vysledek = new ArrayList<>();
		for (final File soubor : OfflineMapy.mapyVeSlozce(slozkaMap)) {
			final HlavickaMapy hlavicka;
			try {
				hlavicka = HlavickaMapy.precti(soubor);
			} catch (final IOException e) {
				log.debug("Mapa {} se pro pokrytí nepoužije: {}", soubor, e.getMessage());
				continue;
			}
			final Optional<KatalogovaMapa> zKatalogu = katalog.podleSouboru(soubor.getName());
			final boolean pokryva = zKatalogu.isPresent() ? zKatalogu.get().getPokryti().obsahuje(bod) : hlavicka.obsahuje(bod.lat, bod.lon);
			if (pokryva) {
				vysledek.add(soubor);
			}
		}
		return vysledek;
	}
}
