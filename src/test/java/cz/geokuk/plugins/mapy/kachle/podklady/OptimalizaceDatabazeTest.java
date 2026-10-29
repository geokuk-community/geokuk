package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.*;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleManager.ItemToSave;
import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.*;
import cz.geokuk.util.file.Filex;

public class OptimalizaceDatabazeTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File slozka;
	private File soubor;
	private KachleDBManager manager;
	private OptimalizaceDatabaze optimalizace;

	private static final Prubeh BEZ_ZRUSENI = new Prubeh() {
		@Override
		public void zpracovano(final long hotovo, final long celkem) {}

		@Override
		public boolean zruseno() {
			return false;
		}
	};

	@Before
	public void setUp() throws Exception {
		slozka = tmp.newFolder("cache");
		soubor = new File(slozka, "tiles.sqlite");
		final KachleCacheFolderHolder holder = new KachleCacheFolderHolder();
		holder.setKachleCacheDir(new Filex(slozka, false, true));
		manager = new KachleDBManager(holder);
		optimalizace = manager.optimalizace();
		optimalizace.setPauzaMezipokusy(0);
	}

	/** Stará cache se stránkou 1024; každá druhá dlaždice smazaná, takže má volné stránky. */
	private void vyrobStarouCache(final int pocet, final int velikost) throws Exception {
		final SqlJetDb db = SqlJetDb.open(soubor, true);
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		db.createTable("CREATE TABLE tiles (x int, y int, z int, s varchar(10), image blob, PRIMARY KEY(x, y, z, s))");
		final ISqlJetTable t = db.getTable("tiles");
		final Random r = new Random(1);
		for (int i = 0; i < pocet; i++) {
			final byte[] d = new byte[velikost];
			r.nextBytes(d);
			t.insertOr(SqlJetConflictAction.REPLACE, i, i % 7, 14, "o1", d);
		}
		db.commit();
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		final ISqlJetCursor c = t.open();
		while (!c.eof()) {
			c.delete();
			if (!c.eof()) {
				c.next();
			}
		}
		c.close();
		db.commit();
		db.close();
	}

	private void ctiDlazdici(final int x) throws Exception {
		final SqlJetDb db = SqlJetDb.open(soubor, false);
		try {
			db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
			final ISqlJetTable t = db.getTable("tiles");
			final ISqlJetCursor c = t.lookup(t.getPrimaryKeyIndexName(), x, x % 7, 14, "o1");
			Assert.assertFalse("dlaždice " + x + " chybí", c.eof());
			final byte[] d = c.getBlobAsArray("image");
			final byte[] ocekavano = new byte[d.length];
			final Random r = new Random(1);
			for (int i = 0; i <= x; i++) {
				r.nextBytes(ocekavano);
			}
			Assert.assertArrayEquals(ocekavano, d);
			c.close();
			db.commit();
		} finally {
			db.close();
		}
	}

	private static long pocetRadku(final File f) throws Exception {
		final SqlJetDb db = SqlJetDb.open(f, false);
		try {
			db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
			final long n = db.getTable("tiles").open().getRowCount();
			db.commit();
			return n;
		} finally {
			db.close();
		}
	}

	private void cachePoUdalostiPouzitelna() throws Exception {
		final Ka ka = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13), EKaType.TURIST_M);
		Assert.assertTrue("zápis po optimalizaci", manager.save(java.util.Collections.singleton(new ItemToSave(ka, new byte[] { 1, 2, 3 }))));
	}

	@Test
	public void zkopirujeZiveRadkyDoNoveStranky() throws Exception {
		vyrobStarouCache(300, 15000);
		final StavSouboruCache pred = optimalizace.stav();
		Assert.assertEquals(1024, pred.velikostStranky);
		Assert.assertTrue(pred.podilVolnych() > 0.3);
		Assert.assertEquals(Vysledek.HOTOVO, optimalizace.spust(BEZ_ZRUSENI));
		final StavSouboruCache po = optimalizace.stav();
		Assert.assertEquals(CacheDb.STRANKA_NOVE, po.velikostStranky);
		Assert.assertEquals(0, po.volnych);
		Assert.assertTrue(po.velikost < pred.velikost / 2 + (1 << 20));
		Assert.assertEquals(150, pocetRadku(soubor));
		for (int x = 1; x < 300; x += 2) {
			ctiDlazdici(x);
		}
		Assert.assertFalse(new File(soubor.getPath() + ".new").exists());
		cachePoUdalostiPouzitelna();
	}

	@Test
	public void behemOptimalizaceJeCacheZavrena() throws Exception {
		vyrobStarouCache(300, 15000);
		final Ka ka = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13), EKaType.TURIST_M);
		final AtomicInteger volani = new AtomicInteger();
		optimalizace.spust(new Prubeh() {
			@Override
			public void zpracovano(final long hotovo, final long celkem) {
				volani.incrementAndGet();
				Assert.assertNull(manager.load(ka));
				Assert.assertFalse(manager.save(java.util.Collections.singleton(new ItemToSave(ka, new byte[] { 1 }))));
			}

			@Override
			public boolean zruseno() {
				return false;
			}
		});
		Assert.assertTrue(volani.get() > 0);
		cachePoUdalostiPouzitelna();
	}

	@Test
	public void zruseniNechaPuvodniSouborBajtDoBajtu() throws Exception {
		vyrobStarouCache(5000, 100);
		final byte[] pred = Files.readAllBytes(soubor.toPath());
		final Vysledek v = optimalizace.spust(new Prubeh() {
			@Override
			public void zpracovano(final long hotovo, final long celkem) {}

			@Override
			public boolean zruseno() {
				return true;
			}
		});
		Assert.assertEquals(Vysledek.ZRUSENO, v);
		Assert.assertArrayEquals(pred, Files.readAllBytes(soubor.toPath()));
		Assert.assertFalse(new File(soubor.getPath() + ".new").exists());
		Assert.assertFalse(new File(soubor.getPath() + ".new-journal").exists());
		cachePoUdalostiPouzitelna();
	}

	@Test
	public void selhaniVymenyNechaPuvodniAUklidiNovy() throws Exception {
		vyrobStarouCache(300, 15000);
		final byte[] pred = Files.readAllBytes(soubor.toPath());
		final AtomicInteger pokusu = new AtomicInteger();
		optimalizace.setPresun((odkud, kam) -> {
			pokusu.incrementAndGet();
			throw new IOException("soubor je zamčený");
		});
		try {
			optimalizace.spust(BEZ_ZRUSENI);
			Assert.fail("výměna měla selhat");
		} catch (final OptimalizaceChyba e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("původní cache zůstala beze změny"));
		}
		Assert.assertEquals(3, pokusu.get());
		Assert.assertArrayEquals(pred, Files.readAllBytes(soubor.toPath()));
		Assert.assertFalse(new File(soubor.getPath() + ".new").exists());
		cachePoUdalostiPouzitelna();
	}

	@Test
	public void vymenaProjdeAzPoDruhemPokusu() throws Exception {
		vyrobStarouCache(300, 15000);
		final AtomicInteger pokusu = new AtomicInteger();
		optimalizace.setPresun((odkud, kam) -> {
			if (pokusu.incrementAndGet() < 2) {
				throw new IOException("antivirus");
			}
			Files.move(odkud, kam, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		});
		Assert.assertEquals(Vysledek.HOTOVO, optimalizace.spust(BEZ_ZRUSENI));
		Assert.assertEquals(2, pokusu.get());
		Assert.assertEquals(150, pocetRadku(soubor));
	}

	@Test
	public void nedostatekMistaOdmitneBezZmeny() throws Exception {
		vyrobStarouCache(300, 15000);
		final byte[] pred = Files.readAllBytes(soubor.toPath());
		optimalizace.setVolneMisto(f -> 1L << 20);
		try {
			optimalizace.spust(BEZ_ZRUSENI);
			Assert.fail("měla být odmítnuta");
		} catch (final OptimalizaceChyba e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("chybí místo"));
		}
		Assert.assertArrayEquals(pred, Files.readAllBytes(soubor.toPath()));
		Assert.assertFalse(new File(soubor.getPath() + ".new").exists());
		cachePoUdalostiPouzitelna();
	}

	@Test
	public void zbylyNovySouborPoPaduSeUklidi() throws Exception {
		vyrobStarouCache(300, 15000);
		Files.write(new File(soubor.getPath() + ".new").toPath(), new byte[] { 1, 2, 3 });
		Assert.assertEquals(Vysledek.HOTOVO, optimalizace.spust(BEZ_ZRUSENI));
		Assert.assertEquals(150, pocetRadku(soubor));
	}

	@Test(timeout = 60000)
	public void zamekJinehoProgramuNechaPuvodni() throws Exception {
		vyrobStarouCache(300, 15000);
		final byte[] pred = Files.readAllBytes(soubor.toPath());
		final Process jiny = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(), "-cp", System.getProperty("java.class.path"),
				ZamekJinehoProgramu.class.getName(), soubor.getPath()).redirectErrorStream(true).start();
		try (java.io.BufferedReader vystup = new java.io.BufferedReader(new java.io.InputStreamReader(jiny.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
			for (String radek = vystup.readLine(); !"zamceno".equals(radek); radek = vystup.readLine()) {
				Assert.assertNotNull("druhý program skončil bez zámku", radek);
			}
			try {
				optimalizace.spust(BEZ_ZRUSENI);
				Assert.fail("zamčená cache se nemá optimalizovat");
			} catch (final OptimalizaceChyba e) {
				Assert.assertTrue(e.getMessage(), e.getMessage().contains("jiný program"));
			}
		} finally {
			jiny.getOutputStream().write('\n');
			jiny.getOutputStream().flush();
			jiny.waitFor(10, TimeUnit.SECONDS);
			jiny.destroyForcibly();
		}
		Assert.assertArrayEquals(pred, Files.readAllBytes(soubor.toPath()));
		Assert.assertFalse(new File(soubor.getPath() + ".new").exists());
		cachePoUdalostiPouzitelna();
	}

	@Test
	public void bezCacheJeChyba() {
		try {
			optimalizace.spust(BEZ_ZRUSENI);
			Assert.fail();
		} catch (final OptimalizaceChyba e) {
			Assert.assertTrue(e.getMessage().contains("není co optimalizovat"));
		}
	}

	@Test
	public void doporuceniPodlePrahu() {
		final long mb = 1L << 20;
		// stránek, volných: velikost = stránek × 1024
		Assert.assertEquals(Doporuceni.DOPORUCIT, OptimalizaceDatabaze.posud(new StavSouboruCache(1000 * mb, 1024, 1000 * 1024, 400 * 1024), 10_000 * mb));
		Assert.assertEquals("pod 30 % volných", Doporuceni.NENI, OptimalizaceDatabaze.posud(new StavSouboruCache(1000 * mb, 1024, 1000 * 1024, 290 * 1024), 10_000 * mb));
		Assert.assertEquals("malý soubor", Doporuceni.NENI, OptimalizaceDatabaze.posud(new StavSouboruCache(150 * mb, 1024, 150 * 1024, 100 * 1024), 10_000 * mb));
		Assert.assertEquals("málo místa", Doporuceni.MALO_MISTA, OptimalizaceDatabaze.posud(new StavSouboruCache(1000 * mb, 1024, 1000 * 1024, 400 * 1024), 300 * mb));
		Assert.assertEquals("není cache", Doporuceni.NENI, OptimalizaceDatabaze.posud(null, 10_000 * mb));
		Assert.assertEquals("neznámé místo se bere jako dost", Doporuceni.DOPORUCIT, OptimalizaceDatabaze.posud(new StavSouboruCache(1000 * mb, 1024, 1000 * 1024, 400 * 1024), -1));
	}

	@Test
	public void odhadMinutAMista() {
		final long gb = 1L << 30;
		final StavSouboruCache s = new StavSouboruCache(2 * gb, 1024, 2 * gb / 1024, gb / 1024);
		Assert.assertEquals(1, s.zivaData() / gb);
		Assert.assertEquals(1, OptimalizaceDatabaze.odhadMinut(s));
		final StavSouboruCache velka = new StavSouboruCache(10 * gb, 1024, 10 * gb / 1024, 0);
		Assert.assertEquals(7, OptimalizaceDatabaze.odhadMinut(velka));
		Assert.assertTrue(OptimalizaceDatabaze.potrebneMisto(velka) > 10 * gb);
	}

	@Test
	public void spojeniOtevreneTesnePredPozastavenimSeNezaregistruje() throws Exception {
		vyrobStarouCache(50, 1000);
		final Ka ka = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13), EKaType.TURIST_M);
		manager.predRegistraciSpojeni = () -> {
			manager.predRegistraciSpojeni = () -> {};
			Assert.assertTrue(manager.pozastav());
		};
		Assert.assertNull("cache je pozastavená", manager.load(ka));
		Assert.assertTrue("staré spojení se nesmí držet", manager.connections.isEmpty());
		manager.obnov();
		cachePoUdalostiPouzitelna();
		Assert.assertEquals(1, manager.connections.size());
	}

	private static final String PONECHANA = "o0000000a.00000001";
	private static final String STARA = "o0000000b.00000001";
	private static final String PREKRYV_PONECHANY = "t0000000c.00000001";
	private static final String PREKRYV_STARY = "t0000000d.00000001";

	/** Dlaždice o 1000 bajtech pro každý typ, {@code pocet} od x = 0; vrací bajty obrázků. */
	private void vlozDlazdice(final int pocet, final String... typy) throws Exception {
		final SqlJetDb db = SqlJetDb.open(soubor, true);
		try {
			db.beginTransaction(SqlJetTransactionMode.WRITE);
			if (!db.getSchema().getTableNames().contains("tiles")) {
				db.createTable("CREATE TABLE tiles (x int, y int, z int, s varchar(10), image blob, PRIMARY KEY(x, y, z, s))");
			}
			final ISqlJetTable t = db.getTable("tiles");
			for (final String typ : typy) {
				for (int i = 0; i < pocet; i++) {
					t.insertOr(SqlJetConflictAction.REPLACE, i, 0, 14, typ, new byte[1000]);
				}
			}
			db.commit();
		} finally {
			db.close();
		}
	}

	private void zapisKlice(final String podslozka, final String... klice) throws Exception {
		final File s = new File(slozka, podslozka);
		Assert.assertTrue(s.isDirectory() || s.mkdirs());
		int i = 0;
		for (final String klic : klice) {
			final File symboly = new File(s, "symboly-" + klic + ".bin");
			Files.write(symboly.toPath(), new byte[] { 1 });
			UklidOfflineCache.zaznamenej(s, klic, symboly, "00000000", String.format("%08x", ++i));
		}
	}

	private long pocetTypu(final String typ) throws Exception {
		final SqlJetDb db = SqlJetDb.open(soubor, false);
		try {
			db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
			final ISqlJetTable t = db.getTable("tiles");
			long n = 0;
			for (final ISqlJetCursor c = t.open(); !c.eof(); c.next()) {
				if (typ.equals(c.getString("s"))) {
					n++;
				}
			}
			db.commit();
			return n;
		} finally {
			db.close();
		}
	}

	private void naplnSmesnouCache() throws Exception {
		vlozDlazdice(100, PONECHANA, STARA, PREKRYV_PONECHANY, PREKRYV_STARY, "TURIST_M");
		zapisKlice(UklidOfflineCache.SLOZKA_PODKLADU, PONECHANA.substring(0, 9));
		zapisKlice(UklidOfflineCache.SLOZKA_PREKRYVU, PREKRYV_PONECHANY.substring(0, 9));
	}

	@Test
	public void optimalizaceVynechaZastaraleOfflineDlazdiceAOnlineNechaBezeZmeny() throws Exception {
		naplnSmesnouCache();
		Assert.assertEquals(Vysledek.HOTOVO, optimalizace.spust(BEZ_ZRUSENI));
		Assert.assertEquals(100, pocetTypu(PONECHANA));
		Assert.assertEquals(0, pocetTypu(STARA));
		Assert.assertEquals(100, pocetTypu(PREKRYV_PONECHANY));
		Assert.assertEquals(0, pocetTypu(PREKRYV_STARY));
		Assert.assertEquals("online dlaždice zůstávají", 100, pocetTypu("TURIST_M"));
		Assert.assertEquals(300, pocetRadku(soubor));
		cachePoUdalostiPouzitelna();
	}

	@Test
	public void souboryKlicuOptimalizaceNemeni() throws Exception {
		naplnSmesnouCache();
		final byte[] podklad = Files.readAllBytes(new File(slozka, "offline-temata/klice.txt").toPath());
		final byte[] prekryv = Files.readAllBytes(new File(slozka, "offline-trasy/klice.txt").toPath());
		Assert.assertEquals(Vysledek.HOTOVO, optimalizace.spust(BEZ_ZRUSENI));
		Assert.assertArrayEquals(podklad, Files.readAllBytes(new File(slozka, "offline-temata/klice.txt").toPath()));
		Assert.assertArrayEquals(prekryv, Files.readAllBytes(new File(slozka, "offline-trasy/klice.txt").toPath()));
	}

	@Test
	public void bezSouboruKlicuSeNicNevynecha() throws Exception {
		vlozDlazdice(50, PONECHANA, STARA, PREKRYV_STARY);
		Assert.assertEquals(Vysledek.HOTOVO, optimalizace.spust(BEZ_ZRUSENI));
		Assert.assertEquals(150, pocetRadku(soubor));
		Assert.assertSame(StaraData.ZADNA, optimalizace.zapamatovaneStare());
	}

	@Test
	public void prekryvBezSvychKlicuNeniZastarely() throws Exception {
		vlozDlazdice(50, PONECHANA, STARA, PREKRYV_STARY);
		zapisKlice(UklidOfflineCache.SLOZKA_PODKLADU, PONECHANA.substring(0, 9));
		Assert.assertEquals(Vysledek.HOTOVO, optimalizace.spust(BEZ_ZRUSENI));
		Assert.assertEquals(0, pocetTypu(STARA));
		Assert.assertEquals("o překryvu soubor klíčů nic neví", 50, pocetTypu(PREKRYV_STARY));
	}

	@Test
	public void zruseniSZastaralymiDlazdicemiNechaSouborBeze() throws Exception {
		naplnSmesnouCache();
		final byte[] pred = Files.readAllBytes(soubor.toPath());
		final Vysledek v = optimalizace.spust(new Prubeh() {
			@Override
			public void zpracovano(final long hotovo, final long celkem) {}

			@Override
			public boolean zruseno() {
				return true;
			}
		});
		Assert.assertEquals(Vysledek.ZRUSENO, v);
		Assert.assertArrayEquals(pred, Files.readAllBytes(soubor.toPath()));
	}

	@Test
	public void prubehHlasiObjemZkopirovanychAVynechanych() throws Exception {
		naplnSmesnouCache();
		final long[] posledni = new long[4];
		optimalizace.spust(new Prubeh() {
			@Override
			public void objem(final long zkopirovano, final long vynechano) {
				posledni[2] = zkopirovano;
				posledni[3] = vynechano;
			}

			@Override
			public void zpracovano(final long hotovo, final long celkem) {
				posledni[0] = hotovo;
				posledni[1] = celkem;
			}

			@Override
			public boolean zruseno() {
				return false;
			}
		});
		Assert.assertEquals(500, posledni[0]);
		Assert.assertEquals(500, posledni[1]);
		Assert.assertEquals(300 * 1000, posledni[2]);
		Assert.assertEquals(200 * 1000, posledni[3]);
	}

	@Test
	public void odhadZastaralychDlazdicSeSpocitaJednouAZapamatuje() throws Exception {
		naplnSmesnouCache();
		Assert.assertNull("zatím nespočteno", optimalizace.zapamatovaneStare());
		final StaraData stare = optimalizace.spocitejStare();
		Assert.assertEquals(200, stare.dlazdic());
		Assert.assertEquals(200 * 1000, stare.bajtu());
		final StaraData znovu = optimalizace.zapamatovaneStare();
		Assert.assertNotNull(znovu);
		Assert.assertEquals(200, znovu.dlazdic());
		Assert.assertEquals(200 * 1000, znovu.bajtu());
		// Soubor cache se při pamatování neotvírá.
		Assert.assertTrue(new File(slozka, "stare-dlazdice.txt").isFile());
	}

	@Test
	public void odhadPoZmeneKlicuUzNeplati() throws Exception {
		naplnSmesnouCache();
		optimalizace.spocitejStare();
		Assert.assertNotNull(optimalizace.zapamatovaneStare());
		zapisKlice(UklidOfflineCache.SLOZKA_PODKLADU, STARA.substring(0, 9));
		Assert.assertNull("jiná sada klíčů", optimalizace.zapamatovaneStare());
		Assert.assertEquals("teď je zastaralá první", 100 + 100, optimalizace.spocitejStare().dlazdic());
	}

	@Test
	public void odhadSObjememVetsimNezSouborPatriKJineCache() throws Exception {
		naplnSmesnouCache();
		new StaraData(1, 10L << 30, StaraData.otiskPlanu(UklidOfflineCache.ponechaneVCache(slozka))).uloz(new File(slozka, "stare-dlazdice.txt"));
		Assert.assertNull(optimalizace.zapamatovaneStare());
	}

	@Test
	public void poOptimalizaciJeOdhadNula() throws Exception {
		naplnSmesnouCache();
		optimalizace.spocitejStare();
		optimalizace.spust(BEZ_ZRUSENI);
		final StaraData po = optimalizace.zapamatovaneStare();
		Assert.assertNotNull(po);
		Assert.assertEquals(0, po.bajtu());
		Assert.assertEquals(0, optimalizace.spocitejStare().dlazdic());
	}

	@Test
	public void doporuceniSZastaralymiDlazdicemi() {
		final long mb = 1L << 20;
		// Soubor 1000 MB, 10 % volných stránek.
		final StavSouboruCache stav = new StavSouboruCache(1000 * mb, 1024, 1000 * 1024, 100 * 1024);
		Assert.assertEquals(Doporuceni.NENI, OptimalizaceDatabaze.posud(stav, StaraData.ZADNA, 10_000 * mb));
		Assert.assertEquals("10 % volných + 250 MB zastaralých = 35 %", Doporuceni.DOPORUCIT, OptimalizaceDatabaze.posud(stav, new StaraData(1, 250 * mb, "x"), 10_000 * mb));
		Assert.assertEquals("10 % + 150 MB = 25 %", Doporuceni.NENI, OptimalizaceDatabaze.posud(stav, new StaraData(1, 150 * mb, "x"), 10_000 * mb));
		Assert.assertEquals("500 MB samy stačí", Doporuceni.DOPORUCIT, OptimalizaceDatabaze.posud(new StavSouboruCache(3000 * mb, 1024, 3000 * 1024, 0), new StaraData(1, 500 * mb, "x"), 10_000 * mb));
		Assert.assertEquals("pod 500 MB nestačí", Doporuceni.NENI, OptimalizaceDatabaze.posud(new StavSouboruCache(3000 * mb, 1024, 3000 * 1024, 0), new StaraData(1, 500 * mb - 1, "x"), 10_000 * mb));
		Assert.assertEquals("místo se počítá bez zastaralých", Doporuceni.DOPORUCIT, OptimalizaceDatabaze.posud(new StavSouboruCache(3000 * mb, 1024, 3000 * 1024, 0), new StaraData(1, 2000 * mb, "x"), 1500 * mb));
		Assert.assertEquals(Doporuceni.MALO_MISTA, OptimalizaceDatabaze.posud(new StavSouboruCache(3000 * mb, 1024, 3000 * 1024, 0), new StaraData(1, 600 * mb, "x"), 1500 * mb));
	}

	@Test
	public void stavBezStarychPocitaStareDlazdiceZaVolne() {
		final StavSouboruCache stav = new StavSouboruCache(1000 << 20, 1024, 1000 * 1024, 100 * 1024);
		final StavSouboruCache po = stav.bezStarych(200L << 20);
		Assert.assertEquals(0.30, po.podilVolnych(), 1e-9);
		Assert.assertEquals(700L << 20, po.zivaData());
		Assert.assertSame(stav, stav.bezStarych(0));
		Assert.assertEquals("nejvýš všechny stránky", 0, stav.bezStarych(5000L << 20).zivaData());
	}

	@Test
	public void pocitaniZastaralychDlazdicDrziSouborJenPoDobuDavky() throws Exception {
		naplnSmesnouCache();
		final AtomicInteger davek = new AtomicInteger();
		final StaraData stare = StaraData.spocti(soubor, UklidOfflineCache.ponechaneVCache(slozka), 70, () -> false, () -> {
			davek.incrementAndGet();
			// Mezi dávkami je soubor volný: zápis dlaždic bez čekání projde.
			try {
				final SqlJetDb db = SqlJetDb.open(soubor, true);
				try {
					db.setBusyHandler(null);
					db.beginTransaction(SqlJetTransactionMode.WRITE);
					db.commit();
				} finally {
					db.close();
				}
			} catch (final org.tmatesoft.sqljet.core.SqlJetException e) {
				throw new AssertionError("soubor je mezi dávkami zamčený", e);
			}
		});
		Assert.assertTrue("víc dávek", davek.get() >= 6);
		Assert.assertEquals(200, stare.dlazdic());
		Assert.assertEquals(200 * 1000, stare.bajtu());
	}

	@Test
	public void pocitaniLzeZrusitMeziDavkami() throws Exception {
		naplnSmesnouCache();
		final AtomicInteger davek = new AtomicInteger();
		Assert.assertNull(StaraData.spocti(soubor, UklidOfflineCache.ponechaneVCache(slozka), 70, () -> davek.get() >= 2, davek::incrementAndGet));
		Assert.assertEquals(2, davek.get());
	}
}
