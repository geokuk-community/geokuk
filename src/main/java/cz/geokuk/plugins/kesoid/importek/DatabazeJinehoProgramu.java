package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.sql.*;
import java.util.Set;
import java.util.TreeSet;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteException;

/** Databáze GeoGetu a GSAKu, do kterých ten program může zrovna zapisovat. */
final class DatabazeJinehoProgramu {

	/** Import do GeoGetu nebo GSAKu drží databázi zamčenou i desítky sekund, počkáme na něj. */
	static final int CEKANI_NA_ZAMEK_MS = 60_000;

	/** Při zjišťování, co je soubor zač, dlouho nečekáme: zamčená databáze by zdržela načtení všech ostatních zdrojů. */
	static final int CEKANI_PRI_ZJISTOVANI_MS = 2_000;

	private static final int SQLITE_CORRUPT = 11;
	private static final int SQLITE_NOTADB = 26;
	private static final int SQLITE_BUSY = 5;
	private static final int SQLITE_LOCKED = 6;
	private static final int SQLITE_READONLY = 8;
	private static final int SQLITE_IOERR = 10;
	private static final int SQLITE_CANTOPEN = 14;
	/** Rozšířený kód: databáze má rozepsaný zápis (hot journal), jen pro čtení ho nejde vrátit. */
	private static final int SQLITE_READONLY_ROLLBACK = 776;

	/** Databázi drží zamčenou jiný program déle, než na něj čekáme. Načte se při dalším pokusu. */
	static class Zamcena extends RuntimeException {
		private static final long serialVersionUID = 1L;

		Zamcena(final File soubor, final Throwable pricina) {
			super("Databáze \"" + jmeno(soubor) + "\" je zamčená, GeoGet nebo GSAK do ní právě zapisuje. Keše z ní se načtou, až zápis skončí.", pricina);
		}
	}

	/** Databáze GSAKu se jmenují všechny sqlite.db3, uživatel je zná podle jména složky. */
	static String jmeno(final File soubor) {
		final File slozka = soubor.getParentFile();
		return "sqlite.db3".equalsIgnoreCase(soubor.getName()) && slozka != null ? slozka.getName() : soubor.getName();
	}

	static Connection otevri(final File soubor) throws SQLException {
		return otevri(soubor, CEKANI_NA_ZAMEK_MS);
	}

	static Connection otevri(final File soubor, final int cekaniNaZamekMs) throws SQLException {
		final SQLiteConfig config = new SQLiteConfig();
		config.setBusyTimeout(cekaniNaZamekMs);
		// Cizí databázi nesmí Geokuk založit ani změnit.
		config.setReadOnly(true);
		return DriverManager.getConnection("jdbc:sqlite:" + soubor.getAbsolutePath(), config.toProperties());
	}

	/** Jména sloupců tabulky bez ohledu na velikost písmen, prázdné, když tabulka není. */
	static Set<String> sloupce(final Statement statement, final String tabulka) throws SQLException {
		final Set<String> vysledek = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		try (ResultSet rs = statement.executeQuery("PRAGMA table_info(" + tabulka + ")")) {
			while (rs.next()) {
				vysledek.add(rs.getString("name"));
			}
		}
		return vysledek;
	}

	/** Zda databázi pořád drží zamčenou jiný program, bez čekání. */
	static boolean jeZamcena(final File soubor) {
		try (Connection c = otevri(soubor, 0); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM sqlite_master")) {
			return false;
		} catch (final SQLException e) {
			return jeZamcena(e);
		}
	}

	static boolean jeZamcena(final Throwable chyba) {
		final int kod = kodSqlite(chyba);
		return kod == SQLITE_BUSY || kod == SQLITE_LOCKED;
	}

	/** Primární kód chyby SQLite z řetězce příčin, -1 když tam žádná není. */
	static int kodSqlite(final Throwable chyba) {
		final SQLiteException e = chybaSqlite(chyba);
		return e == null ? -1 : e.getResultCode().code & 0xff;
	}

	private static SQLiteException chybaSqlite(final Throwable chyba) {
		for (Throwable t = chyba; t != null; t = t.getCause()) {
			if (t instanceof SQLiteException) {
				return (SQLiteException) t;
			}
		}
		return null;
	}

	/** Srozumitelný popis, proč databázi nejde přečíst, nebo null, když nejde o chybu SQLite. */
	static String popisChyby(final File soubor, final Throwable chyba) {
		final String proc;
		final SQLiteException sqlite = chybaSqlite(chyba);
		if (sqlite != null && sqlite.getResultCode().code == SQLITE_READONLY_ROLLBACK) {
			return "Databáze \"" + soubor + "\" má nedokončený zápis z GeoGetu nebo GSAKu. Otevřete ji v GeoGetu nebo GSAKu, ten ji uvede do pořádku, a GeoKuk ji pak načte.";
		}
		switch (kodSqlite(chyba)) {
		case -1:
			return null;
		case SQLITE_BUSY:
		case SQLITE_LOCKED:
			proc = "je zamčená, GeoGet nebo GSAK do ní právě zapisuje";
			break;
		case SQLITE_CORRUPT:
			proc = "je poškozená, opravte ji údržbou databáze v GeoGetu nebo GSAKu";
			break;
		case SQLITE_NOTADB:
			proc = "není databáze nebo je poškozená";
			break;
		case SQLITE_READONLY:
		case SQLITE_IOERR:
		case SQLITE_CANTOPEN:
			proc = "nejde otevřít, zkontrolujte, jestli soubor existuje a jestli smíte zapisovat do jeho složky";
			break;
		default:
			proc = "nejde přečíst";
		}
		return "Databáze \"" + soubor + "\" " + proc + ". (" + chybaSqlite(chyba).getMessage() + ")";
	}

	private DatabazeJinehoProgramu() {}
}
