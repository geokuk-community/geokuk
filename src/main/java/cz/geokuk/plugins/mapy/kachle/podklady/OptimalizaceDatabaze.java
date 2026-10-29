package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;

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

	/** Zastaralé dlaždice offline map stačí k doporučení, i když volných stránek není dost. */
	public static final long PRAH_STARYCH = 500L << 20;

	private static final String SOUBOR_STARYCH = "stare-dlazdice.txt";

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

		/** Objem dosud zkopírovaných a vynechaných (zastaralých) dlaždic v bajtech; volá se těsně před {@link #zpracovano}. */
		default void objem(final long zkopirovano, final long vynechano) {
			// Průběh objem nepotřebuje.
		}
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
		return posud(stav, StaraData.ZADNA, volneMisto);
	}

	/**
	 * Doporučení; zastaralé dlaždice se počítají mezi prázdné stránky, protože optimalizace je vynechá. Samy stačí, když jich je {@link #PRAH_STARYCH}.
	 */
	public static Doporuceni posud(final StavSouboruCache stav, final StaraData stare, final long volneMisto) {
		if (stav == null || stav.velikost() <= PRAH_VELIKOSTI) {
			return Doporuceni.NENI;
		}
		final StavSouboruCache po = stav.bezStarych(stare.bajtu());
		if (po.podilVolnych() <= PRAH_VOLNYCH && stare.bajtu() < PRAH_STARYCH) {
			return Doporuceni.NENI;
		}
		return volneMisto >= 0 && volneMisto < potrebneMisto(po) ? Doporuceni.MALO_MISTA : Doporuceni.DOPORUCIT;
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

	/** Složka cache dlaždic, nebo null. */
	private File slozkaCache() {
		final File f = manager.souborCache();
		return f == null ? null : f.getParentFile();
	}

	/** Klíče offline dlaždic, které zůstávají; prázdné, když soubory klíčů nejdou přečíst (pak se nic nepovažuje za zastaralé). */
	private Set<String> ponechane() {
		final File slozka = slozkaCache();
		if (slozka == null) {
			return Collections.emptySet();
		}
		try {
			return UklidOfflineCache.ponechaneVCache(slozka);
		} catch (final IOException | RuntimeException e) {
			log.warn("Soubory klíčů offline map nejdou přečíst, zastaralé dlaždice se nevynechají: {}", e.toString());
			return Collections.emptySet();
		}
	}

	/** Zapamatovaný odhad zastaralých dlaždic pro současné soubory klíčů, nebo null, když ještě nebyl spočítán. */
	public StaraData zapamatovaneStare() {
		final File slozka = slozkaCache();
		final File f = manager.souborCache();
		if (slozka == null || f == null) {
			return StaraData.ZADNA;
		}
		final Set<String> ponechat = ponechane();
		if (ponechat.isEmpty()) {
			return StaraData.ZADNA;
		}
		final StaraData stare = StaraData.nacti(new File(slozka, SOUBOR_STARYCH), StaraData.otiskPlanu(ponechat));
		// Odhad většího objemu, než kolik je v souboru, patří k jiné cache.
		return stare != null && stare.bajtu() <= f.length() ? stare : null;
	}

	/**
	 * Spočítá zastaralé dlaždice průchodem tabulkou ve vlákně volajícího (v nízké prioritě) a výsledek zapamatuje; platí, dokud se soubory klíčů nezmění.
	 * Soubor cache se jen čte, zápis dávek na chvíli počká na sdílený zámek.
	 */
	public StaraData spocitejStare() {
		final File f = manager.souborCache();
		final Set<String> ponechat = ponechane();
		if (f == null || ponechat.isEmpty() || !f.isFile()) {
			return StaraData.ZADNA;
		}
		final long zacatek = System.nanoTime();
		try {
			final StaraData stare = StaraData.spocti(f, ponechat);
			stare.uloz(new File(f.getParentFile(), SOUBOR_STARYCH));
			log.info("Cache dlaždic: zastaralých dlaždic offline map {} ({} MB), zjištěno za {} ms", stare.dlazdic(), stare.bajtu() >> 20, (System.nanoTime() - zacatek) / 1_000_000);
			return stare;
		} catch (final SQLException e) {
			log.warn("Objem zastaralých dlaždic nejde zjistit: {}", e.toString());
			return StaraData.ZADNA;
		}
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
		final Set<String> ponechat = ponechane();
		final StaraData znama = zapamatovaneStare();
		final long volne = volneMistoPodSlozkou.applyAsLong(puvodni.getParentFile());
		final long potreba = potrebneMisto(znama == null ? stav : stav.bezStarych(znama.bajtu()));
		if (volne < potreba) {
			throw new OptimalizaceChyba(String.format("Na disku chybí místo: je potřeba asi %d MB, volných je %d MB.", potreba >> 20, volne >> 20));
		}
		final File nova = new File(puvodni.getPath() + ".new");
		if (!manager.pozastav()) {
			throw new OptimalizaceChyba("Cache dlaždic se právě kontroluje po nedokončeném zápisu, zkuste to za chvíli.");
		}
		final long zacatek = System.nanoTime();
		log.info("Optimalizace cache dlaždic: začátek, soubor {}, zastaralých dlaždic podle odhadu {}", stav, znama == null ? "neznámo" : znama.dlazdic() + " (" + (znama.bajtu() >> 20) + " MB)");
		try {
			uklid(nova);
			zkusZamek(puvodni);
			final Soucty soucty = zkopiruj(puvodni, nova, ponechat, prubeh);
			if (soucty == null) {
				uklid(nova);
				log.info("Optimalizace cache dlaždic: zrušena po {} s, cache beze změny", (System.nanoTime() - zacatek) / 1_000_000_000L);
				return Vysledek.ZRUSENO;
			}
			overNovou(nova);
			vymen(nova, puvodni);
			new StaraData(0, 0, StaraData.otiskPlanu(ponechat)).uloz(new File(puvodni.getParentFile(), SOUBOR_STARYCH));
			final StavSouboruCache po = StavSouboruCache.precti(puvodni);
			log.info("Optimalizace cache dlaždic: hotovo za {} s, soubor {} MB -> {} MB, zkopírováno {} dlaždic ({} MB), odstraněno zastaralých {} ({} MB)", (System.nanoTime() - zacatek) / 1_000_000_000L,
					stav.velikost() >> 20, po == null ? "?" : po.velikost() >> 20, soucty.zkopirovano, soucty.bajtuZkopirovano >> 20, soucty.vynechano, soucty.bajtuVynechano >> 20);
			return Vysledek.HOTOVO;
		} catch (final RuntimeException e) {
			uklid(nova);
			log.warn("Optimalizace cache dlaždic selhala po {} s: {}", (System.nanoTime() - zacatek) / 1_000_000_000L, e.toString());
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

	/** Počty a objem dlaždic kopie. */
	static final class Soucty {
		long zkopirovano;
		long bajtuZkopirovano;
		long vynechano;
		long bajtuVynechano;
	}

	/** Kopie v pořadí klíče bez dlaždic zastaralých klíčů; null, když ji uživatel zrušil. */
	static Soucty zkopiruj(final File zdroj, final File cil, final Set<String> ponechat, final Prubeh prubeh) {
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
			final Soucty soucty = new Soucty();
			long hotovo = 0;
			do_.beginTransaction(SqlJetTransactionMode.WRITE);
			while (!radky.eof()) {
				final byte[] obraz = radky.getBlobAsArray("image");
				if (UklidOfflineCache.jeZastarala(radky.getString("s"), ponechat)) {
					soucty.vynechano++;
					soucty.bajtuVynechano += obraz == null ? 0 : obraz.length;
				} else {
					tc.insertOr(SqlJetConflictAction.REPLACE, radky.getInteger("x"), radky.getInteger("y"), radky.getInteger("z"), radky.getString("s"), obraz);
					soucty.zkopirovano++;
					soucty.bajtuZkopirovano += obraz == null ? 0 : obraz.length;
				}
				radky.next();
				if (++hotovo % DAVKA == 0) {
					do_.commit();
					if (prubeh.zruseno()) {
						return null;
					}
					prubeh.objem(soucty.bajtuZkopirovano, soucty.bajtuVynechano);
					prubeh.zpracovano(hotovo, celkem);
					do_.beginTransaction(SqlJetTransactionMode.WRITE);
				}
			}
			do_.commit();
			radky.close();
			ze.commit();
			prubeh.objem(soucty.bajtuZkopirovano, soucty.bajtuVynechano);
			prubeh.zpracovano(celkem, celkem);
			return prubeh.zruseno() ? null : soucty;
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
