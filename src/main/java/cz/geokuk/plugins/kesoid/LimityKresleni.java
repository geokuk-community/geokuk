package cz.geokuk.plugins.kesoid;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.MyPreferences;

/**
 * Nejvyšší počty waypointů ve výřezu, do kterých se jednotlivé vrstvy ještě kreslí.
 * Mění se za běhu z dialogu, vrstvy si hodnotu čtou při každém kreslení.
 */
public final class LimityKresleni {

	public enum Vrstva {
		KESE("Keše a waypointy"), POPISKY("Popisky"), KRUHY("Zvýrazňovací kruhy"), OBSAZENOST("Obsazenost");

		public final String nazev;

		Vrstva(final String nazev) {
			this.nazev = nazev;
		}
	}

	private static final String UZEL = "limityKresleni";

	private static final int[] limity = new int[Vrstva.values().length];
	private static final long[] posledniKresleniNs = new long[Vrstva.values().length];
	private static volatile int posledniPocetVeVyrezu;

	static {
		final MyPreferences pref = MyPreferences.current().node(UZEL);
		for (final Vrstva v : Vrstva.values()) {
			limity[v.ordinal()] = Math.max(0, pref.getInt(v.name(), FConst.MAX_POC_WPT_NA_MAPE));
		}
	}

	private LimityKresleni() {}

	public static synchronized int get(final Vrstva vrstva) {
		return limity[vrstva.ordinal()];
	}

	public static synchronized void set(final Vrstva vrstva, final int limit) {
		limity[vrstva.ordinal()] = Math.max(0, limit);
		MyPreferences.current().node(UZEL).putInt(vrstva.name(), limity[vrstva.ordinal()]);
	}

	public static boolean prekroceno(final Vrstva vrstva, final int pocetVeVyrezu) {
		return pocetVeVyrezu > get(vrstva);
	}

	/** Doba posledního kreslení vrstvy na obrazovku, 0 = vrstva nekreslila nic. */
	public static synchronized void zapisKresleni(final Vrstva vrstva, final long ns) {
		posledniKresleniNs[vrstva.ordinal()] = ns;
	}

	public static synchronized long getPosledniKresleniNs(final Vrstva vrstva) {
		return posledniKresleniNs[vrstva.ordinal()];
	}

	public static void zapisPocetVeVyrezu(final int pocet) {
		posledniPocetVeVyrezu = pocet;
	}

	public static int getPosledniPocetVeVyrezu() {
		return posledniPocetVeVyrezu;
	}
}
