package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.sql.*;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;

/** Tagy z databáze GeoGetu musí být u keše už ve chvíli, kdy ji loader předá dál. */
public class GeogetLoaderTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void tagyJsouUKeseUzPriPridani() throws Exception {
		final File db = new File(tmp.getRoot(), "test.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE geocache (id TEXT, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT,"
					+ " cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT, dtfound INTEGER)");
			s.execute("CREATE TABLE geolist (id TEXT, shortdesc BLOB, hint TEXT)");
			s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, prefixid TEXT, wpttype TEXT, name TEXT)");
			s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)");
			s.execute("CREATE TABLE geotagcategory (key INTEGER, value TEXT)");
			s.execute("CREATE TABLE geotagvalue (key INTEGER, value TEXT)");
			s.execute("INSERT INTO geocache VALUES ('GC12345', 50.1, 14.4, 'Keš', 'autor', 'Traditional Cache', 'Regular', '2', '3', 0, 1, 20200101, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geocache VALUES ('GC99999', 50.2, 14.5, 'Bez tagů', 'autor', 'Traditional Cache', 'Regular', '1', '1', 0, 1, 20200101, 'CZ', 'Praha', 0)");
			s.execute("INSERT INTO geotagcategory VALUES (1, 'Hodnoceni'), (2, 'Znamka'), (3, 'favorites'), (4, 'Elevation'), (5, 'BestOf'), (6, 'geokuk_barva')");
			s.execute("INSERT INTO geotagvalue VALUES (1, '80%'), (2, '75'), (3, '12'), (4, '450'), (5, '3'), (6, 'modra')");
			s.execute("INSERT INTO geotag VALUES ('GC12345', 1, 1), ('GC12345', 2, 2), ('GC12345', 3, 3), ('GC12345', 4, 4), ('GC12345', 5, 5), ('GC12345', 6, 6)");
		}

		final Map<String, String> priPridani = new HashMap<>();
		final IImportBuilder builder = new IImportBuilder() {
			@Override
			public void addGpxWpt(final GpxWpt w) {
				priPridani.put(w.name, w.gpxg.hodnoceni + "/" + w.gpxg.znamka + "/" + w.gpxg.favorites + "/" + w.gpxg.elevation + "/" + w.gpxg.bestOf + "/" + w.gpxg.userTags);
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
		};

		new GeogetLoader().nacti(db, builder, null, new ProgressModel());

		Assert.assertEquals("80/75/12/450/3/{barva=modra}", priPridani.get("GC12345"));
		Assert.assertEquals("keš bez tagů má hodnocení neuvedené", "-1/-1/-1/0/-1/{}", priPridani.get("GC99999"));
	}
}
