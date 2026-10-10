package cz.geokuk.plugins.kesoid.importek;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;

/**
 * Textové sloupce řádku přečtené jedním voláním JDBC. Každé čtení sloupce přes sqlite-jdbc stojí víc než rozdělení textu v Javě, proto databáze texty
 * spojí do jednoho sloupce a ten se tu rozdělí. Stejné sloupce jsou v dotazu i zvlášť a čtou se, když nějaký text obsahuje oddělovač.
 */
final class SpojeneTexty {

	/** Začátek textu. */
	private static final char TEXT = 30;
	/** Sloupec je NULL. */
	private static final char NIC = 31;

	/** Jen pro testy: čte vždy po sloupcích. */
	static boolean poSloupcich;
	/** Jen pro testy: řádky, které se kvůli oddělovači v textu četly po sloupcích. */
	static final LongAdder ZALOZNI_RADKY = new LongAdder();

	private final int sloupec;
	private final int[] zalozni;
	private final int[] poradi;
	private final String[] hodnoty;
	private final Map<String, Integer> podleJmena = new HashMap<>();

	/**
	 * @param sloupec
	 *            index sloupce s výrazem {@link #vyraz(List)}
	 * @param zalozni
	 *            indexy týchž sloupců zvlášť, ve stejném pořadí jako ve výrazu
	 */
	SpojeneTexty(final int sloupec, final int... zalozni) {
		this.sloupec = sloupec;
		this.zalozni = zalozni.clone();
		poradi = new int[Arrays.stream(zalozni).max().orElse(0) + 1];
		Arrays.fill(poradi, -1);
		for (int i = 0; i < zalozni.length; i++) {
			poradi[zalozni[i]] = i;
		}
		hodnoty = new String[zalozni.length];
	}

	/** Spojený sloupec je v dotazu poslední, záložní se najdou podle jména a texty se pak čtou {@link #get(String)}. */
	static SpojeneTexty posledniSloupec(final ResultSet rs, final String... jmena) throws SQLException {
		final int[] zalozni = new int[jmena.length];
		for (int i = 0; i < jmena.length; i++) {
			zalozni[i] = rs.findColumn(jmena[i]);
		}
		final SpojeneTexty texty = new SpojeneTexty(rs.getMetaData().getColumnCount(), zalozni);
		for (int i = 0; i < jmena.length; i++) {
			texty.podleJmena.put(jmena[i], zalozni[i]);
		}
		return texty;
	}

	/** Výraz SQL, který spojí sloupce do jednoho textu: každý jako char(30) a text, NULL jako char(31). */
	static String vyraz(final List<String> sloupce) {
		final StringBuilder sb = new StringBuilder();
		for (final String s : sloupce) {
			if (sb.length() > 0) {
				sb.append(" || ");
			}
			sb.append("ifnull(char(30) || ").append(s).append(", char(31))");
		}
		return sb.length() == 0 ? "''" : sb.toString();
	}

	/** Načte texty aktuálního řádku. */
	void nacti(final ResultSet rs) throws SQLException {
		if (poSloupcich || !rozdel(rs.getString(sloupec))) {
			ZALOZNI_RADKY.increment();
			for (int i = 0; i < zalozni.length; i++) {
				hodnoty[i] = rs.getString(zalozni[i]);
			}
		}
	}

	/** Text ze sloupce {@code zaloznisloupec} aktuálního řádku. */
	String get(final int zaloznisloupec) {
		return hodnoty[poradi[zaloznisloupec]];
	}

	/** Text ze sloupce {@code jmeno} aktuálního řádku, pro {@link #posledniSloupec(ResultSet, String...)}. */
	String get(final String jmeno) {
		return get(podleJmena.get(jmeno));
	}

	/** Oddělovač uvnitř textu přidá pole navíc, takže přesně tolik polí, kolik je sloupců, znamená, že žádný text oddělovač neobsahuje. */
	private boolean rozdel(final String s) {
		if (s == null) {
			return false;
		}
		int z = 0;
		for (int i = 0; i < hodnoty.length; i++) {
			if (z >= s.length()) {
				return false;
			}
			final char c = s.charAt(z);
			if (c == NIC) {
				hodnoty[i] = null;
				z++;
			} else if (c == TEXT) {
				int konec = z + 1;
				while (konec < s.length() && s.charAt(konec) != TEXT && s.charAt(konec) != NIC) {
					konec++;
				}
				hodnoty[i] = s.substring(z + 1, konec);
				z = konec;
			} else {
				return false;
			}
		}
		return z == s.length();
	}
}
