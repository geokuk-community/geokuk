package cz.geokuk.plugins.mapy.sprava.gui;

import java.io.File;
import java.time.Instant;
import java.util.*;

import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.reader.header.MapFileInfo;

import cz.geokuk.core.program.PrehledovaMapa;

/** Soubory .map ve složce offline map jako řádky popupu Mapy. Čte hlavičky map, proto mimo EDT. */
final class SouboryMap {

	static final String POSKOZENA = "Soubor nejde přečíst jako offline mapu.";

	private SouboryMap() {}

	static List<RadekMapy> nacti(final File slozka) {
		final File[] soubory = slozka == null ? null
				: slozka.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".map") && !f.getName().equalsIgnoreCase(PrehledovaMapa.SOUBOR));
		if (soubory == null) {
			return Collections.emptyList();
		}
		Arrays.sort(soubory, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
		final List<RadekMapy> radky = new ArrayList<>();
		for (final File f : soubory) {
			radky.add(radek(f));
		}
		return radky;
	}

	static RadekMapy radek(final File f) {
		final RadekMapy.Builder b = RadekMapy.vlastni(f.getName()).velikost(f.length());
		MapFile mf = null;
		try {
			mf = new MapFile(f);
			final MapFileInfo i = mf.getMapFileInfo();
			b.datumDat(Instant.ofEpochMilli(i.mapDate));
		} catch (final RuntimeException e) {
			b.chyba(POSKOZENA);
		} finally {
			if (mf != null) {
				mf.close();
			}
		}
		return b.build();
	}
}
