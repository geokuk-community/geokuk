package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.sql.*;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.mvc.GsakParametryNacitani;

/** Načtení keší z databáze GSAK. */
public class GsakDbLoaderTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File db;

	@Before
	public void setUp() throws Exception {
		db = new File(tmp.getRoot(), "sqlite.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			for (final String t : new String[] { "Attributes", "CacheImages", "Corrected", "Filter", "Ignore", "LogImages", "LogMemo", "Logs" }) {
				s.execute("CREATE TABLE " + t + " (Code TEXT)");
			}
			s.execute("CREATE TABLE Caches (Code TEXT, Name TEXT, PlacedBy TEXT, Archived INTEGER, CacheType TEXT, Container TEXT, County TEXT, Country TEXT, Difficulty TEXT,"
					+ " FoundByMeDate TEXT, Latitude REAL, Longitude REAL, OwnerId INTEGER, OwnerName TEXT, PlacedDate TEXT, State TEXT, TempDisabled INTEGER, Terrain TEXT,"
					+ " LatOriginal REAL, LonOriginal REAL, Elevation INTEGER, FavPoints INTEGER)");
			s.execute("CREATE TABLE CacheMemo (Code TEXT, ShortDescription TEXT, Hints TEXT)");
			s.execute("CREATE TABLE Waypoints (cParent TEXT, cCode TEXT, cPrefix TEXT, cName TEXT, cType TEXT, cLat REAL, cLon REAL, cByuser INTEGER, cDate TEXT, cFlag INTEGER, sB1 INTEGER)");
			s.execute("CREATE TABLE Custom (Code TEXT, barva TEXT, CasNalezu TEXT)");
			s.execute("INSERT INTO Caches VALUES ('GC1111', 'Mystery u řeky', 'Kačer', 0, 'U', 'Small', 'Praha', 'Czech Republic', '3.5', '2020-05-01', 50.2, 14.5, 7, 'Kačer',"
					+ " '2015-06-01', 'Praha', 0, '2', 50.1, 14.4, 250, 12)");
			s.execute("INSERT INTO Caches VALUES ('GC2222', 'Archivní', 'Jiný', 1, 'T', 'Regular', 'Brno', 'Czech Republic', '1', '', 49.2, 16.6, 8, 'Jiný', '2010-01-01', 'Brno', 0, '1',"
					+ " 0, 0, 0, 0)");
			s.execute("INSERT INTO CacheMemo VALUES ('GC1111', 'Krátký popis', 'Pod kamenem'), ('GC2222', '', '')");
			s.execute("INSERT INTO Waypoints VALUES ('GC2222', 'PK2222', 'PK', 'Parkoviště', 'Parking Area', 49.21, 16.61, 0, '', 0, 0)");
			s.execute("INSERT INTO Custom VALUES ('GC1111', 'modra', '10:15')");
		}
	}

	private Map<String, GpxWpt> nacti() throws Exception {
		final Map<String, GpxWpt> w = new LinkedHashMap<>();
		final IImportBuilder builder = new IImportBuilder() {
			@Override
			public void addGpxWpt(final GpxWpt g) {
				w.put(g.name, g);
			}

			@Override
			public void init() {}

			@Override
			public void done() {}

			@Override
			public void addTrackWpt(final GpxWpt wpt) {}

			@Override
			public void begTrack() {}

			@Override
			public void begTrackSegment() {}

			@Override
			public void endTrack() {}

			@Override
			public void endTrackSegment() {}

			@Override
			public void setTrackName(final String nazev) {}
		};
		final GsakParametryNacitani parametry = new GsakParametryNacitani();
		parametry.setCasNalezu(Collections.singleton("CasNalezu"));
		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		new GsakDbLoader(() -> parametry).nacti(db, builder, null, progress);
		return w;
	}

	@Test
	public void rozpoznaDatabaziGsak() {
		Assert.assertTrue(new GsakDbLoader(GsakParametryNacitani::new).umiNacist(db));
	}

	@Test
	public void kes() throws Exception {
		final GpxWpt kes = nacti().get("GC1111");
		Assert.assertEquals("Mystery u řeky", kes.groundspeak.name);
		Assert.assertEquals("Unknown Cache", kes.groundspeak.type);
		Assert.assertEquals("3.5", kes.groundspeak.difficulty);
		Assert.assertEquals("2", kes.groundspeak.terrain);
		Assert.assertEquals("Small", kes.groundspeak.container);
		Assert.assertNull("popisy se při načítání nečtou", kes.groundspeak.encodedHints);
		Assert.assertEquals("Pod kamenem", kes.groundspeak.hintZDatabaze.get());
		Assert.assertEquals(7, kes.groundspeak.ownerid);
		Assert.assertEquals(12, kes.gpxg.favorites);
		Assert.assertEquals(250, kes.gpxg.elevation);
		Assert.assertEquals("http://coord.info/GC1111", kes.link.href);
	}

	@Test
	public void opraveneSouradniceJakoFinal() throws Exception {
		final Map<String, GpxWpt> w = nacti();
		Assert.assertEquals("keš zůstává na původním místě", 50.1, w.get("GC1111").wgs.lat, 1e-9);
		final GpxWpt fin = w.get("##1111");
		Assert.assertNotNull(fin);
		Assert.assertEquals("Final Location", fin.sym);
		Assert.assertEquals(50.2, fin.wgs.lat, 1e-9);
		Assert.assertNull("bez opravy není final", w.get("##2222"));
	}

	@Test
	public void nalezenaSCasemZVlastniHodnoty() throws Exception {
		final GpxWpt kes = nacti().get("GC1111");
		Assert.assertEquals("Geocache Found", kes.sym);
		Assert.assertEquals("2020-05-01T10:15", kes.gpxg.found);
		Assert.assertEquals("modra", kes.gpxg.userTags.get("barva"));
	}

	@Test
	public void casNalezuZeSloupceKese() throws Exception {
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("DELETE FROM Custom");
			s.execute("ALTER TABLE Caches ADD COLUMN casnalezu TEXT");
			s.execute("UPDATE Caches SET casnalezu = '9:05' WHERE Code = 'GC1111'");
		}
		Assert.assertEquals("2020-05-01T9:05", nacti().get("GC1111").gpxg.found);
	}

	@Test
	public void prazdnaVlastniHodnotaNeniTag() throws Exception {
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("INSERT INTO Custom VALUES ('GC2222', '  ', NULL)");
		}
		final GpxWpt kes = nacti().get("GC2222");
		Assert.assertFalse(kes.gpxg.userTags.containsKey("barva"));
		Assert.assertFalse(kes.gpxg.userTags.containsKey("CasNalezu"));
	}

	@Test
	public void archivovanaAPridavnyWaypoint() throws Exception {
		final Map<String, GpxWpt> w = nacti();
		Assert.assertTrue(w.get("GC2222").groundspeak.archived);
		Assert.assertEquals("Geocache", w.get("GC2222").sym);
		Assert.assertEquals("Parkoviště", w.get("PK2222").desc);
		Assert.assertEquals("Parking Area", w.get("PK2222").sym);
	}
	/** Starší GSAK nemá některé sloupce a tabulky, keše se přesto načtou. */
	@Test
	public void starsiSchemaBezNepovinnychSloupcu() throws Exception {
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			for (final String t : new String[] { "Attributes", "CacheImages", "Corrected", "Filter", "Ignore", "LogImages", "LogMemo", "Logs", "Waypoints", "Custom", "Caches" }) {
				s.execute("DROP TABLE " + t);
			}
			s.execute("CREATE TABLE Caches (Code TEXT, Name TEXT, PlacedBy TEXT, Archived INTEGER, CacheType TEXT, Container TEXT, County TEXT, Country TEXT, Difficulty TEXT,"
					+ " FoundByMeDate TEXT, Latitude REAL, Longitude REAL, OwnerId INTEGER, OwnerName TEXT, PlacedDate TEXT, State TEXT, TempDisabled INTEGER, Terrain TEXT)");
			s.execute("INSERT INTO Caches VALUES ('GC3333', 'Stará', 'Kačer', 0, 'T', 'Small', 'Praha', 'Czech Republic', '1', '', 50.2, 14.5, 7, 'Kačer',"
					+ " '2015-06-01', 'Praha', 0, '2')");
		}
		Assert.assertTrue(new GsakDbLoader(GsakParametryNacitani::new).umiNacist(db));
		final Map<String, GpxWpt> w = nacti();
		Assert.assertEquals("keš bez řádku v CacheMemo se načte", Collections.singleton("GC3333"), w.keySet());
		Assert.assertEquals(0, w.get("GC3333").gpxg.favorites);
	}

	/** Nesmyslná souřadnice jednoho waypointu nesmí zahodit ostatní. */
	@Test
	public void vadnyWaypointNeshodiOstatni() throws Exception {
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("INSERT INTO Waypoints VALUES ('GC2222', 'S12222', 'S1', 'Nekonečná', 'Stages', 9e999, 16.61, 0, '', 0, 0)");
			s.execute("INSERT INTO Waypoints VALUES ('GC2222', 'S22222', 'S2', 'Další', 'Stages', 49.22, 16.62, 0, '', 0, 0)");
		}
		final Map<String, GpxWpt> w = nacti();
		Assert.assertTrue(w.containsKey("PK2222"));
		Assert.assertTrue(w.containsKey("S22222"));
		Assert.assertFalse(w.containsKey("S12222"));
	}

	/** Vadný záznam nesmí připravit uživatele o zbytek databáze. */
	@Test
	public void vadnyZaznamNeshodiCelouDatabazi() throws Exception {
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("INSERT INTO Caches VALUES ('G', 'Zmrzačený kód', 'Nikdo', 0, 'T', 'Micro', 'Praha', 'Czech Republic', '1', '', 50.3, 14.7, 9, 'Nikdo',"
					+ " '2018-01-01', 'Praha', 0, '1', 50.31, 14.71, 0, 0)");
			s.execute("INSERT INTO CacheMemo VALUES ('G', '', '')");
		}
		final Map<String, GpxWpt> w = nacti();
		Assert.assertNotNull("ostatní keše se načtou", w.get("GC1111"));
		Assert.assertNotNull(w.get("GC2222"));
	}
}
