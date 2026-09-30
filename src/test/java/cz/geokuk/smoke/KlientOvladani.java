package cz.geokuk.smoke;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Klient dálkového ovládání Geokuku pro testy, s malým parserem JSON. */
public class KlientOvladani {

	private final String zaklad;
	private final String token;

	public KlientOvladani(final File souborOvladani) throws IOException {
		final Properties p = new Properties();
		try (Reader r = new InputStreamReader(new FileInputStream(souborOvladani), StandardCharsets.UTF_8)) {
			p.load(r);
		}
		zaklad = "http://127.0.0.1:" + p.getProperty("port");
		token = p.getProperty("token");
	}

	public Object get(final String cesta) throws IOException {
		return zavolej("GET", cesta);
	}

	public Object post(final String cesta, final String... parametry) throws IOException {
		final StringBuilder sb = new StringBuilder(cesta);
		for (int i = 0; i + 1 < parametry.length; i += 2) {
			sb.append(i == 0 ? '?' : '&').append(URLEncoder.encode(parametry[i], "UTF-8")).append('=').append(URLEncoder.encode(parametry[i + 1], "UTF-8"));
		}
		return zavolej("POST", sb.toString());
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> stav() throws IOException {
		return (Map<String, Object>) get("/stav");
	}

	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> seznam(final String cesta) throws IOException {
		return (List<Map<String, Object>>) get(cesta);
	}

	private Object zavolej(final String metoda, final String cesta) throws IOException {
		final HttpURLConnection c = (HttpURLConnection) new URL(zaklad + cesta).openConnection(Proxy.NO_PROXY);
		c.setRequestMethod(metoda);
		c.setRequestProperty("Authorization", "Bearer " + token);
		c.setReadTimeout(60_000);
		final int kod = c.getResponseCode();
		final InputStream is = kod < 400 ? c.getInputStream() : c.getErrorStream();
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if (is != null) {
			final byte[] buf = new byte[8192];
			for (int n; (n = is.read(buf)) > 0;) {
				baos.write(buf, 0, n);
			}
			is.close();
		}
		final String telo = new String(baos.toByteArray(), StandardCharsets.UTF_8);
		if (kod != 200) {
			throw new IOException(metoda + " " + cesta + " vrátil " + kod + ": " + telo);
		}
		return new Json(telo).hodnota();
	}

	/** Parser JSON: objekty jako LinkedHashMap, pole jako ArrayList, čísla jako Double. */
	static class Json {
		private final String s;
		private int i;

		Json(final String s) {
			this.s = s;
		}

		Object hodnota() {
			mezery();
			final char c = s.charAt(i);
			if (c == '{') {
				final Map<String, Object> m = new LinkedHashMap<>();
				i++;
				mezery();
				if (s.charAt(i) == '}') {
					i++;
					return m;
				}
				while (true) {
					mezery();
					final String klic = (String) hodnota();
					mezery();
					i++; // :
					m.put(klic, hodnota());
					mezery();
					if (s.charAt(i++) == '}') {
						return m;
					}
				}
			}
			if (c == '[') {
				final List<Object> l = new ArrayList<>();
				i++;
				mezery();
				if (s.charAt(i) == ']') {
					i++;
					return l;
				}
				while (true) {
					l.add(hodnota());
					mezery();
					if (s.charAt(i++) == ']') {
						return l;
					}
				}
			}
			if (c == '"') {
				final StringBuilder sb = new StringBuilder();
				i++;
				while (s.charAt(i) != '"') {
					char z = s.charAt(i++);
					if (z == '\\') {
						z = s.charAt(i++);
						if (z == 'n') {
							z = '\n';
						} else if (z == 'r') {
							z = '\r';
						} else if (z == 't') {
							z = '\t';
						} else if (z == 'u') {
							z = (char) Integer.parseInt(s.substring(i, i + 4), 16);
							i += 4;
						}
					}
					sb.append(z);
				}
				i++;
				return sb.toString();
			}
			if (s.startsWith("true", i)) {
				i += 4;
				return true;
			}
			if (s.startsWith("false", i)) {
				i += 5;
				return false;
			}
			if (s.startsWith("null", i)) {
				i += 4;
				return null;
			}
			final int od = i;
			while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
				i++;
			}
			return Double.valueOf(s.substring(od, i));
		}

		private void mezery() {
			while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
				i++;
			}
		}
	}
}
