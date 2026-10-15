package cz.geokuk.plugins.mapy.sprava;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.podklady.OfflineMapy;
import lombok.extern.slf4j.Slf4j;

/** Které stažené offline mapy pokrývají místo. Čte hlavičky souborů, proto ne na EDT. */
@Slf4j
public final class PokrytiMap {

	private PokrytiMap() {}

	/** Celá země stažená zároveň s některými svými regiony: stejné území je v mapách dvakrát. */
	public static final class DvojitaMapa {
		private final KatalogovaMapa celek;
		private final List<KatalogovaMapa> regiony;

		DvojitaMapa(final KatalogovaMapa celek, final List<KatalogovaMapa> regiony) {
			this.celek = celek;
			this.regiony = Collections.unmodifiableList(regiony);
		}

		public KatalogovaMapa getCelek() {
			return celek;
		}

		/** Stažené regiony celé země, aspoň jeden. */
		public List<KatalogovaMapa> getRegiony() {
			return regiony;
		}
	}

	/** Celé země, které jsou ve složce zároveň s některým svým regionem. Čte jen jména souborů, ne mapy. */
	public static List<DvojitaMapa> dvojiteMapy(final File slozkaMap, final KatalogMap katalog) {
		final Set<String> stazene = new HashSet<>();
		for (final File f : OfflineMapy.mapyVeSlozce(slozkaMap)) {
			stazene.add(f.getName().toLowerCase(Locale.ROOT));
		}
		final List<DvojitaMapa> vysledek = new ArrayList<>();
		for (final KatalogovaMapa m : katalog.getMapy()) {
			if (!stazene.contains(m.getNazevSouboru().toLowerCase(Locale.ROOT))) {
				continue;
			}
			final List<KatalogovaMapa> regiony = new ArrayList<>();
			for (final KatalogovaMapa r : katalog.regiony(m)) {
				if (stazene.contains(r.getNazevSouboru().toLowerCase(Locale.ROOT))) {
					regiony.add(r);
				}
			}
			if (!regiony.isEmpty()) {
				vysledek.add(new DvojitaMapa(m, regiony));
			}
		}
		return vysledek;
	}

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
