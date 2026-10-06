package cz.geokuk.smoke;

import java.io.File;
import java.sql.*;
import java.util.Random;

/** Databáze ve stylu GSAKu ({@code sqlite.db3}: Caches, CacheMemo, Waypoints, Custom) s kešemi po Česku. */
public class SyntetickaDatabazeGsaku {

	private static final String[] SLOVA = "les louka kopec kriz skala jeskyne pramen zamek rybnik most cesta mlyn kaple potok hrad studanka vyhlidka".split(" ");
	private static final String[] TYPY = { "T", "U", "M", "E", "B", "V" };
	private static final String[] NADOBY = { "Micro", "Small", "Regular", "Large", "Other" };
	private static final String[] TYPY_WPT = { "Parking Area", "Final Location", "Stages of a Multicache", "Reference Point", "Trailhead" };
	private static final String[] PREFIXY = { "PK", "FN", "S1", "RP", "TH" };

	/** Zapíše {@code kesi} keší a {@code waypointu} přídavných waypointů, vrátí celkový počet waypointů. */
	public static int zapis(final File soubor, final int kesi, final int waypointu) throws Exception {
		final Random r = new Random(11);
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + soubor); Statement s = c.createStatement()) {
			for (final String t : new String[] { "Attributes", "CacheImages", "Corrected", "Filter", "Ignore", "LogImages", "LogMemo", "Logs" }) {
				s.execute("CREATE TABLE " + t + " (Code TEXT)");
			}
			s.execute("CREATE TABLE Caches (Code TEXT, Name TEXT, PlacedBy TEXT, Archived INTEGER, CacheType TEXT, Container TEXT, County TEXT, Country TEXT, Difficulty TEXT,"
					+ " FoundByMeDate TEXT, Latitude REAL, Longitude REAL, OwnerId INTEGER, OwnerName TEXT, PlacedDate TEXT, State TEXT, TempDisabled INTEGER, Terrain TEXT,"
					+ " LatOriginal REAL, LonOriginal REAL, Elevation INTEGER, FavPoints INTEGER)");
			s.execute("CREATE TABLE CacheMemo (Code TEXT, ShortDescription TEXT, Hints TEXT)");
			s.execute("CREATE TABLE Waypoints (cParent TEXT, cCode TEXT, cPrefix TEXT, cName TEXT, cType TEXT, cLat REAL, cLon REAL, cByuser INTEGER, cDate TEXT, cFlag INTEGER, sB1 INTEGER)");
			s.execute("CREATE TABLE Custom (Code TEXT, barva TEXT)");
			c.setAutoCommit(false);
			try (PreparedStatement k = c.prepareStatement("INSERT INTO Caches VALUES (?,?,?,0,?,?,'','Czech Republic',?,?,?,?,?,?,'2012-05-07','Jihomoravsky kraj',0,?,0,0,?,?)");
					PreparedStatement m = c.prepareStatement("INSERT INTO CacheMemo VALUES (?,?,?)");
					PreparedStatement v = c.prepareStatement("INSERT INTO Custom VALUES (?,?)")) {
				for (int i = 0; i < kesi; i++) {
					final String kod = kod(i);
					final String autor = "owner" + i % 5000;
					k.setString(1, kod);
					k.setString(2, veta(r, 4));
					k.setString(3, autor);
					k.setString(4, TYPY[r.nextInt(TYPY.length)]);
					k.setString(5, NADOBY[r.nextInt(NADOBY.length)]);
					k.setString(6, String.valueOf(1 + r.nextInt(5)));
					k.setString(7, i % 3 == 0 ? "2020-01-01" : "");
					k.setDouble(8, 48.6 + r.nextDouble() * 2.4);
					k.setDouble(9, 12.1 + r.nextDouble() * 6.7);
					k.setInt(10, i % 5000);
					k.setString(11, autor);
					k.setString(12, String.valueOf(1 + r.nextInt(5)));
					k.setInt(13, 200 + r.nextInt(800));
					k.setInt(14, r.nextInt(50));
					k.addBatch();
					m.setString(1, kod);
					m.setString(2, veta(r, 50));
					m.setString(3, veta(r, 10));
					m.addBatch();
					if (i % 10 == 0) {
						v.setString(1, kod);
						v.setString(2, "modra");
						v.addBatch();
					}
					if (i % 20_000 == 19_999) {
						k.executeBatch();
						m.executeBatch();
						v.executeBatch();
					}
				}
				k.executeBatch();
				m.executeBatch();
				v.executeBatch();
			}
			try (PreparedStatement w = c.prepareStatement("INSERT INTO Waypoints VALUES (?,?,?,?,?,?,?,0,'',0,0)")) {
				for (int j = 0; j < waypointu; j++) {
					final String rodic = kod(j % kesi);
					final String prefix = PREFIXY[j / kesi % 5];
					w.setString(1, rodic);
					w.setString(2, prefix + rodic.substring(2));
					w.setString(3, prefix);
					w.setString(4, veta(r, 3));
					w.setString(5, TYPY_WPT[j % 5]);
					w.setDouble(6, 48.6 + r.nextDouble() * 2.4);
					w.setDouble(7, 12.1 + r.nextDouble() * 6.7);
					w.addBatch();
					if (j % 20_000 == 19_999) {
						w.executeBatch();
					}
				}
				w.executeBatch();
			}
			c.commit();
		}
		return kesi + waypointu;
	}

	private static String kod(int i) {
		final String abc = "0123456789ABCDEFGHJKMNPQRTVWXYZ";
		final StringBuilder sb = new StringBuilder();
		do {
			sb.insert(0, abc.charAt(i % 31));
			i /= 31;
		} while (i > 0);
		while (sb.length() < 4) {
			sb.insert(0, '0');
		}
		return "GC" + sb;
	}

	private static String veta(final Random r, final int slov) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < slov; i++) {
			sb.append(i == 0 ? "" : " ").append(SLOVA[r.nextInt(SLOVA.length)]);
		}
		return sb.toString();
	}
}
