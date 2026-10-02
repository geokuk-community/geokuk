package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.util.Locale;

/** Měření dotažení hintu z databáze: {@code MerHint soubor.db3 geoget|gsak}. První dotaz načítá ovladač SQLite. */
public class MerHint {

	public static void main(final String[] a) throws Exception {
		for (int i = 0; i < 5; i++) {
			final long t0 = System.nanoTime();
			final String kod = "GC" + Integer.toHexString(0x10000 + i).toUpperCase(Locale.ROOT);
			final String hint = HintZDatabaze.nacti(new File(a[0]), a[1].equals("geoget") ? HintZDatabaze.GEOGET : HintZDatabaze.GSAK, kod);
			System.out.println(a[1] + " " + hint + ": " + (System.nanoTime() - t0) / 1000 + " µs");
		}
	}
}
