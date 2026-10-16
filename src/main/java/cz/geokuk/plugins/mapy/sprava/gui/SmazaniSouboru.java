package cz.geokuk.plugins.mapy.sprava.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva;

/** Které soubory patří vrstvě nebo mapě z katalogu a jejich smazání; nic nemaže bez toho, aby se předem ukázalo, co to je. */
final class SmazaniSouboru {

	/** K souboru vrstvy patří kontrolní součet a rozpracované nebo zálohované stažení. */
	private static final String[] PRIPONY = { "", ".sha256", ".stahuje", ".stahuje.info", ".stara" };
	private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("d. M. yyyy", new Locale("cs")).withZone(ZoneId.systemDefault());

	private SmazaniSouboru() {}

	/** Existující soubory vrstvy ve složce vrstev včetně kontrolních součtů a rozpracovaných souborů. */
	static List<File> souboryVrstvy(final File slozkaVrstev, final KatalogovaVrstva vrstva) {
		final List<File> soubory = new ArrayList<>();
		if (slozkaVrstev == null) {
			return soubory;
		}
		for (final KatalogovaVrstva.Soubor s : vrstva.getSoubory()) {
			for (final String pripona : PRIPONY) {
				final File f = new File(slozkaVrstev, s.getCesta() + pripona);
				if (f.isFile()) {
					soubory.add(f);
				}
			}
		}
		return soubory;
	}

	/** Soubor mapy z katalogu ve složce offline map, když tam je. */
	static List<File> souboryMapy(final File slozkaMap, final KatalogovaMapa mapa) {
		final List<File> soubory = new ArrayList<>();
		if (slozkaMap != null) {
			final File[] vsechny = slozkaMap.listFiles(f -> f.isFile() && f.getName().equalsIgnoreCase(mapa.getNazevSouboru()));
			if (vsechny != null) {
				for (final File f : vsechny) {
					soubory.add(f);
				}
			}
		}
		return soubory;
	}

	/** Soubory ke smazání u země staženou dvakrát: regiony, nebo celá země (ta jen když ji regiony pokrývají). */
	static List<File> souboryDvojite(final File slozkaMap, final cz.geokuk.plugins.mapy.sprava.PokrytiMap.DvojitaMapa d, final boolean regiony) {
		final List<File> soubory = new ArrayList<>();
		if (regiony) {
			for (final KatalogovaMapa r : d.getRegiony()) {
				soubory.addAll(souboryMapy(slozkaMap, r));
			}
		} else if (d.isRegionyPokryvajiCelek()) {
			soubory.addAll(souboryMapy(slozkaMap, d.getCelek()));
		}
		return soubory;
	}

	/** Seznam souborů pro potvrzení: jméno, velikost a datum změny. */
	static String popis(final List<File> soubory) {
		final StringBuilder sb = new StringBuilder();
		long celkem = 0;
		for (final File f : soubory) {
			celkem += f.length();
			sb.append(f.getName()).append(" (").append(RadekMapy.velikost(f.length())).append(", změněno ").append(DATUM.format(Instant.ofEpochMilli(f.lastModified()))).append(")\n");
		}
		return sb.append("Celkem ").append(RadekMapy.velikost(celkem)).toString();
	}

	/** Smaže soubory; vrátí ty, které smazat nešlo. */
	static List<File> smaz(final List<File> soubory) {
		final List<File> nesmazane = new ArrayList<>();
		for (final File f : soubory) {
			try {
				Files.deleteIfExists(f.toPath());
			} catch (final IOException e) {
				nesmazane.add(f);
			}
		}
		return nesmazane;
	}
}
