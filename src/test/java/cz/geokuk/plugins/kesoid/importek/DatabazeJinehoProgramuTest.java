package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.sql.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

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

	@Test
	public void zamceniSePoznaVRetezciPricin() {
		final Exception e = new RuntimeException(new java.io.IOException(new org.sqlite.SQLiteException("zamčeno", org.sqlite.SQLiteErrorCode.SQLITE_BUSY)));
		Assert.assertTrue(DatabazeJinehoProgramu.jeZamcena(e));
		Assert.assertFalse(DatabazeJinehoProgramu.jeZamcena(new RuntimeException("jiná chyba")));
	}

	@Test
	public void souborCoNeniDatabazeSePopiseCesky() throws Exception {
		final File db = new File(tmp.getRoot(), "geoget.db3");
		java.nio.file.Files.write(db.toPath(), new byte[4096]);
		java.nio.file.Files.write(db.toPath(), "tohle není databáze, jen text dost dlouhý na hlavičku".getBytes("UTF-8"));
		try (Connection c = DatabazeJinehoProgramu.otevri(db); Statement s = c.createStatement()) {
			s.executeQuery("SELECT name FROM sqlite_master").close();
			Assert.fail();
		} catch (final SQLException e) {
			final String popis = DatabazeJinehoProgramu.popisChyby(db, e);
			Assert.assertTrue(popis, popis.contains("není databáze"));
		}
	}

	/** GeoGet nebo GSAK spadl uprostřed zápisu: vedle databáze zůstal rozepsaný žurnál. */
	@Test
	public void nedokoncenyZapisRadiOtevritDatabaziVGeogetu() throws Exception {
		final File zdroj = new File(tmp.newFolder("zdroj"), "geoget.db3");
		final File db = new File(tmp.newFolder("kopie"), "geoget.db3");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + zdroj); Statement s = c.createStatement()) {
			s.execute("PRAGMA journal_mode=DELETE");
			s.execute("CREATE TABLE geocache (id TEXT)");
			c.setAutoCommit(false);
			for (int i = 0; i < 2000; i++) {
				s.execute("INSERT INTO geocache VALUES ('GC" + i + "')");
			}
			s.execute("PRAGMA cache_size=1"); // stránky se zapíší do souboru ještě před commitem
			s.execute("UPDATE geocache SET id = id || 'x'");
			// Kopie v půli transakce = stav po pádu programu, který zapisoval.
			java.nio.file.Files.copy(zdroj.toPath(), db.toPath());
			java.nio.file.Files.copy(new File(zdroj.getPath() + "-journal").toPath(), new File(db.getPath() + "-journal").toPath());
			c.rollback();
		}
		try (Connection c = DatabazeJinehoProgramu.otevri(db); Statement s = c.createStatement()) {
			s.executeQuery("SELECT count(*) FROM geocache").close();
			Assert.fail("databáze s rozepsaným žurnálem se jen pro čtení otevřít nemá");
		} catch (final SQLException e) {
			final String popis = DatabazeJinehoProgramu.popisChyby(db, e);
			Assert.assertTrue(popis, popis.contains("Otevřete ji v GeoGetu nebo GSAKu"));
			Assert.assertFalse(popis, popis.contains("smíte zapisovat"));
		}
	}

	/** WAL databáze v nezapisovatelné složce: rada o právech, ne o nedokončeném zápisu. */
	@Test
	public void nezapisovatelnaSlozkaRadiKontroluPrav() {
		final String popis = DatabazeJinehoProgramu.popisChyby(new File("geoget.db3"),
				new SQLiteException("attempt to write a readonly database", SQLiteErrorCode.SQLITE_READONLY_DIRECTORY));
		Assert.assertTrue(popis, popis.contains("smíte zapisovat"));
		Assert.assertFalse(popis, popis.contains("nedokončený zápis"));
	}
}
