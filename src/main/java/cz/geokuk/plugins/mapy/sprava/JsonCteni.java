package cz.geokuk.plugins.mapy.sprava;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Malá čtečka JSON (vestavěný katalog map, popisy vydání): objekt → Map, pole → List, číslo → Double, text, true/false, null. */
public final class JsonCteni {

	/** Hloubka vnoření; hlubší JSON je chybný, ne přetečení zásobníku. */
	private static final int NEJVETSI_HLOUBKA = 64;

	private final String text;
	private int pozice;
	private int hloubka;

	private JsonCteni(final String text) {
		this.text = text;
	}

	public static Object precti(final String text) throws IOException {
		final JsonCteni c = new JsonCteni(text);
		final Object hodnota = c.hodnota();
		c.mezery();
		if (c.pozice != text.length()) {
			throw c.chyba("za hodnotou je další text");
		}
		return hodnota;
	}

	private Object hodnota() throws IOException {
		mezery();
		if (pozice >= text.length()) {
			throw chyba("chybí hodnota");
		}
		final char z = text.charAt(pozice);
		switch (z) {
		case '{':
		case '[':
			if (++hloubka > NEJVETSI_HLOUBKA) {
				throw chyba("příliš hluboké vnoření");
			}
			final Object o = z == '{' ? objekt() : pole();
			hloubka--;
			return o;
		case '"':
			return retezec();
		case 't':
			return slovo("true", Boolean.TRUE);
		case 'f':
			return slovo("false", Boolean.FALSE);
		case 'n':
			return slovo("null", null);
		default:
			return cislo();
		}
	}

	private Map<String, Object> objekt() throws IOException {
		final Map<String, Object> m = new LinkedHashMap<>();
		pozice++;
		mezery();
		if (znak() == '}') {
			pozice++;
			return m;
		}
		for (;;) {
			mezery();
			if (znak() != '"') {
				throw chyba("čekám klíč");
			}
			final String klic = retezec();
			mezery();
			ocekavej(':');
			m.put(klic, hodnota());
			mezery();
			if (znak() == ',') {
				pozice++;
			} else {
				ocekavej('}');
				return m;
			}
		}
	}

	private List<Object> pole() throws IOException {
		final List<Object> l = new ArrayList<>();
		pozice++;
		mezery();
		if (znak() == ']') {
			pozice++;
			return l;
		}
		for (;;) {
			l.add(hodnota());
			mezery();
			if (znak() == ',') {
				pozice++;
			} else {
				ocekavej(']');
				return l;
			}
		}
	}

	private String retezec() throws IOException {
		pozice++;
		final StringBuilder sb = new StringBuilder();
		for (;;) {
			if (pozice >= text.length()) {
				throw chyba("neukončený text");
			}
			final char z = text.charAt(pozice++);
			if (z == '"') {
				return sb.toString();
			}
			if (z != '\\') {
				sb.append(z);
				continue;
			}
			if (pozice >= text.length()) {
				throw chyba("neukončený text");
			}
			final char e = text.charAt(pozice++);
			switch (e) {
			case 'n':
				sb.append('\n');
				break;
			case 't':
				sb.append('\t');
				break;
			case 'r':
				sb.append('\r');
				break;
			case 'b':
				sb.append('\b');
				break;
			case 'f':
				sb.append('\f');
				break;
			case '"':
			case '\\':
			case '/':
				sb.append(e);
				break;
			case 'u':
				if (pozice + 4 > text.length()) {
					throw chyba("neplatné \\u");
				}
				int kod = 0;
				for (int i = 0; i < 4; i++) {
					final int cislice = Character.digit(text.charAt(pozice++), 16);
					if (cislice < 0) {
						throw chyba("neplatné \\u");
					}
					kod = kod * 16 + cislice;
				}
				sb.append((char) kod);
				break;
			default:
				throw chyba("neplatný escape \\" + e);
			}
		}
	}

	private Double cislo() throws IOException {
		final int zacatek = pozice;
		while (pozice < text.length() && "+-0123456789.eE".indexOf(text.charAt(pozice)) >= 0) {
			pozice++;
		}
		try {
			return Double.valueOf(text.substring(zacatek, pozice));
		} catch (final NumberFormatException e) {
			pozice = zacatek;
			throw chyba("neplatná hodnota");
		}
	}

	private Object slovo(final String slovo, final Object hodnota) throws IOException {
		if (!text.startsWith(slovo, pozice)) {
			throw chyba("neplatná hodnota");
		}
		pozice += slovo.length();
		return hodnota;
	}

	private void ocekavej(final char z) throws IOException {
		if (znak() != z) {
			throw chyba("čekám " + z);
		}
		pozice++;
	}

	private char znak() {
		return pozice < text.length() ? text.charAt(pozice) : 0;
	}

	private void mezery() {
		while (pozice < text.length() && Character.isWhitespace(text.charAt(pozice))) {
			pozice++;
		}
	}

	private IOException chyba(final String co) {
		return new IOException("Chybný JSON na pozici " + pozice + ": " + co);
	}
}
