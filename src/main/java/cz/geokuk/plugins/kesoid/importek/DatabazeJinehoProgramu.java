package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Future;

import org.sqlite.BusyHandler;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteException;

/** Databáze GeoGetu, GSAKu a OpenSAKu, do kterých ten program může zrovna zapisovat. */
final class DatabazeJinehoProgramu {

	/** Import do GeoGetu, GSAKu nebo OpenSAKu drží databázi zamčenou i desítky sekund, počkáme na něj. */
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
			super("Databáze \"" + jmeno(soubor) + "\" je zamčená, GeoGet, GSAK nebo OpenSAK do ní právě zapisuje. Keše z ní se načtou, až zápis skončí.", pricina);
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

	/** Načítání keší běžící v tomto vlákně; když se zruší, na zámek se dál nečeká. */
	private static final ThreadLocal<Future<?>> NACITANI = new ThreadLocal<>();

	static void setNacitani(final Future<?> nacitani) {
		if (nacitani == null) {
			NACITANI.remove();
		} else {
			NACITANI.set(nacitani);
		}
	}

	static Connection otevri(final File soubor, final int cekaniNaZamekMs) throws SQLException {
		final SQLiteConfig config = new SQLiteConfig();
		config.setBusyTimeout(cekaniNaZamekMs);
		// Cizí databázi nesmí Geokuk založit ani změnit.
		config.setReadOnly(true);
		final Connection c = DriverManager.getConnection("jdbc:sqlite:" + soubor.getAbsolutePath(), config.toProperties());
		final Future<?> nacitani = NACITANI.get();
		if (nacitani != null && cekaniNaZamekMs > 0) {
			try {
				BusyHandler.setHandler(c, new Cekani(cekaniNaZamekMs, nacitani));
			} catch (final SQLException e) {
				c.close();
				throw e;
			}
		}
		return c;
	}

	/** Čeká na zámek jako busy timeout, ale skončí hned, když se načítání zruší. Spojení jen čte, přerušené čekání databázi nezmění. */
	private static final class Cekani extends BusyHandler {
		private static final int KROK_MS = 20;
		private final long limitNs;
		private final Future<?> nacitani;
		private long zacatek;

		Cekani(final int limitMs, final Future<?> nacitani) {
			limitNs = limitMs * 1_000_000L;
			this.nacitani = nacitani;
		}

		@Override
		protected int callback(final int pokus) {
			if (pokus == 0) {
				zacatek = System.nanoTime();
			}
			if (nacitani.isCancelled() || System.nanoTime() - zacatek >= limitNs) {
				return 0;
			}
			try {
				Thread.sleep(KROK_MS);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				return 0;
			}
			return 1;
		}
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

	/** Sloupce pro SELECT; ty, které starší verze programu v tabulce nemá, budou NULL. */
	static String vyber(final Statement statement, final String tabulka, final String[] sloupce) throws SQLException {
		final Set<String> existujici = sloupce(statement, tabulka);
		final StringBuilder sb = new StringBuilder();
		for (final String sloupec : sloupce) {
			final String[] jmenoAlias = sloupec.split(" as ");
			final String alias = jmenoAlias[jmenoAlias.length - 1];
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(existujici.contains(jmenoAlias[0]) ? tabulka + "." + jmenoAlias[0] : "NULL").append(" as ").append(alias);
		}
		return sb.toString();
	}

	/** Sloupce pro {@link SpojeneTexty#vyraz(List)}; ty, které starší verze programu v tabulce nemá, budou NULL. */
	static List<String> sloupceNeboNull(final Statement statement, final String tabulka, final String... sloupce) throws SQLException {
		final Set<String> existujici = sloupce(statement, tabulka);
		final List<String> vysledek = new ArrayList<>();
		for (final String sloupec : sloupce) {
			vysledek.add(existujici.contains(sloupec) ? tabulka + "." + sloupec : "NULL");
		}
		return vysledek;
	}

	/** Jeden sloupec textů z dotazu; s krátkým čekáním na zámek, zamčená databáze se jen nezjistí předem. */
	static java.util.List<String> jmena(final File soubor, final String dotaz) throws SQLException {
		final java.util.List<String> vysledek = new java.util.ArrayList<>();
		try (Connection c = otevri(soubor, CEKANI_PRI_ZJISTOVANI_MS); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(dotaz)) {
			while (rs.next()) {
				final String jmeno = rs.getString(1);
				if (jmeno != null) {
					vysledek.add(jmeno);
				}
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

	/** Databáze má jinou strukturu, než GeoKuk zná; přeskočí se a ostatní zdroje se načtou. */
	static class JineSchema extends RuntimeException {
		private static final long serialVersionUID = 1L;

		JineSchema(final String zprava) {
			super(zprava);
		}
	}

	/**
	 * Ověří, že tabulky mají sloupce, bez kterých keše nejde načíst. Volitelná tabulka se kontroluje, jen když v databázi je.
	 *
	 * @throws JineSchema
	 *             se srozumitelným popisem, co chybí
	 */
	static void zkontrolujSloupce(final Statement statement, final File soubor, final String program, final Map<String, List<String>> povinne, final Set<String> volitelneTabulky)
			throws SQLException {
		final List<String> chybi = new ArrayList<>();
		for (final Map.Entry<String, List<String>> e : povinne.entrySet()) {
			final Set<String> existujici = sloupce(statement, e.getKey());
			if (existujici.isEmpty() && volitelneTabulky.contains(e.getKey())) {
				continue;
			}
			for (final String sloupec : e.getValue()) {
				if (!existujici.contains(sloupec)) {
					chybi.add(e.getKey() + "." + sloupec);
				}
			}
		}
		if (!chybi.isEmpty()) {
			throw new JineSchema("Databáze " + program + " \"" + soubor + "\" má jinou strukturu, než GeoKuk zná (chybí " + String.join(", ", chybi)
					+ "), a proto se nenačetla. Zkontrolujte, že jde o databázi " + program + ".");
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
			proc = "je zamčená, GeoGet, GSAK nebo OpenSAK do ní právě zapisuje";
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
