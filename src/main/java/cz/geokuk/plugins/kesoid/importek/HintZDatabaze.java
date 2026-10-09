package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.*;
import java.util.function.Function;

/**
 * Hint keše z databáze jiného programu, načtený až při zobrazení. Popisy jsou v databázích GeoGetu, GSAKu i OpenSAKu ve stejném řádku jako dlouhý listing,
 * a jejich čtení při načítání by znamenalo přečíst celou databázi.
 */
final class HintZDatabaze {

	static final String GEOGET = "SELECT hint FROM geolist WHERE id = ?";
	static final String GSAK = "SELECT Hints FROM CacheMemo WHERE Code = ?";
	static final String OPENSAK = "SELECT encoded_hints FROM caches WHERE gc_code = ?";

	private HintZDatabaze() {}

	/** Jeden dotahovač pro všechny keše databáze, dostane kód keše; samostatný na keš by zabral desítky MB. */
	static Function<String, String> dotahovac(final File databaze, final String dotaz) {
		return kod -> nacti(databaze, dotaz, kod);
	}

	static String nacti(final File databaze, final String dotaz, final String kod) {
		try (Connection c = DatabazeJinehoProgramu.otevri(databaze); PreparedStatement ps = c.prepareStatement(dotaz)) {
			ps.setString(1, kod);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getString(1) : null;
			}
		} catch (final SQLException e) {
			throw new UncheckedIOException(new IOException("Hint keše " + kod + " nejde přečíst z databáze " + databaze + ": " + e.getMessage(), e));
		}
	}
}
