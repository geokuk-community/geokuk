package cz.geokuk.plugins.mapy.sprava.gui;

import java.io.File;
import java.io.IOException;
import java.util.*;

import cz.geokuk.core.program.PrehledovaMapa;
import cz.geokuk.plugins.mapy.sprava.*;

/** Soubory .map ve složce offline map jako řádky popupu Mapy a obrysy pokrytí. Čte hlavičky map, proto mimo EDT. */
final class SouboryMap {

	static final String POSKOZENA = "Soubor nejde přečíst jako offline mapu.";

	private SouboryMap() {}

	static List<File> soubory(final File slozka) {
		final File[] soubory = slozka == null ? null
				: slozka.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".map") && !f.getName().equalsIgnoreCase(PrehledovaMapa.SOUBOR));
		if (soubory == null) {
			return Collections.emptyList();
		}
		Arrays.sort(soubory, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
		return Arrays.asList(soubory);
	}

	/** Mapa se jménem souboru z katalogu je katalogová (název země, zdroj, licence), ostatní jsou soubory uživatele. */
	static List<RadekMapy> nacti(final File slozka, final KatalogMap katalog) {
		final List<RadekMapy> radky = new ArrayList<>();
		for (final File f : soubory(slozka)) {
			radky.add(radek(f, katalog));
		}
		return radky;
	}

	static RadekMapy radek(final File f, final KatalogMap katalog) {
		final Optional<KatalogovaMapa> zKatalogu = katalog.podleSouboru(f.getName());
		final RadekMapy.Builder b;
		if (zKatalogu.isPresent()) {
			final KatalogovaMapa m = zKatalogu.get();
			b = RadekMapy.katalogova(m.getId(), m.getNazev(), RadekMapy.Stav.AKTUALNI).skupina(m.getSkupina()).zdroj(m.getZdroj()).licence(m.getLicence());
		} else {
			b = RadekMapy.vlastni(f.getName());
		}
		b.velikost(f.length());
		try {
			b.datumDat(HlavickaMapy.precti(f).getDatumDat());
		} catch (final IOException | RuntimeException e) {
			b.chyba(POSKOZENA).stav(RadekMapy.Stav.CHYBA).veSlozce(true);
		}
		return b.build();
	}

	/** Území map ve složce: katalogové podle obrysu země, ostatní obdélník z hlavičky. Nečitelné soubory vynechá. */
	static List<PolygonPokryti> obrysy(final File slozka, final KatalogMap katalog) {
		final List<PolygonPokryti> obrysy = new ArrayList<>();
		for (final File f : soubory(slozka)) {
			try {
				final HlavickaMapy h = HlavickaMapy.precti(f);
				final Optional<KatalogovaMapa> zKatalogu = katalog.podleSouboru(f.getName());
				obrysy.add(zKatalogu.isPresent() ? zKatalogu.get().getPokryti() : PolygonPokryti.obdelnik(h.getJih(), h.getZapad(), h.getSever(), h.getVychod()));
			} catch (final IOException | RuntimeException e) {
				// Nečitelná mapa nic nepokrývá.
			}
		}
		return obrysy;
	}
}
