package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import lombok.extern.slf4j.Slf4j;

/**
 * Optimalizace databáze cache dlaždic: živé řádky se zkopírují do nového souboru se stránkou 4096 bez volných stránek a ten nahradí původní.
 * Po dobu běhu je cache zavřená; původní soubor se mění až poslední výměnou.
 */
@Slf4j
public final class OptimalizaceDatabaze {

	/** Doporučení se ukazuje, když je volných stránek víc než tento podíl a soubor je větší než {@link #PRAH_VELIKOSTI}. */
	public static final double PRAH_VOLNYCH = 0.30;

	public static final long PRAH_VELIKOSTI = 200L << 20;

	/** Rezerva na disku navíc k živým datům. */
	private static final double REZERVA_MISTA = 1.05;

	/** Kopie jednoho GB živých dat trvá na rychlém disku asi 30 s, odhad počítá s pomalejším. */
	private static final double SEKUND_NA_GB = 40;

	private static final int POCET_POKUSU_VYMENY = 3;

	private static final int DAVKA = 2000;

	public enum Doporuceni {
		/** Cache je v pořádku nebo malá. */
		NENI,
		DOPORUCIT,
		/** Optimalizace by byla vhodná, ale na disku není dost místa. */
		MALO_MISTA
	}

	public enum Vysledek {
		HOTOVO,
		ZRUSENO
	}

	/** Chyba optimalizace; cache zůstala beze změny. */
	public static final class OptimalizaceChyba extends RuntimeException {
		private static final long serialVersionUID = 1L;

		OptimalizaceChyba(final String zprava) {
			super(zprava);
		}

		OptimalizaceChyba(final String zprava, final Throwable pricina) {
			super(zprava, pricina);
		}
	}

	/** Zpětné volání průběhu; voláno z vlákna optimalizace. */
	public interface Prubeh {
		void zpracovano(long hotovo, long celkem);

		boolean zruseno();
	}

	/** Přesunutí nového souboru na místo původního. */
	@FunctionalInterface
	interface Presun {
		void presun(Path odkud, Path kam) throws IOException;
	}

	private final KachleDBManager manager;

	private Presun presun = (odkud, kam) -> Files.move(odkud, kam, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

	private long pauzaMezipokusy = 1000;

	private java.util.function.ToLongFunction<File> volneMistoPodSlozkou = File::getUsableSpace;

	OptimalizaceDatabaze(final KachleDBManager manager) {
		this.manager = manager;
	}

	void setPresun(final Presun presun) {
		this.presun = presun;
	}

	void setVolneMisto(final java.util.function.ToLongFunction<File> volne) {
		volneMistoPodSlozkou = volne;
	}

	void setPauzaMezipokusy(final long ms) {
		pauzaMezipokusy = ms;
	}

	/** Stav souboru cache z hlavičky; null, když cache není. */
	public StavSouboruCache stav() {
		final File f = manager.souborCache();
		return f == null ? null : StavSouboruCache.precti(f);
	}

	public long volneMisto() {
		final File f = manager.souborCache();
		return f == null ? -1 : volneMistoPodSlozkou.applyAsLong(f.getParentFile());
	}

	/** Doporučení podle stavu souboru a volného místa na disku (záporné = neznámé, bere se jako dost). */
	public static Doporuceni posud(final StavSouboruCache stav, final long volneMisto) {
		if (stav == null || stav.velikost() <= PRAH_VELIKOSTI || stav.podilVolnych() <= PRAH_VOLNYCH) {
			return Doporuceni.NENI;
		}
		return volneMisto >= 0 && volneMisto < potrebneMisto(stav) ? Doporuceni.MALO_MISTA : Doporuceni.DOPORUCIT;
	}

	/** Volné místo, které optimalizace potřebuje: živá data s rezervou a bezpečná hranice zápisu do cache. */
	public static long potrebneMisto(final StavSouboruCache stav) {
		return (long) (stav.zivaData() * REZERVA_MISTA) + KachleDBManager.MIN_VOLNE_MISTO;
	}

	/** Odhad trvání v minutách, nejméně jedna. */
	public static int odhadMinut(final StavSouboruCache stav) {
		final double gb = stav.zivaData() / (double) (1L << 30);
		return (int) Math.max(1, Math.ceil(gb * SEKUND_NA_GB / 60));
	}

	/**
	 * Provede optimalizaci. Cache je po dobu běhu zavřená. Při zrušení i při chybě zůstává původní soubor beze změny.
	 *
	 * @throws OptimalizaceChyba
	 *             když optimalizace nešla provést; zpráva je určená uživateli
	 */
	public Vysledek spust(final Prubeh prubeh) {
		final File puvodni = manager.souborCache();
		final StavSouboruCache stav = puvodni == null ? null : StavSouboruCache.precti(puvodni);
		if (stav == null) {
			throw new OptimalizaceChyba("Cache dlaždic nejde použít, není co optimalizovat.");
		}
		final long volne = volneMistoPodSlozkou.applyAsLong(puvodni.getParentFile());
		if (volne < potrebneMisto(stav)) {
			throw new OptimalizaceChyba(String.format("Na disku chybí místo: je potřeba asi %d MB, volných je %d MB.", potrebneMisto(stav) >> 20, volne >> 20));
		}
		final File nova = new File(puvodni.getPath() + ".new");
		if (!manager.pozastav()) {
			throw new OptimalizaceChyba("Cache dlaždic se právě kontroluje po nedokončeném zápisu, zkuste to za chvíli.");
		}
		try {
			uklid(nova);
			zkusZamek(puvodni);
			final boolean dokonceno = zkopiruj(puvodni, nova, prubeh);
			if (!dokonceno) {
				uklid(nova);
				return Vysledek.ZRUSENO;
			}
			overNovou(nova);
			vymen(nova, puvodni);
			return Vysledek.HOTOVO;
		} catch (final RuntimeException e) {
			uklid(nova);
			throw e;
		} finally {
			manager.obnov();
		}
	}

	private static void uklid(final File nova) {
		smaz(nova);
		smaz(new File(nova.getPath() + "-journal"));
	}

	private static void smaz(final File soubor) {
		try {
			Files.deleteIfExists(soubor.toPath());
		} catch (final IOException e) {
			log.warn("Soubor {} nejde smazat: {}", soubor, e.toString());
		}
	}

	/** Cizí program (druhý GeoKuk) může soubor zamykat; zjistí se dřív, než se začne kopírovat. */
	private static void zkusZamek(final File puvodni) {
		try {
			final SqlJetDb db = SqlJetDb.open(puvodni, true);
			try {
				db.beginTransaction(SqlJetTransactionMode.WRITE);
				db.rollback();
			} finally {
				db.close();
			}
		} catch (final SqlJetException e) {
			if (e.getErrorCode() == SqlJetErrorCode.BUSY) {
				throw new OptimalizaceChyba("Cache dlaždic používá jiný program (například druhý GeoKuk), zavřete ho a zkuste to znovu.", e);
			}
			throw new OptimalizaceChyba("Cache dlaždic nejde otevřít: " + e.getMessage(), e);
		}
	}

	/** Kopie v pořadí klíče; false, když ji uživatel zrušil. */
	static boolean zkopiruj(final File zdroj, final File cil, final Prubeh prubeh) {
		SqlJetDb ze = null;
		CacheDb do_ = null;
		try {
			ze = SqlJetDb.open(zdroj, false);
			final ISqlJetTable tz = ze.getTable("tiles");
			ze.beginTransaction(SqlJetTransactionMode.READ_ONLY);
			final ISqlJetCursor radky = tz.order(tz.getPrimaryKeyIndexName());
			final long celkem = radky.getRowCount();
			do_ = CacheDb.otevri(cil, true);
			do_.beginTransaction(SqlJetTransactionMode.WRITE);
			do_.createTable("CREATE TABLE tiles (x int, y int, z int, s varchar(10), image blob, PRIMARY KEY(x, y, z, s))");
			do_.commit();
			final ISqlJetTable tc = do_.getTable("tiles");
			long hotovo = 0;
			do_.beginTransaction(SqlJetTransactionMode.WRITE);
			while (!radky.eof()) {
				tc.insertOr(SqlJetConflictAction.REPLACE, radky.getInteger("x"), radky.getInteger("y"), radky.getInteger("z"), radky.getString("s"), radky.getBlobAsArray("image"));
				radky.next();
				if (++hotovo % DAVKA == 0) {
					do_.commit();
					if (prubeh.zruseno()) {
						return false;
					}
					prubeh.zpracovano(hotovo, celkem);
					do_.beginTransaction(SqlJetTransactionMode.WRITE);
				}
			}
			do_.commit();
			radky.close();
			ze.commit();
			prubeh.zpracovano(celkem, celkem);
			return !prubeh.zruseno();
		} catch (final SqlJetException e) {
			throw new OptimalizaceChyba("Cache dlaždic nejde zkopírovat: " + e.getMessage(), e);
		} finally {
			zavri(ze);
			zavri(do_);
		}
	}

	private void overNovou(final File nova) {
		SqlJetDb db = null;
		try {
			db = SqlJetDb.open(nova, false);
			manager.projdi(db);
		} catch (final SqlJetException e) {
			throw new OptimalizaceChyba("Nová cache dlaždic nevyšla, původní zůstává beze změny: " + e.getMessage(), e);
		} finally {
			zavri(db);
		}
	}

	private void vymen(final File nova, final File puvodni) {
		// Zbylý žurnál původního souboru by se po výměně použil na nový.
		smaz(new File(puvodni.getPath() + "-journal"));
		IOException posledni = null;
		for (int pokus = 1; pokus <= POCET_POKUSU_VYMENY; pokus++) {
			try {
				presun.presun(nova.toPath(), puvodni.toPath());
				return;
			} catch (final IOException e) {
				posledni = e;
				log.debug("Výměna cache dlaždic, pokus {}: {}", pokus, e.toString());
				if (pokus < POCET_POKUSU_VYMENY) {
					try {
						Thread.sleep(pauzaMezipokusy);
					} catch (final InterruptedException e1) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}
		throw new OptimalizaceChyba("Soubor cache používá jiný program (třeba antivirus), původní cache zůstala beze změny. Zkuste to znovu později.", posledni);
	}

	private static void zavri(final SqlJetDb db) {
		if (db != null) {
			try {
				db.close();
			} catch (final SqlJetException e) {
				log.debug("Spojení nejde zavřít.", e);
			}
		}
	}
}
