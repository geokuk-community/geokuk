package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.*;

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
		Image img = null;
		ISqlJetCursor cursor = null;
		boolean vadne = false;

		try {
			final ISqlJetTable table = database.getTable(TABLE_NAME);
			database.beginTransaction(SqlJetTransactionMode.READ_ONLY);
			cursor = table.lookup(table.getPrimaryKeyIndexName(), ki.getLoc().getFromSzUnsignedX(), ki.getLoc().getFromSzUnsignedY(), ki.getLoc().getMoumer(), ki.typToString());
			if (cursor.eof()) {
				return null;
			}
			log.debug("{} : {} {} {} {} loading from DB", cursor.getRowId(), cursor.getInteger("x"), cursor.getInteger("y"), cursor.getInteger("z"), cursor.getString("s"));
			img = ImageIO.read(cursor.getBlobAsStream("image"));
			if (img == null) {
				log.debug("Loaded DB image is null!");
			}
		} catch (SqlJetException | IOException e) {
			chybyCteni.ohlas(e);
			vadne = e instanceof SqlJetException;
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
		return img;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean save(final Collection<ItemToSave> imagesToSave) {
		final SqlJetDb database = getDatabaseConnection();
		if (database == null) {
			return false;
		}

		// in case something goes wrong, rollback the transaction
		boolean failed = false;

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
		} catch (final SqlJetException e) {
			chybyZapisu.ohlas(e);
			failed = true;
		} finally {
			try {
				if (failed) {
					database.rollback();
				} else {
					database.commit();
				}
			} catch (final SqlJetException e) {
				chybyDokonceni.ohlas(e);
				failed = true;
			}
			if (failed) {
				zahod(database);
			}
		}
		return !failed;
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

		connections.put(mapKey, database);
		return database;
	}

	private SqlJetDb otevri(final File f) throws SqlJetException {
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
			chybyOtevreni.ohlas(chyba);
			return null;
		}
		try {
			return otevri(f);
		} catch (final SqlJetException e) {
			if (!jePoskozena(e) || !odlozVadnouCache(f)) {
				chybyOtevreni.ohlas(e);
				return null;
			}
		}
		try {
			return otevri(f);
		} catch (final SqlJetException e) {
			chybyOtevreni.ohlas(e);
			return null;
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
