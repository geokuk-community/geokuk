package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.sql.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Databázi GeoGetu nebo GSAKu Geokuk jen čte. */
public class DatabazeJinehoProgramuTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void chybejiciDatabazeSeNezalozi() throws Exception {
		final File db = new File(tmp.getRoot(), "geoget.db3");
		try (Connection c = DatabazeJinehoProgramu.otevri(db); Statement s = c.createStatement()) {
			s.executeQuery("select 1").close();
		} catch (final SQLException e) {
			// neexistující databáze se smí ohlásit jako chyba
		}
		Assert.assertFalse(db.exists());
	}

	@Test(expected = SQLException.class)
	public void doDatabazeNejdeZapsat() throws Exception {
		final File db = new File(tmp.getRoot(), "geoget.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
			s.execute("create table geocache (id text)");
		}
		try (Connection c = DatabazeJinehoProgramu.otevri(db); Statement s = c.createStatement()) {
			s.execute("insert into geocache values ('GC1')");
		}
	}
}
