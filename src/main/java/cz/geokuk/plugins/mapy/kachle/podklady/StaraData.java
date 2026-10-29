package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
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

	/** Počet řádků jedné dávky; sdílený zámek souboru se drží jen po dobu jedné dávky. */
	static final int DAVKA = 500;

	/** Pauza mezi dávkami, aby čtení a zápis dlaždic běžely. */
	static final long PAUZA_MS = 10;

	/**
	 * Projde tabulku po dávkách podle rowid, každou v samostatném spojení jen ke čtení, takže soubor je zamčený (sdíleným zámkem) jen po dobu jedné dávky
	 * a zápis dlaždic nečeká déle. Velikost obrázku se čte z hlavičky záznamu, ne z dat.
	 *
	 * @param meziDavkami
	 *            volá se při uvolněném souboru po každé dávce
	 * @return null, když počítání zrušil {@code zruseno}
	 */
	static StaraData spocti(final File db, final Set<String> ponechat, final int davka, final BooleanSupplier zruseno, final Runnable meziDavkami) throws SQLException {
		final String plan = otiskPlanu(ponechat);
		final SQLiteConfig config = new SQLiteConfig();
		config.setReadOnly(true);
		config.setBusyTimeout(30_000);
		final String url = "jdbc:sqlite:" + db.getAbsolutePath();
		long dlazdic = 0;
		long bajtu = 0;
		long posledni = Long.MIN_VALUE;
		while (true) {
			if (zruseno.getAsBoolean()) {
				return null;
			}
			int radku = 0;
			try (Connection c = DriverManager.getConnection(url, config.toProperties()); PreparedStatement st = c.prepareStatement(
					"SELECT rowid, s, length(image) FROM tiles WHERE rowid > ? ORDER BY rowid LIMIT ?")) {
				st.setLong(1, posledni);
				st.setInt(2, davka);
				try (ResultSet r = st.executeQuery()) {
					while (r.next()) {
						radku++;
						posledni = r.getLong(1);
						if (UklidOfflineCache.jeZastarala(r.getString(2), ponechat)) {
							dlazdic++;
							bajtu += r.getLong(3);
						}
					}
				}
			}
			if (radku < davka) {
				return new StaraData(dlazdic, bajtu, plan);
			}
			meziDavkami.run();
		}
	}

	void uloz(final File soubor) {
		try {
			Files.write(soubor.toPath(), Arrays.asList("plan=" + plan, "dlazdic=" + dlazdic, "bajtu=" + bajtu), StandardCharsets.UTF_8);
		} catch (final IOException e) {
			// Odhad se příště spočítá znovu.
		}
	}
}
