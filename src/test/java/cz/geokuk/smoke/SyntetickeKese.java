package cz.geokuk.smoke;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;

/**
 * Vyrobí GPX se syntetickými kešemi ve stylu exportu z GeoGetu, s přídavnými waypointy. Keše leží kolem zadaného bodu.
 */
public class SyntetickeKese {

	private static final String[] TYPY = { "Traditional Cache", "Multi-cache", "Unknown Cache", "Letterbox Hybrid", "Earthcache", "Wherigo Cache" };
	private static final String[] NADOBY = { "Micro", "Small", "Regular", "Large", "Other" };
	private static final String[] HODNOTY = { "1", "1.5", "2", "2.5", "3", "4", "5" };

	/** Zapíše {@code pocet} keší, vrátí počet všech waypointů včetně přídavných. */
	public static int zapis(final File soubor, final int pocet, final double lat, final double lon, final double rozptyl) throws IOException {
		return zapis(soubor, 0, pocet, lat, lon, rozptyl);
	}

	/** Jako {@link #zapis(File, int, double, double, double)}, kódy keší začínají od pořadí {@code prvni}. */
	public static int zapis(final File soubor, final int prvni, final int pocet, final double lat, final double lon, final double rozptyl) throws IOException {
		try (OutputStream os = new FileOutputStream(soubor)) {
			return zapis(os, prvni, pocet, lat, lon, rozptyl);
		}
	}

	public static int zapis(final OutputStream os, final int prvni, final int pocet, final double lat, final double lon, final double rozptyl) throws IOException {
		final Random r = new Random(1 + prvni);
		int wpt = 0;
		final PrintWriter w = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
		w.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
		w.println("<gpx version=\"1.0\" creator=\"GeoGet\" xmlns=\"http://www.topografix.com/GPX/1/0\" xmlns:groundspeak=\"http://www.groundspeak.com/cache/1/0/1\">");
		for (int i = prvni; i < prvni + pocet; i++) {
			final double la = lat + (r.nextDouble() - 0.5) * 2 * rozptyl;
			final double lo = lon + (r.nextDouble() - 0.5) * 2 * rozptyl;
			final String kod = "GC" + Integer.toString(0x10000 + i, 36).toUpperCase(Locale.ROOT);
			final String typ = TYPY[r.nextInt(TYPY.length)];
			final String jmeno = "Kes " + i;
			w.printf(Locale.ROOT, "<wpt lat=\"%.6f\" lon=\"%.6f\"><time>2012-05-01T00:00:00</time><name>%s</name><desc>%s by owner%d, %s</desc>", la, lo, kod, jmeno, i % 97, typ);
			w.printf("<url>https://www.geocaching.com/geocache/%s</url><urlname>%s</urlname><sym>%s</sym><type>Geocache|%s</type>%n", kod, jmeno, i % 5 == 0 ? "Geocache Found" : "Geocache", typ);
			w.printf("<groundspeak:cache id=\"%d\" available=\"%s\" archived=\"False\"><groundspeak:name>%s</groundspeak:name><groundspeak:placed_by>owner%d</groundspeak:placed_by>", 1000000 + i, i % 13 == 0 ? "False" : "True", jmeno,
					i % 97);
			w.printf("<groundspeak:owner id=\"%d\">owner%d</groundspeak:owner><groundspeak:type>%s</groundspeak:type><groundspeak:container>%s</groundspeak:container>", i % 97, i % 97, typ, NADOBY[r.nextInt(NADOBY.length)]);
			w.printf("<groundspeak:difficulty>%s</groundspeak:difficulty><groundspeak:terrain>%s</groundspeak:terrain><groundspeak:country>Czech Republic</groundspeak:country>", HODNOTY[r.nextInt(HODNOTY.length)],
					HODNOTY[r.nextInt(HODNOTY.length)]);
			w.printf("<groundspeak:short_description html=\"False\">Krátký popis %d</groundspeak:short_description><groundspeak:long_description html=\"True\">&lt;p&gt;Popis %d&lt;/p&gt;</groundspeak:long_description>", i, i);
			w.printf("<groundspeak:encoded_hints>Pod kamenem</groundspeak:encoded_hints></groundspeak:cache></wpt>%n");
			wpt++;
			final int pridavnych = r.nextInt(3);
			for (int k = 0; k < pridavnych; k++) {
				final String[] druh = k == 0 ? new String[] { "PK", "Parking Area" } : new String[] { "FN", "Final Location" };
				w.printf(Locale.ROOT, "<wpt lat=\"%.6f\" lon=\"%.6f\"><time>2012-05-01T00:00:00</time><name>%s%s</name><cmt>poznámka</cmt><desc>%s</desc><sym>%s</sym><type>Waypoint|%s</type></wpt>%n", la + 0.001,
						lo + 0.001 * (k + 1), druh[0], kod.substring(2), druh[1], druh[1], druh[1]);
				wpt++;
			}
		}
		w.println("</gpx>");
		w.flush();
		return wpt;
	}
}
