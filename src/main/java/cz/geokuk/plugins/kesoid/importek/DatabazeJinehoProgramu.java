package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.sql.*;
import java.util.Properties;

/** Databáze GeoGetu a GSAKu, do kterých ten program může zrovna zapisovat. */
final class DatabazeJinehoProgramu {

	/** Import do GeoGetu nebo GSAKu drží databázi zamčenou i desítky sekund, počkáme na něj. */
	private static final int CEKANI_NA_ZAMEK_MS = 60_000;

	static Connection otevri(final File soubor) throws SQLException {
		final Properties vlastnosti = new Properties();
		vlastnosti.setProperty("busy_timeout", String.valueOf(CEKANI_NA_ZAMEK_MS));
		return DriverManager.getConnection("jdbc:sqlite:" + soubor.getAbsolutePath(), vlastnosti);
	}

	private DatabazeJinehoProgramu() {}
}
