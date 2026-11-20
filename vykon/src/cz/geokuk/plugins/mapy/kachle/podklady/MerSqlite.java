package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.sqlite.SQLiteConfig;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

/**
 * Měření SqlJet × sqlite-jdbc nad cache dlaždic na syntetickém souboru, určené pro Windows s HDD (spouští {@code vykon/sqlite.sh}). Pracuje jen se souborem
 * {@code mer-sqlite-*.sqlite}, který si sám založí; jiný soubor odmítne, aby se nikdy nedotklo skutečné cache. Příkazy:
 * {@code gen SOUBOR MB}, {@code otevreni SOUBOR knihovna}, {@code cteni SOUBOR knihovna vlaken dotazu}, {@code okno SOUBOR knihovna obrazovek},
 * {@code smiseny SOUBOR knihovna ctenaru davka sekund}, {@code zapis SOUBOR knihovna davka davek}; knihovna je {@code sqljet} nebo {@code jdbc}.
 * Všude journal DELETE (bez WAL), stejně jako cache programu.
 */
public final class MerSqlite {

	static final String TABLE = "tiles";
	static final int PAGE = 4096;
	static final byte[] POOL = new byte[8 << 20];
	static {
		new Random(1).nextBytes(POOL);
	}

	

	static byte[] blob(final Random r) {
		final int len = 15_000 + r.nextInt(25_000);
		final int od = r.nextInt(POOL.length - len);
		return Arrays.copyOfRange(POOL, od, od + len);
	}

	/** Klíč dlaždice podle pořadí: mřížka 512 × N, dvě vrstvy. */
	static int[] klic(final int i, final int vrstev) {
		final int v = i % vrstev;
		final int j = i / vrstev;
		return new int[] { j % 512, j / 512, 14, v };
	}

	static String typ(final int v) {
		return v == 0 ? "TURIST_M" : "OPHOTO_M";
	}

	// ---- SqlJet

	static SqlJetDb sqljet(final File f, final boolean zapis) throws Exception {
		return CacheDb.otevri(f, zapis);
	}

	static void genSqljet(final File f, final int pocet) throws Exception {
		if (f.exists()) {
			throw new IllegalStateException("Soubor " + f + " už existuje; nástroj ho nepřepisuje (smaž ho ručně, je-li to měřicí soubor)");
		}
		final SqlJetDb db = sqljet(f, true);
		db.runWriteTransaction(d -> {
			d.getSchema();
			return null;
		});
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		db.getSchema();
		db.createTable("CREATE TABLE tiles (x int, y int, z int, s varchar(10), image blob, PRIMARY KEY(x, y, z, s))");
		db.commit();
		final ISqlJetTable t = db.getTable(TABLE);
		final Random r = new Random(7);
		int i = 0;
		while (i < pocet) {
			db.beginTransaction(SqlJetTransactionMode.WRITE);
			for (int k = 0; k < 300 && i < pocet; k++, i++) {
				final int[] kl = klic(i, 2);
				t.insertOr(SqlJetConflictAction.REPLACE, kl[0], kl[1], kl[2], typ(kl[3]), blob(r));
			}
			db.commit();
		}
		db.close();
		try (Connection c = jdbc(f, null, true); Statement st = c.createStatement()) {
			st.execute("PRAGMA application_id=" + ZNACKA);
		}
	}

	// ---- jdbc

	static Connection jdbc(final File f, final String journal, final boolean zapis) throws Exception {
		final SQLiteConfig c = new SQLiteConfig();
		c.setBusyTimeout(5000);
		if (journal != null) {
			c.setJournalMode(SQLiteConfig.JournalMode.valueOf(journal));
		}
		c.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
		if (!zapis) {
			c.setReadOnly(true);
		}
		return DriverManager.getConnection("jdbc:sqlite:" + f.getPath(), c.toProperties());
	}

	interface Ctec {
		byte[] cti(int i) throws Exception;

		void zavri() throws Exception;
	}

	interface Zapisovac {
		void zapis(int odI, int kolik, Random r) throws Exception;

		void zavri() throws Exception;
	}

	static Ctec ctecSqljet(final File f) throws Exception {
		final SqlJetDb db = sqljet(f, true);
		final ISqlJetTable t = db.getTable(TABLE);
		return new Ctec() {
			@Override
			public byte[] cti(final int i) throws Exception {
				final int[] k = klic(i, 2);
				db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
				ISqlJetCursor c = null;
				try {
					c = t.lookup(t.getPrimaryKeyIndexName(), k[0], k[1], k[2], typ(k[3]));
					return c.eof() ? null : c.getBlobAsArray("image");
				} finally {
					if (c != null) {
						c.close();
					}
					db.commit();
				}
			}

			@Override
			public void zavri() throws Exception {
				db.close();
			}
		};
	}

	static Zapisovac zapisovacSqljet(final File f) throws Exception {
		final SqlJetDb db = sqljet(f, true);
		final ISqlJetTable t = db.getTable(TABLE);
		return new Zapisovac() {
			@Override
			public void zapis(final int odI, final int kolik, final Random r) throws Exception {
				db.beginTransaction(SqlJetTransactionMode.WRITE);
				try {
					for (int i = odI; i < odI + kolik; i++) {
						final int[] k = klic(i, 2);
						t.insertOr(SqlJetConflictAction.REPLACE, k[0], k[1], k[2], typ(k[3]), blob(r));
					}
				} finally {
					db.commit();
				}
			}

			@Override
			public void zavri() throws Exception {
				db.close();
			}
		};
	}

	static Ctec ctecJdbc(final File f, final String journal) throws Exception {
		final Connection c = jdbc(f, journal, true);
		final PreparedStatement ps = c.prepareStatement("SELECT image FROM tiles WHERE x=? AND y=? AND z=? AND s=?");
		return new Ctec() {
			@Override
			public byte[] cti(final int i) throws Exception {
				final int[] k = klic(i, 2);
				ps.setInt(1, k[0]);
				ps.setInt(2, k[1]);
				ps.setInt(3, k[2]);
				ps.setString(4, typ(k[3]));
				try (ResultSet rs = ps.executeQuery()) {
					return rs.next() ? rs.getBytes(1) : null;
				}
			}

			@Override
			public void zavri() throws Exception {
				ps.close();
				c.close();
			}
		};
	}

	static Zapisovac zapisovacJdbc(final File f, final String journal) throws Exception {
		final Connection c = jdbc(f, journal, true);
		c.setAutoCommit(false);
		final PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO tiles (x,y,z,s,image) VALUES (?,?,?,?,?)");
		return new Zapisovac() {
			@Override
			public void zapis(final int odI, final int kolik, final Random r) throws Exception {
				for (int i = odI; i < odI + kolik; i++) {
					final int[] k = klic(i, 2);
					ps.setInt(1, k[0]);
					ps.setInt(2, k[1]);
					ps.setInt(3, k[2]);
					ps.setString(4, typ(k[3]));
					ps.setBytes(5, blob(r));
					ps.executeUpdate();
				}
				c.commit();
			}

			@Override
			public void zavri() throws Exception {
				ps.close();
				c.close();
			}
		};
	}

	static long pct(final long[] s, final double p) {
		return s[(int) Math.min(s.length - 1, Math.floor(p * s.length))];
	}

	/** Čtení náhodných dlaždic v jednom vlákně nebo více vláknech, bez zápisu. */
	static void cteni(final File f, final String knihovna, final String journal, final int vlaken, final int rozsah, final int dotazu) throws Exception {
		final List<Thread> vlakna = new ArrayList<>();
		final long[][] lat = new long[vlaken][];
		final long t0 = System.nanoTime();
		for (int v = 0; v < vlaken; v++) {
			final int cislo = v;
			final Thread th = new Thread(() -> {
				try {
					final Ctec c = "sqljet".equals(knihovna) ? ctecSqljet(f) : ctecJdbc(f, journal);
					final Random r = new Random(100 + cislo);
					lat[cislo] = new long[dotazu];
					for (int q = 0; q < dotazu; q++) {
						final long s = System.nanoTime();
						final byte[] b = c.cti(r.nextInt(rozsah));
						lat[cislo][q] = System.nanoTime() - s;
						if (b == null) {
							throw new IllegalStateException("chybí dlaždice");
						}
					}
					c.zavri();
				} catch (final Exception e) {
					throw new RuntimeException(e);
				}
			});
			vlakna.add(th);
			th.start();
		}
		for (final Thread th : vlakna) {
			th.join();
		}
		final long celkem = System.nanoTime() - t0;
		final long[] vse = Arrays.stream(lat).flatMapToLong(Arrays::stream).sorted().toArray();
		System.out.printf("cteni %s %s vlaken=%d: dotazu=%d prumer=%.3f ms p50=%.3f p99=%.3f max=%.2f, celkem %d ms%n", knihovna, journal == null ? "" : journal, vlaken, vse.length,
				Arrays.stream(vse).average().orElse(0) / 1e6, pct(vse, 0.5) / 1e6, pct(vse, 0.99) / 1e6, vse[vse.length - 1] / 1e6, celkem / 1_000_000);
	}

	/** Čtecí vlákna a zápis dávek (50 dlaždic / 250 ms), stejné zámky jako aplikace: čtení pod čtecím, zápis pod zapisovacím zámkem. */
	static void smiseny(final File f, final String knihovna, final String journal, final int ctenaru, final int rozsah, final int davka, final int sekund, final boolean vlastniZamek)
			throws Exception {
		final ReentrantReadWriteLock zamek = new ReentrantReadWriteLock(true);
		final AtomicBoolean konec = new AtomicBoolean();
		final List<Thread> vlakna = new ArrayList<>();
		final long[][] lat = new long[ctenaru][];
		final int[] pocty = new int[ctenaru];
		for (int v = 0; v < ctenaru; v++) {
			final int cislo = v;
			final Thread th = new Thread(() -> {
				try {
					final Ctec c = "sqljet".equals(knihovna) ? ctecSqljet(f) : ctecJdbc(f, journal);
					final Random r = new Random(500 + cislo);
					lat[cislo] = new long[2_000_000];
					int n = 0;
					while (!konec.get()) {
						final long s = System.nanoTime();
						if (vlastniZamek) {
							zamek.readLock().lock();
						}
						try {
							c.cti(r.nextInt(rozsah));
						} finally {
							if (vlastniZamek) {
								zamek.readLock().unlock();
							}
						}
						lat[cislo][n++] = System.nanoTime() - s;
						Thread.sleep(0, 200_000); // ~ tempo, ne smyčka na plný výkon
					}
					pocty[cislo] = n;
					c.zavri();
				} catch (final Exception e) {
					throw new RuntimeException(e);
				}
			});
			vlakna.add(th);
			th.start();
		}
		final List<Long> zapisy = new ArrayList<>();
		final Zapisovac z = "sqljet".equals(knihovna) ? zapisovacSqljet(f) : zapisovacJdbc(f, journal);
		final Random r = new Random(9);
		final long konecNs = System.nanoTime() + sekund * 1_000_000_000L;
		int i = rozsah; // nové dlaždice za stávajícím rozsahem
		while (System.nanoTime() < konecNs) {
			final long s = System.nanoTime();
			if (vlastniZamek) {
				zamek.writeLock().lock();
			}
			try {
				z.zapis(i, davka, r);
			} finally {
				if (vlastniZamek) {
					zamek.writeLock().unlock();
				}
			}
			zapisy.add(System.nanoTime() - s);
			i += davka;
			Thread.sleep(250);
		}
		konec.set(true);
		for (final Thread th : vlakna) {
			th.join();
		}
		z.zavri();
		int celkem = 0;
		for (final int n : pocty) {
			celkem += n;
		}
		final long[] vse = new long[celkem];
		int k = 0;
		for (int v = 0; v < ctenaru; v++) {
			System.arraycopy(lat[v], 0, vse, k, pocty[v]);
			k += pocty[v];
		}
		Arrays.sort(vse);
		final long[] w = zapisy.stream().mapToLong(Long::longValue).sorted().toArray();
		System.out.printf("smiseny %s %s zamek=%s ctenaru=%d davka=%d: cteni n=%d p50=%.3f p99=%.3f p99.9=%.3f max=%.1f ms | zapis davek=%d prumer=%.1f max=%.1f ms%n", knihovna,
				journal == null ? "" : journal, vlastniZamek, ctenaru, davka, vse.length, pct(vse, 0.5) / 1e6, pct(vse, 0.99) / 1e6, pct(vse, 0.999) / 1e6, vse[vse.length - 1] / 1e6, w.length,
				Arrays.stream(w).average().orElse(0) / 1e6, w[w.length - 1] / 1e6);
	}

	/** Čas prvního otevření a prvního čtení ve čerstvém JVM. */
	static void otevreni(final File f, final String knihovna) throws Exception {
		final long t0 = System.nanoTime();
		final Ctec c = "sqljet".equals(knihovna) ? ctecSqljet(f) : ctecJdbc(f, null);
		final long t1 = System.nanoTime();
		c.cti(5);
		final long t2 = System.nanoTime();
		c.cti(77);
		final long t3 = System.nanoTime();
		System.out.printf("otevreni %s: open %.0f ms, prvni cteni %.1f ms, druhe %.2f ms%n", knihovna, (t1 - t0) / 1e6, (t2 - t1) / 1e6, (t3 - t2) / 1e6);
		c.zavri();
	}

	/** Zápis dávek bez čtenářů, srovnání rychlosti. */
	static void zapis(final File f, final String knihovna, final String journal, final int davka, final int davek) throws Exception {
		final Zapisovac z = "sqljet".equals(knihovna) ? zapisovacSqljet(f) : zapisovacJdbc(f, journal);
		final Random r = new Random(11);
		final long[] d = new long[davek];
		int i = 1_000_000;
		for (int k = 0; k < davek; k++) {
			final long s = System.nanoTime();
			z.zapis(i, davka, r);
			d[k] = System.nanoTime() - s;
			i += davka;
		}
		z.zavri();
		Arrays.sort(d);
		System.out.printf("zapis %s %s davka=%d: prumer %.1f ms p50 %.1f max %.1f%n", knihovna, journal == null ? "" : journal, davka, Arrays.stream(d).average().orElse(0) / 1e6, pct(d, .5) / 1e6,
				d[d.length - 1] / 1e6);
	}


	private MerSqlite() {}

	/** Okno jedné obrazovky: 8 × 5 sousedních dlaždic. */
	static void okno(final File f, final String knihovna, final int obrazovek, final int rozsah) throws Exception {
		final Ctec c = "sqljet".equals(knihovna) ? ctecSqljet(f) : ctecJdbc(f, null);
		final Random r = new Random(77);
		final long[] ms = new long[obrazovek];
		for (int o = 0; o < obrazovek; o++) {
			final int x = r.nextInt(500 - 8);
			final int y = r.nextInt(Math.max(1, rozsah / 2 / 512 - 5));
			final long s = System.nanoTime();
			for (int dy = 0; dy < 5; dy++) {
				for (int dx = 0; dx < 8; dx++) {
					c.cti(((y + dy) * 512 + x + dx) * 2);
				}
			}
			ms[o] = System.nanoTime() - s;
		}
		c.zavri();
		Arrays.sort(ms);
		System.out.printf("okno %s: obrazovek=%d (40 dlaždic) prumer=%.1f p50=%.1f p95=%.1f max=%.1f ms%n", knihovna, obrazovek, Arrays.stream(ms).average().orElse(0) / 1e6, pct(ms, .5) / 1e6,
				pct(ms, .95) / 1e6, ms[ms.length - 1] / 1e6);
	}

	/** Počet dlaždic v souboru podle tabulky; pro rozsah náhodných čtení. */
	static int pocetDlazdic(final File f) throws Exception {
		try (Connection c = jdbc(f, null, false); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM tiles")) {
			rs.next();
			return rs.getInt(1);
		}
	}

	/** Značka v hlavičce souboru ({@code PRAGMA application_id}), kterou zapíše jen {@code gen}; soubor bez ní se nepoužije. */
	static final int ZNACKA = 0x4D45_5253;

	/**
	 * Soubor smí být jen {@code mer-sqlite-*.sqlite}, nesmí být (ani žádná jeho nadřazená složka) odkaz ani spojení složek a pro čtení a zápis musí mít značku ze
	 * {@code gen}. Kopie nebo odkaz na skutečnou cache proto neprojdou.
	 */
	static void zkontrolujSoubor(final File f, final boolean musiMitZnacku) throws Exception {
		if (!f.getName().startsWith("mer-sqlite-") || !f.getName().endsWith(".sqlite")) {
			throw new IllegalArgumentException("Pracuje jen se souborem mer-sqlite-*.sqlite, ne s " + f + " (skutečná cache se nepoužívá)");
		}
		java.nio.file.Path cesta = f.toPath().toAbsolutePath().normalize();
		while (cesta != null) {
			if (java.nio.file.Files.exists(cesta, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
				final java.nio.file.attribute.BasicFileAttributes at = java.nio.file.Files.readAttributes(cesta, java.nio.file.attribute.BasicFileAttributes.class,
						java.nio.file.LinkOption.NOFOLLOW_LINKS);
				if (at.isSymbolicLink() || at.isOther()) {
					throw new IllegalArgumentException("Odmítnuto: " + cesta + " je odkaz nebo spojení složek, nástroj pracuje jen s vlastním souborem");
				}
			}
			cesta = cesta.getParent();
		}
		if (musiMitZnacku) {
			if (!f.isFile()) {
				throw new IllegalArgumentException("Soubor " + f + " neexistuje, nejdřív příkaz gen");
			}
			try (Connection c = jdbc(f, null, false); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA application_id")) {
				if (!rs.next() || rs.getInt(1) != ZNACKA) {
					throw new IllegalArgumentException("Odmítnuto: " + f + " nemá značku souboru založeného příkazem gen (skutečná cache se nepoužívá)");
				}
			}
		}
	}

	/** Samotest ochrany: odkaz, kopie bez značky, špatné jméno; vypíše výsledek a skončí chybou, když něco projde. */
	static void samotest(final File slozka) throws Exception {
		java.nio.file.Files.createDirectories(slozka.toPath());
		final File cizi = new File(slozka, "tiles-cizi.sqlite");
		for (final String n : new String[] { "tiles-cizi.sqlite", "mer-sqlite-kopie.sqlite", "mer-sqlite-vlastni.sqlite", "mer-sqlite-odkaz.sqlite", "tiles-vlastni.sqlite", "odkaz-na-slozku" }) {
			java.nio.file.Files.deleteIfExists(new File(slozka, n).toPath());
		}
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + cizi.getPath()); Statement st = c.createStatement()) {
			st.execute("CREATE TABLE tiles (x int, y int, z int, s varchar(10), image blob, PRIMARY KEY(x, y, z, s))");
		}
		final File kopie = new File(slozka, "mer-sqlite-kopie.sqlite");
		java.nio.file.Files.copy(cizi.toPath(), kopie.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		final File vlastni = new File(slozka, "mer-sqlite-vlastni.sqlite");
		java.nio.file.Files.deleteIfExists(vlastni.toPath());
		genSqljet(vlastni, 50);
		odmitne("špatné jméno", cizi, true);
		final File prejmenovany = new File(slozka, "tiles-vlastni.sqlite");
		java.nio.file.Files.copy(vlastni.toPath(), prejmenovany.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		odmitne("soubor se značkou pod cizím jménem", prejmenovany, true);
		odmitne("kopie bez značky", kopie, true);
		zkontrolujSoubor(vlastni, true);
		System.out.println("samotest: vlastní soubor se značkou projde");
		try {
			genSqljet(vlastni, 10);
			throw new IllegalStateException("gen přepsal existující soubor");
		} catch (final IllegalStateException e) {
			if (!e.getMessage().contains("už existuje")) {
				throw e;
			}
			System.out.println("samotest: gen existující soubor nepřepíše: ok");
		}
		final File odkaz = new File(slozka, "mer-sqlite-odkaz.sqlite");
		java.nio.file.Files.deleteIfExists(odkaz.toPath());
		try {
			java.nio.file.Files.createSymbolicLink(odkaz.toPath(), cizi.toPath().toAbsolutePath());
			odmitne("odkaz na cizí soubor", odkaz, true);
			odmitne("odkaz na vlastní soubor", odkaz, false);
			final File slozkaOdkaz = new File(slozka, "odkaz-na-slozku");
			java.nio.file.Files.deleteIfExists(slozkaOdkaz.toPath());
			java.nio.file.Files.createSymbolicLink(slozkaOdkaz.toPath(), slozka.toPath().toAbsolutePath());
			odmitne("soubor v odkazu na složku", new File(slozkaOdkaz, vlastni.getName()), true);
			java.nio.file.Files.deleteIfExists(slozkaOdkaz.toPath());
			java.nio.file.Files.deleteIfExists(odkaz.toPath());
		} catch (final UnsupportedOperationException | java.io.IOException | SecurityException e) {
			System.out.println("samotest: odkazy nejde vytvořit (" + e + "), kontrola odkazů přeskočena");
		}
		System.out.println("samotest: ochrana funguje");
	}

	private static void odmitne(final String co, final File f, final boolean znacka) throws Exception {
		try {
			zkontrolujSoubor(f, znacka);
		} catch (final IllegalArgumentException e) {
			System.out.println("samotest: " + co + ": odmítnuto, ok");
			return;
		}
		throw new IllegalStateException("Ochrana selhala: " + co + " prošlo");
	}

	public static void main(final String[] a) throws Exception {
		final String cmd = a[0];
		final File f = new File(a[1]);
		if ("samotest".equals(cmd)) {
			samotest(f);
			System.exit(0);
		}
		zkontrolujSoubor(f, !"gen".equals(cmd));
		switch (cmd) {
		case "overit":
			System.out.println("měřicí soubor: ok");
			break;
		case "gen": {
			final int mb = Integer.parseInt(a[2]);
			final int dlazdic = (int) ((long) mb * 1_048_576 / 27_500);
			final long t0 = System.nanoTime();
			genSqljet(f, dlazdic);
			System.out.printf("gen: %d dlaždic, %d MB, %d s%n", dlazdic, f.length() >> 20, (System.nanoTime() - t0) / 1_000_000_000L);
			break;
		}
		case "otevreni":
			otevreni(f, a[2]);
			break;
		case "cteni":
			cteni(f, a[2], null, Integer.parseInt(a[3]), pocetDlazdic(f), Integer.parseInt(a[4]));
			break;
		case "okno":
			okno(f, a[2], Integer.parseInt(a[3]), pocetDlazdic(f));
			break;
		case "smiseny":
			smiseny(f, a[2], null, Integer.parseInt(a[3]), pocetDlazdic(f), Integer.parseInt(a[4]), Integer.parseInt(a[5]), true);
			break;
		case "zapis":
			zapis(f, a[2], null, Integer.parseInt(a[3]), Integer.parseInt(a[4]));
			break;
		default:
			throw new IllegalArgumentException(cmd);
		}
		System.exit(0);
	}
}
