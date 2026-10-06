package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.*;

import javax.imageio.ImageIO;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.framework.ChybyVDiagnostice;
import cz.geokuk.plugins.mapy.kachle.data.*;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleManager.ItemToSave;
import cz.geokuk.util.file.Filex;

/** Poškozená cache dlaždic se nesmí tiše vypnout. */
public class KachleDBManagerTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final Ka KACHLE = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13), EKaType.TURIST_M);

	private File slozka;
	private File soubor;
	private KachleDBManager manager;

	@Before
	public void setUp() throws Exception {
		slozka = tmp.newFolder("cache");
		soubor = new File(slozka, "tiles.sqlite");
		final KachleCacheFolderHolder holder = new KachleCacheFolderHolder();
		holder.setKachleCacheDir(new Filex(slozka, false, true));
		manager = new KachleDBManager(holder);
	}

	private static byte[] png() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}

	@Test
	public void ulozeniANacteni() throws Exception {
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(KACHLE, png()))));
		Assert.assertNotNull(manager.load(KACHLE));
	}

	@Test
	public void poskozenaCacheSeZaloziZnovu() throws Exception {
		Files.write(soubor.toPath(), "tohle není databáze".getBytes(StandardCharsets.UTF_8));
		Assert.assertNull("z poškozené cache se nic nenačte", manager.load(KACHLE));
		Assert.assertTrue("poškozený soubor se odloží", new File(soubor.getPath() + ".vadna").isFile());
		Assert.assertTrue("cache dál funguje", manager.save(Collections.singleton(new ItemToSave(KACHLE, png()))));
		Assert.assertNotNull(manager.load(KACHLE));
	}

	/** Souvislé čtení z více vláken nesmí zablokovat zápis nových dlaždic. */
	@Test(timeout = 120000)
	public void zapisProjdePriSouvislemCteni() throws Exception {
		final byte[] png = velkePng();
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(KACHLE, png))));
		final java.util.concurrent.atomic.AtomicBoolean konec = new java.util.concurrent.atomic.AtomicBoolean();
		final java.util.concurrent.atomic.AtomicInteger precteno = new java.util.concurrent.atomic.AtomicInteger();
		final ExecutorService ctenari = Executors.newFixedThreadPool(4);
		final java.util.List<java.util.concurrent.Future<?>> cteni = new java.util.ArrayList<>();
		try {
			for (int i = 0; i < 4; i++) {
				cteni.add(ctenari.submit(() -> {
					while (!konec.get()) {
						try {
							if (manager.load(KACHLE) != null) {
								precteno.incrementAndGet();
							}
						} catch (final RuntimeException e) {
							// Souběžné čtení SqlJet občas hlásí chybu, dlaždice se pak stáhne znovu.
						}
					}
				}));
			}
			Thread.sleep(200);
			for (int i = 0; i < 4; i++) {
				final Ka dalsi = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 14 + i), EKaType.TURIST_M);
				Assert.assertTrue("zápis " + i + " při čtení", manager.save(Collections.singleton(new ItemToSave(dalsi, png))));
			}
		} finally {
			konec.set(true);
			ctenari.shutdown();
			Assert.assertTrue(ctenari.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));
		}
		for (final java.util.concurrent.Future<?> f : cteni) {
			f.get();
		}
		Assert.assertTrue("čtenáři četli", precteno.get() > 0);
	}

	/** Zápis počká, až jiné spojení dokončí čtecí transakci, i když trvá déle než čekání SqlJet. */
	@Test(timeout = 120000)
	public void zapisPockaNaDlouheCteni() throws Exception {
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(KACHLE, png()))));
		final SqlJetDb jine = SqlJetDb.open(soubor, false);
		final ExecutorService ctenar = Executors.newSingleThreadExecutor();
		try {
			jine.beginTransaction(SqlJetTransactionMode.READ_ONLY);
			final ISqlJetTable tabulka = jine.getTable("tiles");
			final ISqlJetCursor kurzor = tabulka.open();
			Assert.assertFalse(kurzor.eof());
			kurzor.close();
			ctenar.submit(() -> {
				Thread.sleep(1500);
				jine.commit();
				return null;
			});
			final Ka dalsi = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 14), EKaType.TURIST_M);
			Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(dalsi, png()))));
		} finally {
			ctenar.shutdown();
			ctenar.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
			jine.close();
		}
	}

	/** Dlaždice s kresbou, aby dekódování trvalo jako u skutečné mapy. */
	private static byte[] velkePng() throws Exception {
		final BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		final java.util.Random r = new java.util.Random(1);
		for (int x = 0; x < 256; x++) {
			for (int y = 0; y < 256; y++) {
				img.setRGB(x, y, r.nextInt());
			}
		}
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return out.toByteArray();
	}

	@Test
	public void doNezapisovatelneSlozkyCacheNezaklada() throws Exception {
		manager = new KachleDBManager(manager.folderHolder) {
			@Override
			boolean lzeZapsat(final File s) {
				return false;
			}
		};
		Assert.assertFalse(manager.save(Collections.singleton(new ItemToSave(KACHLE, png()))));
		Assert.assertNull(manager.load(KACHLE));
		Assert.assertFalse("cache se nezaložila", soubor.exists());
	}

	@Test
	public void nezapisovatelnouSlozkuZkusiPoChvileZnovu() throws Exception {
		final int[] zkousek = new int[1];
		manager = new KachleDBManager(manager.folderHolder) {
			@Override
			boolean lzeZapsat(final File s) {
				zkousek[0]++;
				return false;
			}
		};
		Assert.assertNull(manager.load(KACHLE));
		Assert.assertNull(manager.load(KACHLE));
		Assert.assertEquals("výsledek zkoušky platí chvíli", 1, zkousek[0]);
	}

	@Test
	public void zkouskaZapisu() throws Exception {
		Assert.assertTrue(manager.lzeZapsat(slozka));
		Assert.assertEquals("po zkoušce nic nezůstane", 0, slozka.list().length);
		Assert.assertFalse("místo složky soubor", manager.lzeZapsat(new File(tmp.newFile("soubor"), "cache")));
	}

	/**
	 * Poškozená cache se odkládá pod výhradním zámkem. Test pustí otevírající vlákno jen tehdy, když samo drží zámek pro čtení, a pak
	 * kontroluje stav souborů: odložená cache bez nové by znamenala přejmenování během čtení.
	 */
	@Test(timeout = 60000)
	public void odlozeniPoskozenePodVyhradnimZamkem() throws Exception {
		Files.write(soubor.toPath(), "tohle není databáze".getBytes(StandardCharsets.UTF_8));
		final File vadna = new File(soubor.getPath() + ".vadna");
		final java.util.concurrent.locks.ReentrantReadWriteLock z = manager.zamek;
		final java.util.concurrent.atomic.AtomicReference<Throwable> chyba = new java.util.concurrent.atomic.AtomicReference<>();
		final Thread otevirac = new Thread(() -> {
			try {
				manager.load(KACHLE);
			} catch (final Throwable e) {
				chyba.set(e);
			}
		}, "otevirac");
		otevirac.setDaemon(true);
		z.readLock().lock();
		otevirac.start();
		try {
			for (;;) {
				final long konec = System.currentTimeMillis() + 10_000;
				while (!z.hasQueuedThread(otevirac) && otevirac.isAlive()) {
					Assert.assertTrue("otevírající vlákno nečeká na zámek", System.currentTimeMillis() < konec);
					Thread.sleep(1);
				}
				if (!otevirac.isAlive()) {
					break;
				}
				z.readLock().unlock();
				z.readLock().lock(); // spravedlivý zámek: až za otevírajícím vláknem
				Thread.sleep(200);
				Assert.assertFalse("odloženo mimo výhradní zámek", vadna.exists() && !soubor.exists());
			}
		} finally {
			z.readLock().unlock();
		}
		otevirac.join(10000);
		Assert.assertNull(chyba.get());
		Assert.assertTrue("poškozená cache se odložila", vadna.isFile());
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(KACHLE, png()))));
		Assert.assertNotNull(manager.load(KACHLE));
	}

	/** Zápis, který čeká na jiný program, mezi pokusy nesmí blokovat čtení. */
	@Test(timeout = 120000)
	public void cekaniZapisuNeblokujeCteni() throws Exception {
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(KACHLE, png()))));
		final SqlJetDb jine = SqlJetDb.open(soubor, false);
		final ExecutorService vlakna = Executors.newFixedThreadPool(2);
		try {
			jine.beginTransaction(SqlJetTransactionMode.READ_ONLY);
			final ISqlJetCursor kurzor = jine.getTable("tiles").open();
			Assert.assertFalse(kurzor.eof());
			kurzor.close();
			final Ka dalsi = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 14), EKaType.TURIST_M);
			final byte[] png = png();
			final java.util.concurrent.Future<Boolean> zapis = vlakna.submit(() -> manager.save(Collections.singleton(new ItemToSave(dalsi, png))));
			Thread.sleep(300);
			Assert.assertNotNull(vlakna.submit(() -> manager.load(KACHLE)).get());
			Assert.assertFalse("zápis ještě čeká", zapis.isDone());
			jine.commit();
			Assert.assertTrue(zapis.get());
		} finally {
			vlakna.shutdown();
			jine.close();
		}
	}

	/** První kontrola nezapisovatelné složky při čtení nesmí zablokovat souběžný zápis. */
	@Test(timeout = 60000)
	public void kontrolaSlozkyPriCteniNezablokujeZapis() throws Exception {
		final java.util.concurrent.CountDownLatch vKontrole = new java.util.concurrent.CountDownLatch(1);
		manager = new KachleDBManager(manager.folderHolder) {
			@Override
			boolean lzeZapsat(final File s) {
				if ("ctenar".equals(Thread.currentThread().getName())) {
					vKontrole.countDown();
					try {
						Thread.sleep(500);
					} catch (final InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					return false;
				}
				return true;
			}
		};
		final KachleDBManager m = manager;
		final byte[] png = png();
		final Thread ctenar = new Thread(() -> m.load(KACHLE), "ctenar");
		ctenar.setDaemon(true);
		ctenar.start();
		vKontrole.await();
		final Thread zapisovac = new Thread(() -> m.save(Collections.singleton(new ItemToSave(KACHLE, png))), "zapisovac");
		zapisovac.setDaemon(true);
		zapisovac.start();
		ctenar.join(10000);
		zapisovac.join(10000);
		Assert.assertFalse("čtení skončilo", ctenar.isAlive());
		Assert.assertFalse("zápis skončil", zapisovac.isAlive());
	}

	/** Přerušení vlákna při čtení zavře kanál souboru databáze. */
	private void nactiPrerusene(final ExecutorService vlakno) throws Exception {
		vlakno.submit(() -> {
			Thread.currentThread().interrupt();
			try {
				manager.load(KACHLE);
			} catch (final RuntimeException e) {
				// Čtení při přerušení smí selhat.
			}
			Thread.interrupted();
		}).get();
	}

	@Test
	public void poPreruseniCteniVlaknoCteDal() throws Exception {
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(KACHLE, png()))));
		final ExecutorService vlakno = Executors.newSingleThreadExecutor();
		try {
			Assert.assertNotNull(vlakno.submit(() -> manager.load(KACHLE)).get());
			nactiPrerusene(vlakno);
			Assert.assertNotNull("stejné vlákno čte dál", vlakno.submit(() -> manager.load(KACHLE)).get());
		} finally {
			vlakno.shutdown();
		}
	}

	@Test
	public void preruseniPriOtevreniCacheNeodlozi() throws Exception {
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(KACHLE, png()))));
		for (final SqlJetDb db : manager.connections.values()) {
			db.close();
		}
		manager = new KachleDBManager(manager.folderHolder);
		final ExecutorService vlakno = Executors.newSingleThreadExecutor();
		try {
			nactiPrerusene(vlakno);
			Assert.assertFalse("zdravá cache se neodkládá", new File(soubor.getPath() + ".vadna").exists());
			Assert.assertNotNull("dlaždice v cache zůstala", vlakno.submit(() -> manager.load(KACHLE)).get());
		} finally {
			vlakno.shutdown();
		}
	}

	/** Poškozený soubor potká najednou víc vláken, odložit se smí jen on, ne nová cache, kterou mezitím založilo jiné vlákno. */
	@Test
	public void poskozenouCacheOdlozJenJednou() throws Exception {
		final byte[] smeti = new byte[20_000];
		new java.util.Random(1).nextBytes(smeti);
		for (int pokus = 0; pokus < 5; pokus++) {
			for (final SqlJetDb db : manager.connections.values()) {
				db.close();
			}
			manager = new KachleDBManager(manager.folderHolder);
			new File(soubor.getPath() + ".vadna").delete();
			Files.write(soubor.toPath(), smeti);
			final ExecutorService vlakna = Executors.newFixedThreadPool(8);
			final java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
			final java.util.List<java.util.concurrent.Future<Boolean>> ulozeni = new java.util.ArrayList<>();
			try {
				for (int i = 0; i < 8; i++) {
					ulozeni.add(vlakna.submit(() -> {
						start.await();
						manager.load(KACHLE);
						return manager.save(Collections.singleton(new ItemToSave(KACHLE, png())));
					}));
				}
				start.countDown();
				for (final java.util.concurrent.Future<Boolean> u : ulozeni) {
					Assert.assertTrue("uložení do nové cache", u.get());
				}
			} finally {
				vlakna.shutdown();
			}
			Assert.assertEquals("odložený je původní poškozený soubor", smeti.length, new File(soubor.getPath() + ".vadna").length());
			Assert.assertNotNull("dlaždice je v nové cache", manager.load(KACHLE));
		}
	}
	/** Na skoro plný disk se dlaždice neukládají, po uvolnění místa zase ano. */
	@Test
	public void priNedostatkuMistaNezapisuje() throws Exception {
		final long[] volne = { KachleDBManager.MIN_VOLNE_MISTO / 2 };
		manager = new KachleDBManager(manager.folderHolder) {
			@Override
			long volneMisto(final File slozka) {
				return volne[0];
			}
		};
		Assert.assertFalse("bez místa se neukládá", manager.save(Collections.singleton(new ItemToSave(KACHLE, png()))));
		Assert.assertNull(manager.load(KACHLE));
		volne[0] = Long.MAX_VALUE;
		Assert.assertTrue("po uvolnění místa se ukládá", manager.save(Collections.singleton(new ItemToSave(KACHLE, png()))));
		Assert.assertNotNull(manager.load(KACHLE));
	}

	/** Dlaždice se přestanou ukládat, když na disku zbývá méně než 100 MB. */
	@Test
	public void prahVolnehoMistaJeStoMegabajtu() throws Exception {
		final long[] volne = { 99L << 20 };
		manager = new KachleDBManager(manager.folderHolder) {
			@Override
			long volneMisto(final File slozka) {
				return volne[0];
			}

			@Override
			void upozorniNaMisto(final File slozka, final long volne) {}
		};
		Assert.assertFalse("99 MB volna nestačí", manager.save(Collections.singleton(new ItemToSave(KACHLE, png()))));
		volne[0] = 101L << 20;
		Assert.assertTrue("101 MB volna stačí", manager.save(Collections.singleton(new ItemToSave(KACHLE, png()))));
	}

	/** Při opakovaném nedostatku místa se uživatel dozví jen jednou. */
	@Test
	public void naNedostatekMistaUpozorniJednou() throws Exception {
		final int[] upozorneni = { 0 };
		manager = new KachleDBManager(manager.folderHolder) {
			@Override
			long volneMisto(final File slozka) {
				return 0;
			}

			@Override
			void upozorniNaMisto(final File slozka, final long volne) {
				upozorneni[0]++;
			}
		};
		for (int i = 0; i < 3; i++) {
			Assert.assertFalse(manager.save(Collections.singleton(new ItemToSave(kachle(i), png()))));
		}
		Assert.assertEquals(1, upozorneni[0]);
	}

	/** Poškození zjištěné při zápisu dlaždic cache odloží a založí novou. */
	@Test
	public void poskozeniPriZapisuCacheOdlozi() throws Exception {
		poskodStranku(2);
		manager = new KachleDBManager(manager.folderHolder);
		Assert.assertFalse(manager.save(Collections.singleton(new ItemToSave(kachle(5), png()))));
		Assert.assertTrue("poškozená cache se odloží", new File(soubor.getPath() + ".vadna").isFile());
		Assert.assertTrue("nová cache funguje", manager.save(Collections.singleton(new ItemToSave(kachle(5), png()))));
		Assert.assertNotNull(manager.load(kachle(5)));
	}

	/** Chyba starého spojení zjištěná až po odložení poškozené cache neodloží novou. */
	@Test
	public void chybaStarehoSpojeniNeodloziNovouCache() throws Exception {
		poskodStranku(2);
		manager = new KachleDBManager(manager.folderHolder);
		final int pred = manager.odlozeni;
		Assert.assertNull(manager.load(kachle(1)));
		Assert.assertTrue(new File(soubor.getPath() + ".vadna").isFile());
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(kachle(1), png()))));
		manager.odlozZaBehu(soubor, pred);
		Assert.assertNotNull("nová cache zůstala", manager.load(kachle(1)));
	}

	/** Poškození zjištěné až při čtení dlaždice cache odloží a založí novou. */
	@Test
	public void poskozeniPriCteniCacheOdlozi() throws Exception {
		poskodStranku(2);
		manager = new KachleDBManager(manager.folderHolder);
		for (int i = 0; i < 200; i++) {
			Assert.assertNull("dlaždice z poškozené cache se stáhne znovu", manager.load(kachle(i)));
		}
		Assert.assertTrue("poškozená cache se odloží", new File(soubor.getPath() + ".vadna").isFile());
		Assert.assertTrue("nová cache funguje", manager.save(Collections.singleton(new ItemToSave(kachle(1), png()))));
		Assert.assertNotNull(manager.load(kachle(1)));
	}

	/** Žurnál po pádu programu znamená možné poškození, cache se před použitím projde. */
	@Test
	public void poPaduSeCacheZkontroluje() throws Exception {
		poskodStranku(-1);
		Files.write(new File(soubor.getPath() + "-journal").toPath(), new byte[0]);
		manager = new KachleDBManager(manager.folderHolder);
		// Hledání dlaždice mimo cache poslední stránku nečte, poškození najde jen kontrola.
		Assert.assertNull(manager.load(KACHLE_MIMO));
		Assert.assertTrue("zápis počká na kontrolu a jde do nové cache", manager.save(Collections.singleton(new ItemToSave(kachle(1), png()))));
		Assert.assertTrue("poškozená cache se odloží", new File(soubor.getPath() + ".vadna").isFile());
		Assert.assertNotNull(manager.load(kachle(1)));
	}

	/** Kontrola po pádu trvá u velké cache i desítky sekund; dlaždice se mezitím čtou a zápis na ni počká. */
	@Test(timeout = 60000)
	public void kontrolaPoPaduNeblokujeCteni() throws Exception {
		ulozKachle();
		Files.write(new File(soubor.getPath() + "-journal").toPath(), new byte[0]);
		final CountDownLatch kontrolaZacala = new CountDownLatch(1);
		final CountDownLatch pustKontrolu = new CountDownLatch(1);
		manager = new KachleDBManager(manager.folderHolder) {
			@Override
			void projdi(final SqlJetDb database) throws SqlJetException {
				kontrolaZacala.countDown();
				try {
					pustKontrolu.await();
				} catch (final InterruptedException e) {
					throw new IllegalStateException(e);
				}
				super.projdi(database);
			}
		};
		final ExecutorService vlakno = Executors.newSingleThreadExecutor();
		try {
			final Future<Image> nacteni = vlakno.submit(() -> manager.load(kachle(150)));
			Assert.assertNotNull("dlaždice se načte během kontroly", nacteni.get(10, TimeUnit.SECONDS));
			Assert.assertTrue(kontrolaZacala.await(10, TimeUnit.SECONDS));
			final Future<Boolean> ulozeni = vlakno.submit(() -> manager.save(Collections.singleton(new ItemToSave(kachle(300), png()))));
			Thread.sleep(300);
			Assert.assertFalse("zápis čeká na konec kontroly", ulozeni.isDone());
			pustKontrolu.countDown();
			Assert.assertTrue(ulozeni.get(10, TimeUnit.SECONDS));
			Assert.assertNotNull(manager.load(kachle(300)));
			Assert.assertFalse(new File(soubor.getPath() + ".vadna").exists());
		} finally {
			pustKontrolu.countDown();
			vlakno.shutdownNow();
		}
	}

	/** Zdravou cache s žurnálem kontrola neodloží. */
	@Test
	public void kontrolaPoPaduZdravouCacheNeodlozi() throws Exception {
		ulozKachle();
		Files.write(new File(soubor.getPath() + "-journal").toPath(), new byte[0]);
		manager = new KachleDBManager(manager.folderHolder);
		Assert.assertNotNull(manager.load(kachle(150)));
		Assert.assertFalse(new File(soubor.getPath() + ".vadna").exists());
	}

	/** Zápis jiného programu do cache: dlaždice se bere jako chybějící, bez chyby a se zachovaným spojením. */
	@Test(timeout = 60000)
	public void zamekJinehoProgramuJeJenChybejiciDlazdice() throws Exception {
		ulozKachle();
		manager = new KachleDBManager(manager.folderHolder);
		Assert.assertNotNull(manager.load(kachle(1)));
		final Process jiny = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(), "-cp", System.getProperty("java.class.path"),
				ZamekJinehoProgramu.class.getName(), soubor.getPath()).redirectErrorStream(true).start();
		try {
			final java.io.BufferedReader vystup = new java.io.BufferedReader(new java.io.InputStreamReader(jiny.getInputStream(), StandardCharsets.UTF_8));
			for (String radek = vystup.readLine(); !"zamceno".equals(radek); radek = vystup.readLine()) {
				Assert.assertNotNull("druhý program skončil bez zámku", radek);
			}
			final SqlJetDb spojeni = manager.connections.values().iterator().next();
			final int chybPred = ChybyVDiagnostice.pocet("Nepodařilo se");
			Assert.assertNull("zamčená cache = dlaždice není v cache", manager.load(kachle(2)));
			Assert.assertEquals("zámek se nehlásí jako chyba", chybPred, ChybyVDiagnostice.pocet("Nepodařilo se"));
			Assert.assertTrue("spojení se nezahodí", manager.connections.containsValue(spojeni));
		} finally {
			jiny.getOutputStream().write('\n');
			jiny.getOutputStream().flush();
			jiny.waitFor(10, TimeUnit.SECONDS);
			jiny.destroyForcibly();
		}
		Assert.assertNotNull("po uvolnění se dlaždice přečte", manager.load(kachle(2)));
		Assert.assertFalse(new File(soubor.getPath() + ".vadna").exists());
	}

	/** Když vlákno kontroly nejde spustit (nedostatek paměti), zápis nesmí čekat navždy. */
	@Test(timeout = 60000)
	public void nespustenaKontrolaNeblokujeZapis() throws Exception {
		ulozKachle();
		Files.write(new File(soubor.getPath() + "-journal").toPath(), new byte[0]);
		manager = new KachleDBManager(manager.folderHolder) {
			@Override
			void spustVlakno(final Runnable kontrola) {
				throw new OutOfMemoryError("unable to create native thread");
			}
		};
		final ExecutorService vlakno = Executors.newSingleThreadExecutor();
		try {
			final Future<Boolean> ulozeni = vlakno.submit(() -> manager.save(Collections.singleton(new ItemToSave(kachle(300), png()))));
			Assert.assertTrue(ulozeni.get(10, TimeUnit.SECONDS));
			Assert.assertNotNull(manager.load(kachle(300)));
		} finally {
			vlakno.shutdownNow();
		}
	}

	private static final Ka KACHLE_MIMO = new Ka(KaLoc.ofJZ(new Mou(0x10000000, 0x10000000), 13), EKaType.TURIST_M);

	private static Ka kachle(final int i) {
		return new Ka(KaLoc.ofJZ(new Mou(0x40000000 + i * 0x100000, 0x20000000), 13), EKaType.TURIST_M);
	}

	private void ulozKachle() throws Exception {
		final byte[] png = png();
		final java.util.List<ItemToSave> davka = new java.util.ArrayList<>();
		for (int i = 0; i < 200; i++) {
			davka.add(new ItemToSave(kachle(i), png));
		}
		Assert.assertTrue(manager.save(davka));
		for (final SqlJetDb db : manager.connections.values()) {
			db.close();
		}
	}

	/** Stránka (od 1, záporná od konce) dostane neplatný typ; schéma na první stránce zůstane čitelné. */
	private void poskodStranku(final int cislo) throws Exception {
		ulozKachle();
		final byte[] obsah = Files.readAllBytes(soubor.toPath());
		final int velikost = (obsah[16] & 0xff) << 8 | obsah[17] & 0xff;
		final int stranka = cislo > 0 ? cislo : obsah.length / velikost + 1 + cislo;
		obsah[(stranka - 1) * velikost] = 7;
		Files.write(soubor.toPath(), obsah);
	}
}
