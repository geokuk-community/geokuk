package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mvc.GccomNick;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.util.file.FileAndTime;
import cz.geokuk.util.file.KeFile;
import cz.geokuk.util.file.Root;

/**
 * Měření přepínání zdrojů: {@code MerPrepnuti složka [N [opakování done()]]}, s třetím parametrem jen čas done(). Vyrobí GeoGet databáze a GPX (N keší ve velké databázi), načte vše a pak vypíná a zapíná zdroje bez překryvu,
 * s překryvem a všechny najednou. U každého kroku čas načtení, halda se starým i novým bagem (okamžik před uvolněním starého), trvalá halda a špička.
 * S {@code -Dmer.shluky=true} leží keše náhodně (ne na mřížce) a waypointy metry až stovky metrů od keše jako ve skutečných datech; data pak patří do jiné složky.
 */
public class MerPrepnuti {

	private static final boolean SHLUKY = Boolean.getBoolean("mer.shluky");
	private static final Set<File> vypnute = new HashSet<>();
	private static volatile KesBag zobrazene;

	public static void main(final String[] a) throws Exception {
		final File slozka = new File(a[0]);
		final int n = a.length > 1 ? Integer.parseInt(a[1]) : 100000;
		final File gpx = new File(slozka, "gpx");
		final File geoget = new File(slozka, "geoget");
		gpx.mkdirs();
		geoget.mkdirs();
		final File cz = new File(geoget, "cz.db3");
		final File sk = new File(geoget, "sk.db3");
		final File pq = new File(gpx, "pq.gpx");
		final File pqWpts = new File(gpx, "pq-wpts.gpx");
		final File vylet = new File(gpx, "vylet.gpx");
		if (!cz.exists()) {
			databaze(cz, 0, n, n / 2);
			databaze(sk, 10_000_000, n / 4, 0);
			// PQ se z poloviny překrývá s velkou databází
			gpxKesi(pq, n - n / 20, n / 10);
			gpxWaypointu(pqWpts, n - n / 20, n / 10);
			gpxKesi(vylet, 20_000_000, n / 50);
		}

		final Genom genom = new Genom();
		final int opakovaniDone = a.length > 2 ? Integer.parseInt(a[2]) : 3;
		for (int i = 0; i < opakovaniDone; i++) {
			System.out.println("done() všech zdrojů ms;" + casDone(genom, cz, sk, pq, pqWpts, vylet));
		}
		if (a.length > 2) {
			return;
		}
		final MultiNacitac nacitac = new MultiNacitac(model());
		System.out.println("krok;ms;wpt;halda_stary+novy_MB;halda_trvala_MB;spicka_MB");
		krok("plné načtení", nacitac, genom, gpx, geoget);
		for (final Object[] k : new Object[][] { { "výlet GPX bez překryvu", new File[] { vylet } }, { "SK databáze bez překryvu", new File[] { sk } },
				{ "PQ s překryvem", new File[] { pq } }, { "-wpts s překryvem", new File[] { pqWpts } }, { "CZ velká s překryvem", new File[] { cz } },
				{ "vše", new File[] { cz, sk, pq, pqWpts, vylet } } }) {
			final File[] soubory = (File[]) k[1];
			vypnute.addAll(Arrays.asList(soubory));
			krok("vypnout " + k[0], nacitac, genom, gpx, geoget);
			vypnute.removeAll(Arrays.asList(soubory));
			krok("zapnout " + k[0], nacitac, genom, gpx, geoget);
		}
	}

	private static void krok(final String nazev, final MultiNacitac nacitac, final Genom genom, final File gpx, final File geoget) throws Exception {
		gc();
		for (final MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) {
			p.resetPeakUsage();
		}
		nacitac.setRootDirs(true, gpx, geoget, null, Collections.<File>emptySet());
		final long t0 = System.nanoTime();
		final KesBag novy = nacitac.nacti(null, genom);
		final long ms = (System.nanoTime() - t0) / 1_000_000;
		long spicka = 0;
		for (final MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) {
			if (p.getType() == MemoryType.HEAP) {
				spicka += p.getPeakUsage().getUsed();
			}
		}
		final long obaBagy = gc();
		if (novy != null) {
			zobrazene = novy;
		}
		final long trvala = gc();
		System.out.println(nazev + ";" + ms + ";" + (zobrazene == null ? 0 : zobrazene.getWpts().size()) + ";" + obaBagy / 1_000_000 + ";" + trvala / 1_000_000 + ";" + spicka / 1_000_000);
	}

	/** Čas párování a stavby bagu po přečtení všech zdrojů, tj. co dnes stojí každé přepnutí navíc ke čtení. */
	private static long casDone(final Genom genom, final File... soubory) {
		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		final KesoidImportBuilder builder = new KesoidImportBuilder(genom, new GccomNick("Ja", 42), progress, new KesoidPluginManager());
		builder.init();
		for (final File f : soubory) {
			final Nacitac0 nacitac = f.getName().endsWith(".db3") ? new GeogetLoader() : new NacitacGpx();
			builder.setCurrentlyLoading(new KeFile(new FileAndTime(f, f.lastModified()), new Root(f.getParentFile(), new Root.Def(0, null, null))), true);
			nacitac.nactiBezVyjimky(f, builder, null, progress);
		}
		final long t0 = System.nanoTime();
		builder.done();
		return (System.nanoTime() - t0) / 1_000_000;
	}

	private static long gc() {
		final MemoryMXBean m = ManagementFactory.getMemoryMXBean();
		for (int i = 0; i < 3; i++) {
			System.gc();
		}
		return m.getHeapMemoryUsage().getUsed();
	}

	private static String kod(final int i) {
		return "GC" + String.format("%5s", Integer.toString(i, 36).toUpperCase(Locale.ROOT)).replace(' ', '0');
	}

	private static double lat(final int i) {
		if (SHLUKY) {
			return 48.6 + nahodne(i, 1) * 2.4;
		}
		return 48.6 + (i * 7919L % 100000) / 100000.0 * 2.4;
	}

	/** Posun waypointu od keše ve stupních šířky. */
	private static double posun(final int i, final double mrizka) {
		return SHLUKY ? 0.00005 + nahodne(i, 3) * 0.004 : mrizka;
	}

	/** Deterministické číslo z [0, 1) podle indexu keše (splitmix64). */
	private static double nahodne(final int i, final int osa) {
		long z = i * 0x9E3779B97F4A7C15L + osa;
		z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
		z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 11) / (double) (1L << 53);
	}

	private static double lon(final int i) {
		if (SHLUKY) {
			return 12.1 + nahodne(i, 2) * 6.7;
		}
		return 12.1 + (i * 104729L % 100000) / 100000.0 * 6.7;
	}

	private static void databaze(final File db, final int od, final int pocet, final int waypointu) throws SQLException {
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE geocache (id TEXT, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
					+ " cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT, dtfound INTEGER)");
			s.execute("CREATE TABLE geolist (id TEXT, shortdesc BLOB, hint TEXT)");
			s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, prefixid TEXT, wpttype TEXT, name TEXT)");
			s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)");
			s.execute("CREATE TABLE geotagcategory (key INTEGER, value TEXT)");
			s.execute("CREATE TABLE geotagvalue (key INTEGER, value TEXT)");
			c.setAutoCommit(false);
			try (PreparedStatement k = c.prepareStatement("INSERT INTO geocache VALUES (?, ?, ?, ?, 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
					PreparedStatement w = c.prepareStatement("INSERT INTO waypoint VALUES (?, ?, ?, 'PK', 'Parking Area', 'Parkoviště')")) {
				for (int i = od; i < od + pocet; i++) {
					k.setString(1, kod(i));
					k.setDouble(2, lat(i));
					k.setDouble(3, lon(i));
					k.setString(4, "Keš " + i);
					k.addBatch();
					if (i - od < waypointu) {
						w.setString(1, kod(i));
						w.setDouble(2, lat(i) + posun(i, 0.001));
						w.setDouble(3, lon(i));
						w.addBatch();
					}
				}
				k.executeBatch();
				w.executeBatch();
			}
			c.commit();
		}
	}

	private static void gpxKesi(final File soubor, final int od, final int pocet) throws IOException {
		try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(soubor), StandardCharsets.UTF_8))) {
			w.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<gpx version=\"1.0\" xmlns=\"http://www.topografix.com/GPX/1/0\">\n");
			for (int i = od; i < od + pocet; i++) {
				w.write("<wpt lat=\"" + lat(i) + "\" lon=\"" + lon(i) + "\"><name>" + kod(i) + "</name><sym>Geocache</sym><type>Geocache|Traditional Cache</type>"
						+ "<groundspeak:cache available=\"True\" archived=\"False\" xmlns:groundspeak=\"http://www.groundspeak.com/cache/1/0/1\">" + "<groundspeak:name>PQ keš " + i
						+ "</groundspeak:name><groundspeak:placed_by>Cizí</groundspeak:placed_by><groundspeak:owner id=\"1\">Cizí</groundspeak:owner>"
						+ "<groundspeak:type>Traditional Cache</groundspeak:type><groundspeak:container>Small</groundspeak:container><groundspeak:difficulty>2</groundspeak:difficulty>"
						+ "<groundspeak:terrain>1.5</groundspeak:terrain><groundspeak:encoded_hints>nápověda</groundspeak:encoded_hints></groundspeak:cache></wpt>\n");
			}
			w.write("</gpx>");
		}
	}

	private static void gpxWaypointu(final File soubor, final int od, final int pocet) throws IOException {
		try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(soubor), StandardCharsets.UTF_8))) {
			w.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<gpx version=\"1.0\" xmlns=\"http://www.topografix.com/GPX/1/0\">\n");
			for (int i = od; i < od + pocet; i++) {
				w.write("<wpt lat=\"" + (lat(i) + posun(i, 0.002)) + "\" lon=\"" + lon(i) + "\"><name>FN" + kod(i).substring(2) + "</name><desc>Final</desc><sym>Final Location</sym>"
						+ "<type>Waypoint|Final Location</type></wpt>\n");
			}
			w.write("</gpx>");
		}
	}

	private static KesoidModel model() {
		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		final GccomNick nick = new GccomNick("Ja", 42);
		final KesoidModel model = new KesoidModel() {
			@Override
			public GccomNick getGccomNick() {
				return nick;
			}

			@Override
			public KesBag getVsechnyKesoidy() {
				return zobrazene;
			}

			@Override
			public void fire(final Event0<?> udalost) {}

			@Override
			public void setNacitaneZdroje(final InformaceOZdrojich zdroje) {}

			@Override
			public void zaradGsakDatabaze(final Set<File> databaze, final Set<File> nedostupne) {}

			@Override
			public void zaradOpensakDatabaze(final Set<File> databaze, final Set<File> nedostupne) {}

			@Override
			public boolean maSeNacist(final File zdroj) {
				return !vypnute.contains(zdroj);
			}

			@Override
			public boolean maSeNacist(final KeFile zdroj) {
				return maSeNacist(zdroj.getFile());
			}
		};
		model.inject(progress);
		model.inject(new KesoidPluginManager());
		return model;
	}
}
