package cz.geokuk.smoke;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Random;
import java.util.zip.DeflaterOutputStream;

/** Databáze ve stylu GeoGetu (geocache, geolist, waypoint, tagy) s kešemi po Česku. */
public class SyntetickaDatabazeGeogetu {

	private static final String[] SLOVA = "les louka kopec kriz skala jeskyne pramen zamek rybnik most cesta mlyn kaple potok hrad studanka vyhlidka".split(" ");
	private static final String[] TYPY = { "Traditional Cache", "Unknown Cache", "Multi-cache", "Earthcache", "Letterbox Hybrid", "Virtual Cache" };
	private static final String[] NADOBY = { "Micro", "Small", "Regular", "Large", "Other" };
	private static final String[] TYPY_WPT = { "Parking Area", "Final Location", "Stages of a Multicache", "Reference Point", "Trailhead" };
	private static final String[] PREFIXY = { "PK", "FN", "S1", "RP", "TH" };

	/** Zapíše {@code kesi} keší a {@code waypointu} přídavných waypointů, vrátí celkový počet waypointů. */
	public static int zapis(final File soubor, final int kesi, final int waypointu) throws Exception {
		final Random r = new Random(7);
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + soubor); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE geocache (id TEXT, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
					+ " cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT, dtfound INTEGER)");
			s.execute("CREATE TABLE geolist (id TEXT, shortdesc BLOB, hint TEXT)");
			s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, prefixid TEXT, wpttype TEXT, name TEXT)");
			s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)");
			s.execute("CREATE TABLE geotagcategory (key INTEGER, value TEXT)");
			s.execute("CREATE TABLE geotagvalue (key INTEGER, value TEXT)");
			s.execute("INSERT INTO geotagcategory VALUES (1, 'Hodnoceni'), (2, 'Znamka'), (3, 'favorites'), (4, 'Elevation'), (5, 'BestOf'), (6, 'Hodnoceni-Pocet')");
			c.setAutoCommit(false);
			try (PreparedStatement v = c.prepareStatement("INSERT INTO geotagvalue VALUES (?,?)")) {
				for (int k = 1; k <= 200; k++) {
					v.setInt(1, k);
					v.setString(2, k <= 100 ? k + "%" : String.valueOf(k * 13));
					v.addBatch();
				}
				v.executeBatch();
			}
			try (PreparedStatement g = c.prepareStatement("INSERT INTO geocache VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
					PreparedStatement l = c.prepareStatement("INSERT INTO geolist VALUES (?,?,?)");
					PreparedStatement t = c.prepareStatement("INSERT INTO geotag VALUES (?,?,?)")) {
				for (int i = 0; i < kesi; i++) {
					final String kod = kod(i);
					g.setString(1, kod);
					g.setDouble(2, 48.6 + r.nextDouble() * 2.4);
					g.setDouble(3, 12.1 + r.nextDouble() * 6.7);
					g.setString(4, veta(r, 4));
					g.setString(5, "owner" + i % 5000);
					g.setString(6, TYPY[r.nextInt(TYPY.length)]);
					g.setString(7, NADOBY[r.nextInt(NADOBY.length)]);
					g.setString(8, String.valueOf(1 + r.nextInt(5)));
					g.setString(9, String.valueOf(1 + r.nextInt(5)));
					g.setInt(10, 0);
					g.setInt(11, i % 5000);
					g.setInt(12, 20120507);
					g.setString(13, "Czech Republic");
					g.setString(14, "Jihomoravsky kraj");
					g.setInt(15, i % 3 == 0 ? 20200101 : 0);
					g.addBatch();
					l.setString(1, kod);
					l.setBytes(2, zabal(veta(r, 50)));
					l.setString(3, veta(r, 10));
					l.addBatch();
					for (final int[] tag : new int[][] { { 1, 1 + r.nextInt(100) }, { 3, 101 + r.nextInt(100) }, { 4, 101 + r.nextInt(100) } }) {
						t.setString(1, kod);
						t.setInt(2, tag[0]);
						t.setInt(3, tag[1]);
						t.addBatch();
					}
					if (i % 20_000 == 19_999) {
						g.executeBatch();
						l.executeBatch();
						t.executeBatch();
					}
				}
				g.executeBatch();
				l.executeBatch();
				t.executeBatch();
			}
			try (PreparedStatement w = c.prepareStatement("INSERT INTO waypoint VALUES (?,?,?,?,?,?)")) {
				for (int j = 0; j < waypointu; j++) {
					w.setString(1, kod(j % kesi));
					w.setDouble(2, 48.6 + r.nextDouble() * 2.4);
					w.setDouble(3, 12.1 + r.nextDouble() * 6.7);
					w.setString(4, PREFIXY[j / kesi % 5]);
					w.setString(5, TYPY_WPT[j % 5]);
					w.setString(6, veta(r, 3));
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

	private static byte[] zabal(final String text) throws IOException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (DeflaterOutputStream d = new DeflaterOutputStream(baos)) {
			d.write(text.getBytes(StandardCharsets.UTF_8));
		}
		return baos.toByteArray();
	}
}
