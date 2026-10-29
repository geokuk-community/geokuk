package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import javax.imageio.ImageIO;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.*;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleManager.ItemToSave;
import cz.geokuk.util.file.Filex;

public class KachleGeneraceManagerTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final Ka ONLINE = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13), EKaType.TURIST_M);
	private static final Ka OFFLINE = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13), EKaType.OFFLINE_MF);
	private static final String TYP = "o0000002a.0000ffff";

	private File slozka;
	private KachleCacheFolderHolder holder;
	private KachleGeneraceManager manager;

	@Before
	public void setUp() throws Exception {
		slozka = tmp.newFolder("cache");
		holder = new KachleCacheFolderHolder();
		holder.setKachleCacheDir(new Filex(slozka, false, true));
		manager = new KachleGeneraceManager(holder);
	}

	private static byte[] png() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}

	private File soubor(final String jmeno) {
		return new File(slozka, jmeno);
	}

	@Test
	public void onlineDlazdiceZustavajiVPuvodnimSouboruOfflineJdouDoSveho() throws Exception {
		Assert.assertTrue(manager.save(Arrays.asList(new ItemToSave(ONLINE, png()), new ItemToSave(OFFLINE, TYP, png()))));
		Assert.assertTrue(soubor("tiles.sqlite").isFile());
		Assert.assertTrue(soubor(KachleGeneraceManager.SOUBOR_OFFLINE).isFile());
		Assert.assertFalse("nový online soubor neexistuje", soubor("tiles-online-1.sqlite").exists());
		Assert.assertNotNull(manager.load(ONLINE));
		Assert.assertNotNull(manager.load(OFFLINE, TYP));
		Assert.assertTrue(manager.exists(ONLINE));
		Assert.assertTrue(manager.exists(OFFLINE, TYP));
		final KachleDBManager puvodni = new KachleDBManager(holder);
		final KachleDBManager jenOffline = new KachleDBManager(holder, KachleGeneraceManager.SOUBOR_OFFLINE);
		Assert.assertNotNull(puvodni.load(ONLINE));
		Assert.assertNull("offline dlaždice v původním souboru není", puvodni.load(OFFLINE, TYP));
		Assert.assertNotNull(jenOffline.load(OFFLINE, TYP));
		Assert.assertNull(jenOffline.load(ONLINE));
	}

	@Test
	public void starsiOfflineDlazdiceSeCtouZPuvodnihoSouboruAZapisySeDoNehoNedelaji() throws Exception {
		final KachleDBManager puvodni = new KachleDBManager(holder);
		Assert.assertTrue(puvodni.save(Collections.singleton(new ItemToSave(OFFLINE, TYP, png()))));
		final byte[] pred = Files.readAllBytes(soubor("tiles.sqlite").toPath());
		Assert.assertNotNull(manager.load(OFFLINE, TYP));
		Assert.assertTrue(manager.exists(OFFLINE, TYP));
		final String jiny = "o0000002b.0000ffff";
		Assert.assertNull(manager.load(OFFLINE, jiny));
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(OFFLINE, jiny, png()))));
		Assert.assertNotNull(manager.load(OFFLINE, jiny));
		Assert.assertNull("nová offline dlaždice není v původním souboru", puvodni.load(OFFLINE, jiny));
		manager.predotevri();
		puvodni.pozastav();
		Assert.assertArrayEquals("původní soubor se nezměnil", pred, Files.readAllBytes(soubor("tiles.sqlite").toPath()));
	}

	@Test
	public void novaOfflineDlazdiceMaPrednostPredStarou() throws Exception {
		Assert.assertTrue(new KachleDBManager(holder).save(Collections.singleton(new ItemToSave(OFFLINE, TYP, png()))));
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(OFFLINE, TYP, png()))));
		Assert.assertNotNull(new KachleDBManager(holder, KachleGeneraceManager.SOUBOR_OFFLINE).load(OFFLINE, TYP));
		Assert.assertNotNull(manager.load(OFFLINE, TYP));
	}

	@Test
	public void bezPuvodnihoSouboruSeKvuliOfflineNezaklada() throws Exception {
		Assert.assertNull(manager.load(OFFLINE, TYP));
		Assert.assertFalse(manager.exists(OFFLINE, TYP));
		Assert.assertFalse(soubor("tiles.sqlite").exists());
	}

	@Test
	public void optimalizaceSeTykaPuvodnihoSouboru() throws Exception {
		Assert.assertNotNull(manager.optimalizace());
		Assert.assertEquals(soubor("tiles.sqlite"), new KachleDBManager(holder).souborCache());
	}

	@Test
	public void offlineTypPoznaPodleTvaru() {
		Assert.assertTrue(UklidOfflineCache.jeOffline("o0000002a"));
		Assert.assertTrue(UklidOfflineCache.jeOffline("t0000002a.0000ffff"));
		Assert.assertFalse(UklidOfflineCache.jeOffline("TURIST_M"));
		Assert.assertFalse(UklidOfflineCache.jeOffline("o0000002"));
		Assert.assertFalse(UklidOfflineCache.jeOffline(null));
	}

	@Test
	public void souctySmazanychDlazdicSeSectouANedokonceniVyhraje() {
		Assert.assertEquals(0, KachleGeneraceManager.soucet(0, 0));
		Assert.assertEquals(5, KachleGeneraceManager.soucet(2, 3));
		Assert.assertEquals(3, KachleGeneraceManager.soucet(3, 0));
		Assert.assertEquals(3, KachleGeneraceManager.soucet(0, 3));
		Assert.assertEquals(KachleDBManager.NEDOKONCENO, KachleGeneraceManager.soucet(-1, 3));
		Assert.assertEquals(KachleDBManager.NEDOKONCENO, KachleGeneraceManager.soucet(3, -1));
		Assert.assertEquals(KachleDBManager.NEDOKONCENO, KachleGeneraceManager.soucet(-1, -1));
	}

	@Test
	public void uklidOfflineDlazdicSeTykaObouSouboru() throws Exception {
		final String ponechana = "o0000000a.0000ffff";
		final String stara = "o0000000b.0000ffff";
		final KachleDBManager puvodni = new KachleDBManager(holder);
		Assert.assertTrue(puvodni.save(Arrays.asList(new ItemToSave(OFFLINE, stara, png()), new ItemToSave(OFFLINE, ponechana, png()))));
		Assert.assertTrue(manager.save(Arrays.asList(new ItemToSave(OFFLINE, stara, png()))));
		Assert.assertEquals(2, manager.smazOfflineKrome(Collections.singleton("o0000000a")));
		Assert.assertNotNull(manager.load(OFFLINE, ponechana));
		Assert.assertNull(manager.load(OFFLINE, stara));
	}

	private static final java.util.function.LongSupplier POZDE = () -> System.currentTimeMillis() + 3_600_000;

	@Test
	public void rotaceOfflineGeneraciPriStartuZachovaOnlineAPuvodniSoubor() throws Exception {
		final KachleDBManager puvodni = new KachleDBManager(holder);
		final String starsi = "o0000002b.0000ffff";
		Assert.assertTrue(puvodni.save(Arrays.asList(new ItemToSave(ONLINE, png()), new ItemToSave(OFFLINE, starsi, png()))));
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(OFFLINE, TYP, png()))));
		manager.predotevri();
		// Další start: offline soubor přesahuje limit, generace 1 se stane předchozí.
		final KachleGeneraceManager druhy = new KachleGeneraceManager(holder, 100, POZDE);
		Assert.assertNotNull("offline z předchozí generace", druhy.load(OFFLINE, TYP));
		Assert.assertNotNull("starší offline z původního souboru", druhy.load(OFFLINE, starsi));
		Assert.assertNotNull("online z původního souboru", druhy.load(ONLINE));
		Assert.assertEquals(Collections.singletonList("offline=2"), Files.readAllLines(soubor("generace.txt").toPath()));
		final String nova = "o0000002c.0000ffff";
		Assert.assertTrue(druhy.save(Collections.singleton(new ItemToSave(OFFLINE, nova, png()))));
		Assert.assertTrue(soubor("tiles-offline-2.sqlite").isFile());
		druhy.predotevri();
		// Další start: generace 2 je nad limitem, generace 1 se smaže; online a původní soubor zůstávají.
		final KachleGeneraceManager treti = new KachleGeneraceManager(holder, 100, POZDE);
		Assert.assertNotNull(treti.load(OFFLINE, nova));
		Assert.assertNull("offline z generace 1 zmizela", treti.load(OFFLINE, TYP));
		Assert.assertNotNull("starší offline v původním souboru zůstává", treti.load(OFFLINE, starsi));
		Assert.assertNotNull("online zůstává", treti.load(ONLINE));
		Assert.assertFalse(soubor("tiles-offline-1.sqlite").exists());
		Assert.assertTrue(soubor("tiles.sqlite").isFile());
	}
}
