package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.nio.file.Files;
import java.util.Random;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

public class CacheDbTest {

	private static final String TABULKA = "CREATE TABLE tiles (x int, y int, z int, s varchar(10), image blob, PRIMARY KEY(x, y, z, s))";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static void naplni(final SqlJetDb db, final int pocet) throws Exception {
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		db.createTable(TABULKA);
		final ISqlJetTable t = db.getTable("tiles");
		final Random r = new Random(1);
		for (int i = 0; i < pocet; i++) {
			final byte[] d = new byte[10000 + r.nextInt(10000)];
			r.nextBytes(d);
			t.insertOr(SqlJetConflictAction.REPLACE, i, 1, 14, "o1", d);
		}
		db.commit();
	}

	private static void smazKazdouDruhou(final SqlJetDb db) throws Exception {
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		final ISqlJetCursor c = db.getTable("tiles").open();
		while (!c.eof()) {
			c.delete();
			if (!c.eof()) {
				c.next();
			}
		}
		c.close();
		db.commit();
	}

	@Test
	public void novaCacheMaVetsiStranku() throws Exception {
		final File f = new File(tmp.getRoot(), "tiles.sqlite");
		final CacheDb db = CacheDb.otevri(f, true);
		naplni(db, 50);
		db.close();
		Assert.assertEquals(4096, StavSouboruCache.precti(f).velikostStranky);
		final SqlJetDb znovu = CacheDb.otevri(f, true);
		znovu.beginTransaction(SqlJetTransactionMode.READ_ONLY);
		Assert.assertEquals(50, znovu.getTable("tiles").open().getRowCount());
		znovu.commit();
		znovu.close();
	}

	@Test
	public void staraCacheZustaneSeStrankou() throws Exception {
		final File f = new File(tmp.getRoot(), "tiles.sqlite");
		final SqlJetDb stara = SqlJetDb.open(f, true);
		naplni(stara, 50);
		stara.close();
		final CacheDb db = CacheDb.otevri(f, true);
		db.close();
		Assert.assertEquals(1024, StavSouboruCache.precti(f).velikostStranky);
	}

	@Test
	public void podilVolnychZHlavicky() throws Exception {
		final File f = new File(tmp.getRoot(), "tiles.sqlite");
		final CacheDb db = CacheDb.otevri(f, true);
		naplni(db, 200);
		smazKazdouDruhou(db);
		db.close();
		final StavSouboruCache stav = StavSouboruCache.precti(f);
		Assert.assertEquals(f.length(), stav.stranek * stav.velikostStranky);
		Assert.assertEquals(0.5, stav.podilVolnych(), 0.1);
		Assert.assertEquals(f.length(), stav.zivaData() + stav.volnych * stav.velikostStranky);
	}

	@Test
	public void vadnaHlavickaJeNeznamo() throws Exception {
		final File f = tmp.newFile("tiles.sqlite");
		Assert.assertNull(StavSouboruCache.precti(f));
		Files.write(f.toPath(), new byte[200]);
		Assert.assertNull(StavSouboruCache.precti(f));
		Assert.assertNull(StavSouboruCache.precti(new File(tmp.getRoot(), "neni.sqlite")));
	}
}
