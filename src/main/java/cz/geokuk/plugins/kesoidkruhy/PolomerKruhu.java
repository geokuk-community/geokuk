package cz.geokuk.plugins.kesoidkruhy;

import java.text.NumberFormat;
import java.util.Locale;

/** Poloměr jednotkových kruhů v metrech: přednastavené hodnoty posuvníku, převod z textu a do textu, přepočet na pixely. */
final class PolomerKruhu {

	static final int VYCHOZI_METRU = 100;
	static final int MIN_METRU = 1;
	static final int MAX_METRU = 50_000;
	/** Menší kruh se nekreslí. */
	static final int MIN_PIXLU = 3;
	/** Větší kruh se nekreslí. */
	private static final int MAX_PIXLU = 200_000;
	/** Hodnoty, ke kterým se posuvník přichytává. */
	static final int[] PRESETY = { 10, 25, 50, 100, 250, 500, 1000, 2000, 5000 };

	private static final Locale CS = new Locale("cs");

	private PolomerKruhu() {}

	/** Poloměr v pixelech, při přiblížení plynule; 0, když je kruh příliš malý nebo příliš velký ke kreslení. */
	static int pixlu(final int metru, final double pixluNaMetr) {
		final double r = metru * pixluNaMetr;
		if (r < MIN_PIXLU || r > MAX_PIXLU) {
			return 0;
		}
		return (int) Math.round(r);
	}

	/** Poloha posuvníku nejbližší hodnotě (v logaritmické míře, hodnota mimo presety dostane sousední pozici). */
	static int pozice(final int metru) {
		int nejblizsi = 0;
		double nejmensi = Double.MAX_VALUE;
		for (int i = 0; i < PRESETY.length; i++) {
			final double rozdil = Math.abs(Math.log((double) Math.max(metru, 1) / PRESETY[i]));
			if (rozdil < nejmensi) {
				nejmensi = rozdil;
				nejblizsi = i;
			}
		}
		return nejblizsi;
	}

	static int metru(final int pozice) {
		return PRESETY[Math.max(0, Math.min(PRESETY.length - 1, pozice))];
	}

	/** Hodnota z pole: číslo s volitelnou jednotkou m nebo km, čárka i tečka; mimo rozsah nebo nesrozumitelné vrátí 0. */
	static int parsuj(final String text) {
		if (text == null) {
			return 0;
		}
		String t = text.trim().toLowerCase(CS).replace(' ', ' ').replace(',', '.');
		double nasobek = 1;
		if (t.endsWith("km")) {
			nasobek = 1000;
			t = t.substring(0, t.length() - 2).trim();
		} else if (t.endsWith("m")) {
			t = t.substring(0, t.length() - 1).trim();
		}
		if (!t.matches("\\d+(\\.\\d+)?")) {
			return 0;
		}
		final double metru = Double.parseDouble(t) * nasobek;
		final long zaokrouhleno = Math.round(metru);
		if (zaokrouhleno < MIN_METRU || zaokrouhleno > MAX_METRU) {
			return 0;
		}
		return (int) zaokrouhleno;
	}

	/** Text do pole: do 999 m v metrech, od 1 km v kilometrech s desetinnou čárkou. */
	static String format(final int metru) {
		if (metru < 1000) {
			return metru + " m";
		}
		final NumberFormat nf = NumberFormat.getNumberInstance(CS);
		nf.setMaximumFractionDigits(3);
		nf.setGroupingUsed(false);
		return nf.format(metru / 1000.0) + " km";
	}
}
