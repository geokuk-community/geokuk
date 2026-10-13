package cz.geokuk.core.napoveda;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.reader.header.MapFileInfo;

import cz.geokuk.core.coord.VyrezChangedEvent;
import cz.geokuk.core.program.FConst;
import cz.geokuk.core.program.PrehledovaMapa;
import cz.geokuk.plugins.mapy.ZmenaMapNastalaEvent;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.podklady.OfflineMapy;
import cz.geokuk.plugins.mapy.kachle.podklady.TemaOfflineMapy;

/** Předává práci s mapou do informací pro hlášení chyby. */
public class DiagnostikaMapy {

	public void inject(final KachleModel kachleModel) {
		Diagnostika.setPopisOfflineMapy(() -> "offline mapa " + (kachleModel.isOfflineMapaOstra() ? "ostrá" : "rychlejší") + ", písmo " + kachleModel.getOfflineMapaPismoProcent() + " %");
		Diagnostika.setOfflineMapy(() -> {
			final OfflineMapy mapy = kachleModel.getZiskavac() == null ? null : kachleModel.getZiskavac().getOfflineMapy();
			final File cache = kachleModel.getKachleCacheFolderHolder().getKachleCacheFolder() == null ? null
					: kachleModel.getKachleCacheFolderHolder().getKachleCacheFolder().getEffectiveFile();
			return mapy == null ? "" : popis(mapy.getSlozka(), mapy.getTema(), mapy.getChybaTematu(), mapy.getChyba(), cache);
		});
	}

	/** Oddíl o offline mapách; jen jména souborů, cesty bez domovské složky. */
	static String popis(final File slozka, final TemaOfflineMapy tema, final String chybaTematu, final String chybaMapy, final File cache) {
		final StringBuilder sb = new StringBuilder("Offline mapy:\n");
		sb.append("  mapsforge: ").append(verzeMapsforge()).append('\n');
		sb.append("  složka: ").append(slozka == null ? "nenastavená" : Diagnostika.bezDomova(slozka)).append('\n');
		if (tema != null) {
			sb.append("  téma: ").append(tema.getNazev()).append(tema.isVestavene() ? " (vestavěné)" : " (soubor)").append('\n');
		}
		if (chybaTematu != null) {
			sb.append("  chyba tématu: ").append(Diagnostika.textBezDomova(chybaTematu)).append('\n');
		}
		if (chybaMapy != null) {
			final String text = slozka == null ? chybaMapy : chybaMapy.replace(slozka.getPath(), Diagnostika.bezDomova(slozka));
			sb.append("  poslední chyba mapy: ").append(Diagnostika.textBezDomova(text)).append('\n');
		}
		final List<File> soubory = OfflineMapy.mapyVeSlozce(slozka);
		boolean prehled = false;
		for (final File f : soubory) {
			prehled |= PrehledovaMapa.SOUBOR.equalsIgnoreCase(f.getName());
		}
		sb.append("  přehledová mapa: ").append(prehled ? "ano" : "ne").append('\n');
		if (cache != null) {
			sb.append("  cache dlaždic: ").append(mb(new File(cache, "tiles.sqlite").length())).append(", symboly témat ").append(mb(velikost(new File(cache, "offline-temata"))))
					.append('\n');
		}
		sb.append("  mapy (").append(soubory.size()).append("):\n");
		for (final File f : soubory) {
			sb.append("    ").append(popisMapy(f)).append('\n');
		}
		return sb.toString();
	}

	private static String verzeMapsforge() {
		try {
			return FConst.atributManifestu("Mapsforge-Version");
		} catch (final IllegalArgumentException e) {
			return "neznámá verze"; // spuštěno mimo jar
		}
	}

	static String popisMapy(final File f) {
		final StringBuilder sb = new StringBuilder(f.getName()).append(": ").append(mb(f.length()));
		MapFile mf = null;
		try {
			mf = new MapFile(f);
			final MapFileInfo i = mf.getMapFileInfo();
			final SimpleDateFormat datum = new SimpleDateFormat("d. M. yyyy", Locale.ROOT);
			datum.setTimeZone(TimeZone.getTimeZone("UTC"));
			sb.append(", data ").append(datum.format(new Date(i.mapDate)));
			sb.append(String.format(Locale.ROOT, ", oblast %.2f,%.2f – %.2f,%.2f", i.boundingBox.minLatitude, i.boundingBox.minLongitude, i.boundingBox.maxLatitude,
					i.boundingBox.maxLongitude));
			sb.append(", zoom ").append(i.zoomLevelMin).append('–').append(i.zoomLevelMax);
			sb.append(", formát v").append(i.fileVersion);
			if (i.createdBy != null) {
				sb.append(", ").append(i.createdBy);
			}
		} catch (final RuntimeException e) {
			sb.append(", nejde přečíst: ").append(e.getMessage());
		} finally {
			if (mf != null) {
				mf.close();
			}
		}
		return sb.toString();
	}

	private static long velikost(final File slozka) {
		final File[] soubory = slozka.listFiles();
		long v = 0;
		if (soubory != null) {
			for (final File f : soubory) {
				v += f.isFile() ? f.length() : 0;
			}
		}
		return v;
	}

	private static String mb(final long bajtu) {
		return String.format(Locale.ROOT, "%.1f MB", bajtu / 1024.0 / 1024.0);
	}

	public void onEvent(final VyrezChangedEvent event) {
		Diagnostika.zaznamenejVyrez(event.getMoord().getMoumer(), event.getMoord().getMoustred());
	}

	public void onEvent(final ZmenaMapNastalaEvent event) {
		if (event.getModel().getPodklad() != null) {
			Diagnostika.zaznamenej("Mapa: podklad " + event.getModel().getPodklad().getNazev());
		}
	}
}
