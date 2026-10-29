package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;

import org.sqlite.SQLiteConfig;

/** Odhad, kolik místa v cache zabírají dlaždice zastaralých klíčů offline map; platí pro jednu sadu ponechaných klíčů. */
public final class StaraData {

	/** Bez zastaralých dlaždic. */
	public static final StaraData ZADNA = new StaraData(0, 0, "");

	private final long dlazdic;
	private final long bajtu;
	/** Otisk sady ponechaných klíčů, pro kterou odhad platí. */
	private final String plan;

	public StaraData(final long dlazdic, final long bajtu, final String plan) {
		this.dlazdic = dlazdic;
		this.bajtu = bajtu;
		this.plan = plan;
	}

	public long dlazdic() {
		return dlazdic;
	}

	public long bajtu() {
		return bajtu;
	}

	/** Otisk sady ponechaných klíčů; při jiné sadě je dřívější odhad neplatný. */
	static String otiskPlanu(final Set<String> ponechat) {
		final CRC32 crc = new CRC32();
		crc.update(String.join(",", new TreeSet<>(ponechat)).getBytes(StandardCharsets.UTF_8));
		return String.format("%08x", crc.getValue());
	}

	/** Zapamatovaný odhad, jen když platí pro tento plán; jinak null. */
	static StaraData nacti(final File soubor, final String plan) {
		try {
			if (!soubor.isFile()) {
				return null;
			}
			final List<String> radky = Files.readAllLines(soubor.toPath(), StandardCharsets.UTF_8);
			if (radky.size() != 3 || !radky.get(0).equals("plan=" + plan)) {
				return null;
			}
			return new StaraData(Long.parseLong(radky.get(1).replace("dlazdic=", "")), Long.parseLong(radky.get(2).replace("bajtu=", "")), plan);
		} catch (final IOException | RuntimeException e) {
			return null;
		}
	}

	/**
	 * Odhad podle počtu dlaždic jednotlivých typů ({@code COUNT(*) GROUP BY s} se čte z indexu, bez obrázků, a trvá zlomek sekundy; sdílený zámek souboru se drží
	 * jen po tu dobu) krát průměrná velikost dlaždice v souboru. Přesný objem se ukáže při kopii v Optimalizaci.
	 */
	static StaraData spocti(final File db, final Set<String> ponechat) throws SQLException {
		final StavSouboruCache stav = StavSouboruCache.precti(db);
		final SQLiteConfig config = new SQLiteConfig();
		config.setReadOnly(true);
		config.setBusyTimeout(30_000);
		long celkem = 0;
		long dlazdic = 0;
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath(), config.toProperties());
				Statement st = c.createStatement();
				ResultSet r = st.executeQuery("SELECT s, COUNT(*) FROM tiles GROUP BY s")) {
			while (r.next()) {
				celkem += r.getLong(2);
				if (UklidOfflineCache.jeZastarala(r.getString(1), ponechat)) {
					dlazdic += r.getLong(2);
				}
			}
		}
		final long bajtu = celkem == 0 || stav == null ? 0 : (long) (stav.zivaData() / (double) celkem * dlazdic);
		return new StaraData(dlazdic, bajtu, otiskPlanu(ponechat));
	}

	void uloz(final File soubor) {
		try {
			Files.write(soubor.toPath(), Arrays.asList("plan=" + plan, "dlazdic=" + dlazdic, "bajtu=" + bajtu), StandardCharsets.UTF_8);
		} catch (final IOException e) {
			// Odhad se příště spočítá znovu.
		}
	}
}
