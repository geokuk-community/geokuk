package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.sql.*;

import org.sqlite.SQLiteConfig;

/** Databáze GeoGetu a GSAKu, do kterých ten program může zrovna zapisovat. */
final class DatabazeJinehoProgramu {

	/** Import do GeoGetu nebo GSAKu drží databázi zamčenou i desítky sekund, počkáme na něj. */
	private static final int CEKANI_NA_ZAMEK_MS = 60_000;

	static Connection otevri(final File soubor) throws SQLException {
		final SQLiteConfig config = new SQLiteConfig();
		config.setBusyTimeout(CEKANI_NA_ZAMEK_MS);
		// Cizí databázi nesmí Geokuk založit ani změnit.
		config.setReadOnly(true);
		return DriverManager.getConnection("jdbc:sqlite:" + soubor.getAbsolutePath(), config.toProperties());
	}

	private DatabazeJinehoProgramu() {}
}
