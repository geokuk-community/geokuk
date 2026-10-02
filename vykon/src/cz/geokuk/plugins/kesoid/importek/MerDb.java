package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.nio.file.*;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.mvc.GsakParametryNacitani;

/** Měření načtení databáze GeoGetu nebo GSAKu: {@code MerDb geoget|gsak soubor.db3}. */
public class MerDb {

	/** Přečtené bajty procesu, jen na Linuxu, jinde -1. */
	static long precteno() {
		try {
			for (final String radek : Files.readAllLines(Paths.get("/proc/self/io"))) {
				if (radek.startsWith("rchar:")) {
					return Long.parseLong(radek.substring(6).trim());
				}
			}
		} catch (final Exception e) {
			// mimo Linux
		}
		return -1;
	}

	public static void main(final String[] a) throws Exception {
		final int[] n = { 0 };
		final IImportBuilder builder = new IImportBuilder() {
			@Override
			public void init() {}

			@Override
			public void done() {}

			@Override
			public void addGpxWpt(final GpxWpt w) {
				n[0]++;
			}

			@Override
			public void addTrackWpt(final GpxWpt w) {}

			@Override
			public void begTrack() {}

			@Override
			public void begTrackSegment() {}

			@Override
			public void endTrack() {}

			@Override
			public void endTrackSegment() {}

			@Override
			public void setTrackName(final String s) {}
		};
		final ProgressModel progress = new ProgressModel();
		progress.inject(u -> {});
		final File soubor = new File(a[1]);
		final long r0 = precteno();
		final long t0 = System.nanoTime();
		if (a[0].equals("geoget")) {
			new GeogetLoader().nacti(soubor, builder, null, progress);
		} else {
			new GsakDbLoader(GsakParametryNacitani::new).nacti(soubor, builder, null, progress);
		}
		final long ms = (System.nanoTime() - t0) / 1_000_000;
		final long r1 = precteno();
		System.out.println(a[0] + " keší " + n[0] + ", " + ms + " ms, přečteno " + (r0 < 0 ? "?" : String.valueOf((r1 - r0) / 1024 / 1024)) + " MB");
	}
}
