package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Databáze OpenSAKu se schématem podle souboru opensak-schema.txt (stejný soubor hlídá změny schématu v OpenSAKu). */
final class OpensakTestDb {

	private static final Set<String> CISELNE = new HashSet<>(Arrays.asList("latitude", "longitude", "corrected_lat", "corrected_lon", "difficulty", "terrain", "elevation"));

	private OpensakTestDb() {}

	/** Verze schématu z prvního řádku souboru. */
	static int verze() throws IOException {
		return Integer.parseInt(radky().get(0).replace("verze", "").trim());
	}

	/** Tabulka → sloupce podle souboru. */
	static Map<String, List<String>> schema() throws IOException {
		final Map<String, List<String>> tabulky = new LinkedHashMap<>();
		for (final String radek : radky().subList(1, radky().size())) {
			final String[] ts = radek.trim().split("\\s+");
			if (ts.length == 2) {
				tabulky.computeIfAbsent(ts[0], k -> new ArrayList<>()).add(ts[1]);
			}
		}
		return tabulky;
	}

	/**
	 * Založí databázi.
	 *
	 * @param bez
	 *            sloupce „tabulka.sloupec“ nebo celé tabulky, které v databázi nebudou
	 */
	static void zaloz(final File db, final int userVersion, final String... bez) throws Exception {
		final Set<String> vynechat = new HashSet<>(Arrays.asList(bez));
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			for (final Map.Entry<String, List<String>> e : schema().entrySet()) {
				if (vynechat.contains(e.getKey())) {
					continue;
				}
				final StringBuilder sb = new StringBuilder();
				for (final String sloupec : e.getValue()) {
					if (vynechat.contains(e.getKey() + "." + sloupec)) {
						continue;
					}
					sb.append(sb.length() == 0 ? "" : ", ").append(sloupec).append(CISELNE.contains(sloupec) ? " REAL" : " TEXT");
				}
				s.execute("CREATE TABLE " + e.getKey() + " (" + sb + ")");
			}
			s.execute("PRAGMA user_version = " + userVersion);
		}
	}

	/** Vloží řádek; hodnoty jsou dvojice sloupec, hodnota. */
	static void vloz(final File db, final String tabulka, final Object... hodnoty) throws SQLException {
		final StringBuilder sloupce = new StringBuilder();
		final StringBuilder otazniky = new StringBuilder();
		for (int i = 0; i < hodnoty.length; i += 2) {
			sloupce.append(i == 0 ? "" : ", ").append(hodnoty[i]);
			otazniky.append(i == 0 ? "?" : ", ?");
		}
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
				PreparedStatement ps = c.prepareStatement("INSERT INTO " + tabulka + " (" + sloupce + ") VALUES (" + otazniky + ")")) {
			for (int i = 1; i < hodnoty.length; i += 2) {
				ps.setObject(i / 2 + 1, hodnoty[i]);
			}
			ps.executeUpdate();
		}
	}

	/** Jedna aktivní keš, jak ji OpenSAK uloží z GPX. */
	static void vlozKes(final File db, final int id, final String kod, final String typ, final double lat, final double lon, final Object... dalsi) throws SQLException {
		final List<Object> h = new ArrayList<>(Arrays.asList("id", id, "gc_code", kod, "name", "Keš " + kod, "cache_type", typ, "latitude", lat, "longitude", lon, "available", 1,
				"archived", 0, "found", 0));
		for (int i = 0; i < dalsi.length; i += 2) {
			final int index = h.indexOf(dalsi[i]);
			if (index >= 0 && index % 2 == 0) {
				h.set(index + 1, dalsi[i + 1]);
			} else {
				h.add(dalsi[i]);
				h.add(dalsi[i + 1]);
			}
		}
		vloz(db, "caches", h.toArray());
	}

	private static List<String> radky() throws IOException {
		try (InputStream in = OpensakTestDb.class.getResourceAsStream("/opensak-schema.txt")) {
			final List<String> vysledek = new ArrayList<>();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				for (String radek = r.readLine(); radek != null; radek = r.readLine()) {
					if (!radek.trim().isEmpty()) {
						vysledek.add(radek);
					}
				}
			}
			return vysledek;
		}
	}
}
