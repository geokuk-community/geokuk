package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.swing.SwingUtilities;

import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.*;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.Dlg;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import lombok.extern.slf4j.Slf4j;

/**
 * An implementation of KachleManager that stores the data to SQLite database.
 *
 * @author Danstahr
 */
@Slf4j
class KachleDBManager implements KachleManager {



	/**
	 * The name of the SQLite file
	 */
	private static final String FILE_NAME = "tiles.sqlite";

	/**
	 * The name of the table with tiles
	 */
	private static final String TABLE_NAME = "tiles";

	/**
	 * A query to create the appropriate table
	 */
	private static final String TABLE_CREATE_QUERY = String.format("CREATE TABLE %s (x int, y int, " + "z int, s varchar(10), image blob, PRIMARY KEY(x, y, z, s))", TABLE_NAME);

	/** Kolikrát se zkusí zápis, když cache zamyká čtení z jiného vlákna. */
	private static final int POKUSU_O_ZAPIS = 10;

	/**
	 * Since we've got multiple threads that can write to the database, using a single connection is hardly possible (would require synchronization on code level, which is not the way to go). We also want to avoid exposing the implementation details further. Since the number of threads is small
	 * enough, we don't need a connection pool and instead we've got a connection for each thread.
	 */
	final Map<Map.Entry<Thread, File>, SqlJetDb> connections = new ConcurrentHashMap<>();

	/**
	 * The DB file.
	 */
	final KachleCacheFolderHolder folderHolder;

	private final OpakovaneChyby chybyCteni = new OpakovaneChyby("Nepodařilo se přečíst dlaždici z databáze");

	private final OpakovaneChyby chybyZavreni = new OpakovaneChyby("Nepodařilo se zavřít kurzor databáze dlaždic");

	private final OpakovaneChyby chybyDokonceni = new OpakovaneChyby("Nepodařilo se dokončit transakci databáze dlaždic");

	private final OpakovaneChyby chybyZapisu = new OpakovaneChyby("Nepodařilo se zapsat dlaždice do databáze");

	private final OpakovaneChyby chybyOtevreni = new OpakovaneChyby("Nepodařilo se otevřít databázi dlaždic");

	private volatile int neuspesnychOtevreniZaSebou;

	/** Zámky SqlJet mezi spojeními v jednom procesu nevylučují čtení a zápis, proto vlastní zámek. */
	private final ReentrantReadWriteLock zamek = new ReentrantReadWriteLock(true);

	private static final long ZNOVU_ZKUSIT_ZAPIS_MS = 60_000;

	private final Set<File> zapisovatelneSlozky = ConcurrentHashMap.newKeySet();

	/** Nezapisovatelnou složku (třeba odpojený disk) zkouší po chvíli znovu. */
	private final Map<File, Long> nezapisovatelneDo = new ConcurrentHashMap<>();

	private boolean uzivatelUpozornen;

	/**
	 * Constructs a new instance of the DB Manager.
	 */
	public KachleDBManager(final KachleCacheFolderHolder holder) {
		log.trace("Constructor " + Thread.currentThread().getName());
		folderHolder = holder;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean exists(final Ka ki) {
		// TODO : No need to load the whole image
		return load(ki) != null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Image load(final Ka ki) {
		final SqlJetDb database = getDatabaseConnection();
		if (database == null) {
			return null;
		}
		final byte[] data;
		zamek.readLock().lock();
		try {
			data = nactiData(database, ki);
		} finally {
			zamek.readLock().unlock();
		}
		if (data == null) {
			return null;
		}
		try {
			final Image img = KachloDownloader.precti(new ByteArrayInputStream(data));
			if (img == null) {
				log.debug("Loaded DB image is null!");
			}
			return img;
		} catch (final KachloDownloader.UseknutaDlazdice e) {
			// Useknutou dlaždici z dřívějška bere jako chybějící, stáhne se znovu a přepíše.
			log.debug("{}: {}", ki, e.getMessage());
			return null;
		} catch (final IOException e) {
			chybyCteni.ohlas(e);
			throw new RuntimeException(e);
		}
	}

	/** Čtecí transakce blokuje zápis, proto se v ní jen přečtou bajty a obrázek se dekóduje až po ní. */
	private byte[] nactiData(final SqlJetDb database, final Ka ki) {
		ISqlJetCursor cursor = null;
		boolean vadne = false;

		try {
			final ISqlJetTable table = database.getTable(TABLE_NAME);
			database.beginTransaction(SqlJetTransactionMode.READ_ONLY);
			cursor = table.lookup(table.getPrimaryKeyIndexName(), ki.getLoc().getFromSzUnsignedX(), ki.getLoc().getFromSzUnsignedY(), ki.getLoc().getMoumer(), ki.typToString());
			if (cursor.eof()) {
				return null;
			}
			if (log.isDebugEnabled()) {
				log.debug("{} : {} {} {} {} loading from DB", cursor.getRowId(), cursor.getInteger("x"), cursor.getInteger("y"), cursor.getInteger("z"), cursor.getString("s"));
			}
			return cursor.getBlobAsArray("image");
		} catch (final SqlJetException e) {
			chybyCteni.ohlas(e);
			vadne = true;
			throw new RuntimeException(e);
		} finally {
			if (cursor != null) {
				try {
					cursor.close();
				} catch (final SqlJetException e) {
					chybyZavreni.ohlas(e);
				}
			}
			try {
				database.commit();
			} catch (final SqlJetException e) {
				chybyDokonceni.ohlas(e);
			}
			if (vadne) {
				zahod(database);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean save(final Collection<ItemToSave> imagesToSave) {
		// Do SQLite zapisuje vždy jen jedno spojení; souběžné zápisy by si navzájem vracely BUSY.
		synchronized (this) {
			zamek.writeLock().lock();
			try {
				return saveJednoVlakno(imagesToSave);
			} finally {
				zamek.writeLock().unlock();
			}
		}
	}

	private boolean saveJednoVlakno(final Collection<ItemToSave> imagesToSave) {
		final SqlJetDb database = getDatabaseConnection();
		if (database == null) {
			return false;
		}

		for (int pokus = 1;; pokus++) {
			try {
				zapis(database, imagesToSave);
				return true;
			} catch (final SqlJetException e) {
				if (e.getErrorCode() == SqlJetErrorCode.BUSY && pokus < POKUSU_O_ZAPIS) {
					log.debug("Cache dlaždic je zamčená čtením, zápis zkusím znovu ({}. pokus)", pokus);
					try {
						Thread.sleep(20L * pokus);
					} catch (final InterruptedException e1) {
						Thread.currentThread().interrupt();
						chybyZapisu.ohlas(e);
						return false;
					}
					continue;
				}
				chybyZapisu.ohlas(e);
				if (e.getErrorCode() != SqlJetErrorCode.BUSY) {
					zahod(database);
				}
				return false;
			}
		}
	}

	/** Zapíše dávku v jedné transakci, při chybě ji vrátí. */
	private void zapis(final SqlJetDb database, final Collection<ItemToSave> imagesToSave) throws SqlJetException {
		byte[] dataToSave;
		try {
			database.beginTransaction(SqlJetTransactionMode.WRITE);
			for (final ItemToSave imageToSave : imagesToSave) {
				final Ka ki = imageToSave.key;

				dataToSave = imageToSave.imageData;

				// Save to the database
				final KaLoc kaloc = ki.getLoc();
				final int kx = kaloc.getFromSzUnsignedX();
				final int ky = kaloc.getFromSzUnsignedY();
				log.debug("Adding {} {} {} {}", kx, ky, kaloc.getMoumer(), ki.typToString());
				database.getTable(TABLE_NAME).insertOr(SqlJetConflictAction.REPLACE, kx, ky, kaloc.getMoumer(), ki.typToString(), dataToSave);
			}
			database.commit();
		} catch (final SqlJetException e) {
			try {
				database.rollback();
			} catch (final SqlJetException e1) {
				chybyDokonceni.ohlas(e1);
			}
			throw e;
		}
	}

	/**
	 * Spojení po chybě už může být nepoužitelné (přerušení vlákna zavře kanál souboru),
	 * proto ho zahodíme a příští požadavek otevře nové.
	 */
	private void zahod(final SqlJetDb database) {
		connections.values().remove(database);
		zavri(database);
	}

	private static void zavri(final SqlJetDb database) {
		try {
			database.close();
		} catch (final SqlJetException e) {
			log.debug("Spojení s cache dlaždic nejde zavřít.", e);
		}
	}

	/**
	 * Get a connection to the database for the current thread. Also closes all invalid connections for the current thread.
	 *
	 * @return The connection or null if the connection couldn't be established.
	 *
	 * @see #connections
	 */
	private SqlJetDb getDatabaseConnection() {
		final Thread t = Thread.currentThread();
		final File folder = folderHolder.getKachleCacheFolder().getEffectiveFile();
		if (!lzePouzit(folder)) {
			return null; // dlaždice zůstanou jen v paměti
		}
		final File f = new File(folder, FILE_NAME);
		final AbstractMap.SimpleImmutableEntry<Thread, File> mapKey = new AbstractMap.SimpleImmutableEntry<>(t, f);

		if (connections.containsKey(mapKey)) {
			// Got a valid connection
			return connections.get(mapKey);
		}
		SqlJetDb database;
		try {
			database = otevri(f);
		} catch (final SqlJetException e) {
			database = otevriPoskozenou(f, e);
			if (database == null) {
				return null;
			}
		}
		try {
			// Close all deprecated connections (should be exactly one or zero)
			// Done here for synchronization reasons. This way, we never close an active connection that's
			// needed elsewhere at the same moment and there's always at most one connection per thread.
			for (final Map.Entry<Map.Entry<Thread, File>, SqlJetDb> conn : connections.entrySet()) {
				if (conn.getKey().getKey().equals(t)) {
					conn.getValue().close();
					connections.remove(conn.getKey());
				}
			}
		} catch (final SqlJetException e) {
			log.error("Unable to close the deprecated DB connection!", e);
		}

		neuspesnychOtevreniZaSebou = 0;
		connections.put(mapKey, database);
		return database;
	}

	/** Do složky, kam nejde zapisovat (třeba Program Files), se cache nezakládá a dlaždice zůstávají jen v paměti. */
	private boolean lzePouzit(final File slozka) {
		if (zapisovatelneSlozky.contains(slozka)) {
			return true;
		}
		synchronized (nezapisovatelneDo) {
			final Long nezapisovatelnaDo = nezapisovatelneDo.get(slozka);
			if (nezapisovatelnaDo != null && System.currentTimeMillis() < nezapisovatelnaDo) {
				return false;
			}
			if (lzeZapsat(slozka)) {
				nezapisovatelneDo.remove(slozka);
				zapisovatelneSlozky.add(slozka);
				return true;
			}
			if (nezapisovatelnaDo == null) {
				log.warn("Do složky cache dlaždic {} nelze zapisovat, dlaždice zůstanou jen v paměti.", slozka);
				upozorniNaZapis(slozka);
			}
			nezapisovatelneDo.put(slozka, System.currentTimeMillis() + ZNOVU_ZKUSIT_ZAPIS_MS);
			return false;
		}
	}

	/** Na nezapisovatelnou složku data už upozorňuje kontrola umístění programu. */
	private synchronized void upozorniNaZapis(final File slozka) {
		if (uzivatelUpozornen || GraphicsEnvironment.isHeadless()) {
			return;
		}
		if (slozka.toPath().toAbsolutePath().startsWith(FConst.DATA_DIR.toPath().toAbsolutePath()) && !lzeZapsat(FConst.DATA_DIR)) {
			return;
		}
		uzivatelUpozornen = true;
		SwingUtilities.invokeLater(() -> Dlg.upozorneni("Do složky " + slozka + " nelze zapisovat, mapy se budou pokaždé stahovat znovu."));
	}

	boolean lzeZapsat(final File slozka) {
		try {
			Files.createDirectories(slozka.toPath());
			final File zkouska = File.createTempFile("zapis", ".tmp", slozka);
			return zkouska.delete();
		} catch (final IOException | RuntimeException e) {
			log.debug("Zkouška zápisu do {}: {}", slozka, e.toString());
			return false;
		}
	}

	/** Nová cache se při prvním čtení schématu zapisuje, proto otevírání pod stejným zámkem jako zápis. */
	private synchronized SqlJetDb otevri(final File f) throws SqlJetException {
		zamek.writeLock().lock();
		try {
			return otevriZamcene(f);
		} finally {
			zamek.writeLock().unlock();
		}
	}

	private SqlJetDb otevriZamcene(final File f) throws SqlJetException {
		final SqlJetDb database = SqlJetDb.open(f, true);
		try {
			// Poškozený soubor ohlásí CORRUPT nebo NOTADB už při čtení schématu.
			database.getSchema();
			if (!isDbInitialized(database)) {
				initDb(database);
			}
			if (isDbInitialized(database)) {
				return database;
			}
		} catch (final SqlJetException e) {
			zavri(database);
			throw e;
		}
		zavri(database);
		throw new SqlJetException(SqlJetErrorCode.ERROR, "Cache dlaždic " + f + " nelze použít.");
	}

	/**
	 * Na poškozený soubor narazí často víc vláken najednou. Odkládá se proto jen v jednom a až po novém pokusu o otevření, jinak by se
	 * odložila i nová cache, kterou mezitím založilo jiné vlákno.
	 */
	private synchronized SqlJetDb otevriPoskozenou(final File f, final SqlJetException chyba) {
		if (!jePoskozena(chyba)) {
			neslaOtevrit(f, chyba);
			return null;
		}
		try {
			return otevri(f);
		} catch (final SqlJetException e) {
			if (!jePoskozena(e) || !odlozVadnouCache(f)) {
				neslaOtevrit(f, e);
				return null;
			}
		}
		try {
			return otevri(f);
		} catch (final SqlJetException e) {
			neslaOtevrit(f, e);
			return null;
		}
	}

	/** Opakované selhání znamená, že cache nejde použít (odpojený disk, chybějící práva), a to se uživateli řekne jednou. */
	private synchronized void neslaOtevrit(final File f, final SqlJetException e) {
		chybyOtevreni.ohlas(e);
		if (++neuspesnychOtevreniZaSebou == 3 && !uzivatelUpozornen) {
			uzivatelUpozornen = true;
			SwingUtilities.invokeLater(() -> Dlg.upozorneni("Cache dlaždic ve složce " + f.getParent() + " nejde použít, mapy se budou pokaždé stahovat znovu.\n"
					+ "Složku můžete změnit v menu Soubor > Umístění souborů."));
		}
	}

	/** Jen skutečně poškozený soubor se smí odložit, ne chyba čtení nebo přerušené vlákno. */
	private static boolean jePoskozena(final SqlJetException e) {
		return e.getErrorCode() == SqlJetErrorCode.CORRUPT || e.getErrorCode() == SqlJetErrorCode.NOTADB;
	}

	/** Poškozená cache je k ničemu, odložíme ji a založíme prázdnou. */
	private boolean odlozVadnouCache(final File f) {
		if (!f.isFile()) {
			return false;
		}
		final File vadna = new File(f.getPath() + ".vadna");
		vadna.delete();
		if (!f.renameTo(vadna) && !f.delete()) {
			log.error("Poškozenou cache dlaždic {} nelze odložit.", f);
			return false;
		}
		log.warn("Poškozená cache dlaždic {} odložena, zakládám novou.", f);
		return true;
	}

	/**
	 * Initializes the database (if needed)
	 *
	 * @param connection
	 *            A connection to the database.
	 */
	private synchronized void initDb(final SqlJetDb connection) {
		// Another thread might have already done this, so check it once again
		if (!isDbInitialized(connection)) {
			// load and initialize the new DB
			try {
				connection.runWriteTransaction(sqlJetDb -> {
					sqlJetDb.createTable(TABLE_CREATE_QUERY);
					return true;
				});
			} catch (final SqlJetException e) {
				log.error("A database error has occurred!", e);
			} finally {
				try {
					connection.commit();
				} catch (final SqlJetException e) {
					log.error("Couldn't commit to the database!", e);
				}
			}
		}
	}

	/**
	 * Checks whether the DB at the current location is initialized and ready for use.
	 *
	 * @param connection
	 *            A connection to the database.
	 * @return True if its initialized, false otherwise
	 */
	private boolean isDbInitialized(final SqlJetDb connection) {
		try {
			return connection.getSchema().getTableNames().contains(TABLE_NAME);
		} catch (final SqlJetException e) {
			log.error("A database error has occurred!", e);
			return false;
		}
	}
}
