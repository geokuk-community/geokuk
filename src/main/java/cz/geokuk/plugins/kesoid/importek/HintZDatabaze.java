package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.*;

import cz.geokuk.plugins.kesoid.kind.kes.DotahovacZDatabaze;

/**
 * Hint keše z databáze jiného programu, načtený až při zobrazení. Popisy jsou v databázích GeoGetu, GSAKu i OpenSAKu ve stejném řádku jako dlouhý listing,
 * a jejich čtení při načítání by znamenalo přečíst celou databázi.
 */
final class HintZDatabaze {

	static final String GEOGET = "SELECT hint FROM geolist WHERE id = ?";
	static final String GSAK = "SELECT Hints FROM CacheMemo WHERE Code = ?";
	static final String OPENSAK = "SELECT encoded_hints FROM caches WHERE gc_code = ?";

	/** Nadmořská výška keše v databázi GeoGetu: tag kategorie Elevation. */
	static final String GEOGET_VYSKA = "SELECT v.value FROM geotag t JOIN geotagcategory c ON t.ptrkat = c.key JOIN geotagvalue v ON t.ptrvalue = v.key WHERE t.id = ? AND c.value = 'Elevation'";

	private HintZDatabaze() {}

	/** Jeden dotahovač pro všechny keše databáze, dostane kód keše; samostatný na keš by zabral desítky MB. */
	static DotahovacZDatabaze dotahovac(final File databaze, final String dotaz) {
		return dotahovac(databaze, dotaz, null);
	}

	/** Dotahovač, který čte i nadmořskou výšku dotazem {@code dotazVyska} (jeden parametr, kód keše). */
	static DotahovacZDatabaze dotahovac(final File databaze, final String dotaz, final String dotazVyska) {
		return new DotahovacZDatabaze() {
			@Override
			public String apply(final String kod) {
				return nacti(databaze, dotaz, kod, DatabazeJinehoProgramu.CEKANI_NA_ZAMEK_MS);
			}

			@Override
			public int nadmorskaVyska(final String kod) {
				if (dotazVyska == null) {
					return 0;
				}
				final String hodnota = nacti(databaze, dotazVyska, kod, DatabazeJinehoProgramu.CEKANI_PRI_ZJISTOVANI_MS);
				try {
					return hodnota == null ? 0 : Integer.parseInt(hodnota.trim());
				} catch (final NumberFormatException e) {
					return 0;
				}
			}

			@Override
			public boolean ctouVyskuAzPriZobrazeni() {
				return dotazVyska != null;
			}
		};
	}

	static String nacti(final File databaze, final String dotaz, final String kod, final int cekaniNaZamekMs) {
		try (Connection c = DatabazeJinehoProgramu.otevri(databaze, cekaniNaZamekMs); PreparedStatement ps = c.prepareStatement(dotaz)) {
			ps.setString(1, kod);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getString(1) : null;
			}
		} catch (final SQLException e) {
			throw new UncheckedIOException(new IOException("Hint keše " + kod + " nejde přečíst z databáze " + databaze + ": " + e.getMessage(), e));
		}
	}
}
