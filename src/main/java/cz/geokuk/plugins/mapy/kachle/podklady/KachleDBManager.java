package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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

	/** Cache nejde použít (složka, otevření), dlaždice zůstanou jen v paměti. */
	private static final SqlJetException BEZ_CACHE = new SqlJetException(SqlJetErrorCode.CANTOPEN);

	/** Kolikrát se zkusí zápis, když cache zamyká jiný program. */
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
	final ReentrantReadWriteLock zamek = new ReentrantReadWriteLock(true);

	private static final long ZNOVU_ZKUSIT_ZAPIS_MS = 60_000;

	private final Set<File> zapisovatelneSlozky = ConcurrentHashMap.newKeySet();

	/** Nezapisovatelnou složku (třeba odpojený disk) zkouší po chvíli znovu. */
	private final Map<File, Long> nezapisovatelneDo = new ConcurrentHashMap<>();

	private boolean uzivatelUpozornen;

	/** Volné místo, pod které se do cache nezapisuje; plný disk cache poškodí. */
	static final long MIN_VOLNE_MISTO = 100L << 20;

	private boolean upozornenoNaMisto;

	/** Zvýší se při odložení poškozené cache, aby chyba starého spojení neodložila i novou cache. */
	volatile int odlozeni;

	/** Cache, ve které po pádu programu zůstal rozepsaný zápis; před prvním zápisem se zkontroluje. */
	private final Set<File> kOvereni = ConcurrentHashMap.newKeySet();

	/** Běžící kontroly cache po pádu; zápis do kontrolované cache počká, čtení ne. */
	final Map<File, CountDownLatch> kontroly = new ConcurrentHashMap<>();

	/** Kontrola po pádu ještě běží, zápis se zopakuje po jejím dokončení. */
	private static final SqlJetException KONTROLA_BEZI = new SqlJetException(SqlJetErrorCode.BUSY);

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
		final int odlozeniPred = odlozeni;
		final SqlJetDb database = getDatabaseConnection();
		if (database == null) {
			return null;
		}
		byte[] data = null;
		boolean poskozena = false;
		zamek.readLock().lock();
		try {
			if (odlozeniPred != odlozeni) {
				return null; // spojení k odložené cache je zavřené
			}
			data = nactiData(database, ki);
		} catch (final RuntimeException e) {
			if (!(e.getCause() instanceof SqlJetException) || !jePoskozena((SqlJetException) e.getCause())) {
				throw e;
			}
			poskozena = true;
		} finally {
			zamek.readLock().unlock();
		}
		if (poskozena) {
			// Odložit jde až po uvolnění čtecího zámku; dlaždice se zatím stáhne znovu.
			odlozZaBehu(database.getFile(), odlozeniPred);
			return null;
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
			if (e.getErrorCode() == SqlJetErrorCode.BUSY) {
				// Cache zamyká zápis jiného programu; dlaždice se zatím stáhne, spojení zůstává použitelné.
				log.debug("Cache dlaždic je zamčená jiným programem, {} se stáhne.", ki);
				return null;
			}
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
		for (int pokus = 1;; pokus++) {
			final SqlJetException chyba = zapisJednou(imagesToSave);
			if (chyba == null) {
				return true;
			}
			if (chyba == KONTROLA_BEZI) {
				if (!pockejNaKontroly()) {
					return false;
				}
				pokus--;
				continue;
			}
			if (chyba == BEZ_CACHE) {
				return false;
			}
			if (chyba.getErrorCode() != SqlJetErrorCode.BUSY || pokus >= POKUSU_O_ZAPIS) {
				chybyZapisu.ohlas(chyba);
				return false;
			}
			log.debug("Cache dlaždic je zamčená jiným programem, zápis zkusím znovu ({}. pokus)", pokus);
			// Čekání bez zámků, aby mezitím mohlo číst tohle i jiné vlákno.
			try {
				Thread.sleep(20L * pokus);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				chybyZapisu.ohlas(chyba);
				return false;
			}
		}
	}

	/** Jeden pokus o zápis; vrátí chybu, {@link #BEZ_CACHE}, nebo null, když se zapsalo. */
	private SqlJetException zapisJednou(final Collection<ItemToSave> imagesToSave) {
		// Do SQLite zapisuje vždy jen jedno spojení; souběžné zápisy by si navzájem vracely BUSY.
		synchronized (this) {
			zamek.writeLock().lock();
			try {
				final SqlJetDb database = getDatabaseConnection();
				if (database == null) {
					return BEZ_CACHE;
				}
				if (kontroly.containsKey(database.getFile())) {
					return KONTROLA_BEZI;
				}
				if (!dostMista(database.getFile().getParentFile(), imagesToSave)) {
					return BEZ_CACHE;
				}
				try {
					zapis(database, imagesToSave);
					return null;
				} catch (final SqlJetException e) {
					if (e.getErrorCode() != SqlJetErrorCode.BUSY) {
						zahod(database);
					}
					if (jePoskozena(e)) {
						odlozZaBehu(database.getFile(), odlozeni);
					}
					return e;
				}
			} finally {
				zamek.writeLock().unlock();
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

	/** Vrátí false, když bylo vlákno při čekání přerušeno. */
	private boolean pockejNaKontroly() {
		try {
			for (final CountDownLatch kontrola : kontroly.values()) {
				kontrola.await();
			}
			return true;
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/** Při nedostatku místa se dlaždice neukládají a uživatel se to dozví jednou. */
	private boolean dostMista(final File slozka, final Collection<ItemToSave> imagesToSave) {
		long potreba = MIN_VOLNE_MISTO;
		for (final ItemToSave item : imagesToSave) {
			// Žurnál drží kopie přepisovaných stránek, proto dvakrát.
			potreba += 2L * item.imageData.length;
		}
		final long volne = volneMisto(slozka);
		if (volne < 0 || volne >= potreba) {
			return true;
		}
		if (upozornenoNaMisto) {
			return false;
		}
		upozornenoNaMisto = true;
		upozorniNaMisto(slozka, volne);
		return false;
	}

	void upozorniNaMisto(final File slozka, final long volne) {
		log.warn("Na disku se složkou cache dlaždic {} zbývá {} MB, dlaždice se do cache neukládají.", slozka, volne >> 20);
		if (!GraphicsEnvironment.isHeadless()) {
			SwingUtilities.invokeLater(() -> Dlg.upozorneni("Na disku se složkou " + slozka + " dochází místo, mapy se přestaly ukládat do cache.\n"
					+ "Po uvolnění místa se začnou ukládat znovu."));
		}
	}

	/** Volné místo v bajtech, záporné, když ho systém nezjistí. */
	long volneMisto(final File slozka) {
		final long volne = slozka.getUsableSpace();
		return volne == 0 && slozka.getTotalSpace() == 0 ? -1 : volne;
	}

	/**
	 * Poškození zjištěné při čtení nebo zápisu: zavřou se všechna spojení a cache se odloží. Odkládá se jen cache, ve které chyba
	 * vznikla, ne nová, kterou mezitím založilo jiné vlákno.
	 */
	synchronized void odlozZaBehu(final File f, final int odlozeniPred) {
		zamek.writeLock().lock();
		try {
			if (odlozeniPred != odlozeni) {
				return;
			}
			for (final Iterator<Map.Entry<Map.Entry<Thread, File>, SqlJetDb>> it = connections.entrySet().iterator(); it.hasNext();) {
				final Map.Entry<Map.Entry<Thread, File>, SqlJetDb> conn = it.next();
				if (conn.getKey().getValue().equals(f)) {
					it.remove();
					zavri(conn.getValue());
				}
			}
			odlozVadnouCache(f);
		} finally {
			zamek.writeLock().unlock();
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
		final boolean poprve;
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
			poprve = nezapisovatelnaDo == null;
			nezapisovatelneDo.put(slozka, System.currentTimeMillis() + ZNOVU_ZKUSIT_ZAPIS_MS);
		}
		// Mimo zámek složky: upozornění bere zámek manažeru a ten může držet zápis, který čeká na zámek složky.
		if (poprve) {
			log.warn("Do složky cache dlaždic {} nelze zapisovat, dlaždice zůstanou jen v paměti.", slozka);
			upozorniNaZapis(slozka);
		}
		return false;
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
		if (zurnal(f).isFile()) {
			kOvereni.add(f);
		}
		final SqlJetDb database = SqlJetDb.open(f, true);
		try {
			// Poškozený soubor ohlásí CORRUPT nebo NOTADB už při čtení schématu.
			database.getSchema();
			if (!isDbInitialized(database)) {
				initDb(database);
			}
			if (isDbInitialized(database)) {
				if (kOvereni.remove(f)) {
					spustKontrolu(f);
				}
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
		// Odkládá se soubor, ze kterého mohou jiná vlákna číst.
		zamek.writeLock().lock();
		try {
			return otevriPoskozenouZamcene(f, chyba);
		} finally {
			zamek.writeLock().unlock();
		}
	}

	private SqlJetDb otevriPoskozenouZamcene(final File f, final SqlJetException chyba) {
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
					+ "Zkontrolujte disk a práva ke složce, cache můžete i smazat."));
		}
	}

	private static File zurnal(final File f) {
		return new File(f.getPath() + "-journal");
	}

	/** Velká cache se prochází i desítky sekund, proto vlastním spojením na pozadí; dlaždice se mezitím čtou. */
	private void spustKontrolu(final File f) {
		final CountDownLatch hotovo = new CountDownLatch(1);
		kontroly.put(f, hotovo);
		final int odlozeniPred = odlozeni;
		try {
			spustVlakno(() -> {
				try {
					zkontroluj(f, odlozeniPred);
				} finally {
					kontroly.remove(f);
					hotovo.countDown();
				}
			});
		} catch (final Throwable e) {
			// Bez kontroly by zápis čekal navždy; cache se použije nezkontrolovaná.
			kontroly.remove(f);
			hotovo.countDown();
			log.warn("Kontrolu cache dlaždic {} nejde spustit: {}", f, e.toString());
		}
	}

	void spustVlakno(final Runnable kontrola) {
		final Thread vlakno = new Thread(kontrola, "Kontrola cache dlaždic");
		vlakno.setDaemon(true);
		vlakno.start();
	}

	private void zkontroluj(final File f, final int odlozeniPred) {
		final Map.Entry<Thread, File> klic = new AbstractMap.SimpleImmutableEntry<>(Thread.currentThread(), f);
		log.info("Kontroluji cache dlaždic {} po nedokončeném zápisu.", f);
		try {
			final SqlJetDb database = SqlJetDb.open(f, false);
			// Mezi spojeními, aby ho při odložení cache zavřelo i čtení, které poškození najde dřív.
			connections.put(klic, database);
			try {
				projdi(database);
				log.info("Cache dlaždic {} je v pořádku.", f);
			} finally {
				if (connections.remove(klic) != null) {
					zavri(database);
				}
			}
		} catch (final SqlJetException e) {
			if (jePoskozena(e)) {
				odlozZaBehu(f, odlozeniPred);
			} else {
				log.warn("Cache dlaždic {} nejde zkontrolovat: {}", f, e.toString());
			}
		} catch (final RuntimeException e) {
			log.warn("Cache dlaždic {} nejde zkontrolovat: {}", f, e.toString());
		}
	}

	/** Pád uprostřed zápisu může cache poškodit; průchod tabulkou a indexem bez obrázků poškození většinou najde. */
	void projdi(final SqlJetDb database) throws SqlJetException {
		final ISqlJetTable table = database.getTable(TABLE_NAME);
		database.beginTransaction(SqlJetTransactionMode.READ_ONLY);
		try {
			projdi(table.open());
			projdi(table.order(table.getPrimaryKeyIndexName()));
		} finally {
			database.commit();
		}
	}

	private static void projdi(final ISqlJetCursor cursor) throws SqlJetException {
		try {
			while (!cursor.eof()) {
				cursor.getRowId();
				cursor.next();
			}
		} finally {
			cursor.close();
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
		odlozeni++;
		final File zurnal = zurnal(f);
		if (zurnal.isFile()) {
			// Žurnál k odložené cache by se jinak použil na novou.
			final File vadnyZurnal = new File(vadna.getPath() + "-journal");
			vadnyZurnal.delete();
			if (!zurnal.renameTo(vadnyZurnal)) {
				zurnal.delete();
			}
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
