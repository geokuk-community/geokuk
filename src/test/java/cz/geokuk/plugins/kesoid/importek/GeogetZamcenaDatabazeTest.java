package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;

/** GeoGet zapisuje do své databáze (třeba import PQ) právě ve chvíli, kdy ji Geokuk načítá. */
public class GeogetZamcenaDatabazeTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void nacteSePoUvolneniZamku() throws Exception {
		final File db = new File(tmp.getRoot(), "geoget.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE geocache (id TEXT, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
					+ " cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT, dtfound INTEGER)");
			s.execute("CREATE TABLE geolist (id TEXT, shortdesc BLOB, hint TEXT)");
			s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, prefixid TEXT, wpttype TEXT, name TEXT)");
			s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)");
			s.execute("CREATE TABLE geotagcategory (key INTEGER, value TEXT)");
			s.execute("CREATE TABLE geotagvalue (key INTEGER, value TEXT)");
			s.execute("INSERT INTO geocache VALUES ('GC00001', 50.1, 14.4, 'První', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
		}
		final CountDownLatch zamceno = new CountDownLatch(1);
		final ExecutorService geoget = Executors.newSingleThreadExecutor();
		final Future<?> zapis = geoget.submit(() -> {
			try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
				s.execute("BEGIN EXCLUSIVE");
				s.execute("INSERT INTO geocache VALUES ('GC00002', 50.2, 14.5, 'Druhá', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
				zamceno.countDown();
				Thread.sleep(4000); // déle než výchozí čekání sqlite-jdbc (3 s)
				s.execute("COMMIT");
			}
			return null;
		});
		zamceno.await();
		final List<String> nactene = new ArrayList<>();
		try {
			new GeogetLoader().nacti(db, new SbiraciBuilder(nactene), null, new ProgressModel());
			Assert.assertEquals("načte se stav po zápisu", Arrays.asList("GC00001", "GC00002"), nactene);
		} finally {
			zapis.get();
			geoget.shutdown();
		}
	}

	private static class SbiraciBuilder implements IImportBuilder {
		private final List<String> nactene;

		SbiraciBuilder(final List<String> nactene) {
			this.nactene = nactene;
		}

		@Override
		public void addGpxWpt(final GpxWpt w) {
			nactene.add(w.name);
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
		public void setTrackName(final String aTrackName) {}
	}
}
