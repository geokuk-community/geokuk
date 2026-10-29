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
	public void onlineAOfflineDlazdiceJdouKazdaDoSvehoSouboru() throws Exception {
		Assert.assertTrue(manager.save(Arrays.asList(new ItemToSave(ONLINE, png()), new ItemToSave(OFFLINE, TYP, png()))));
		Assert.assertTrue(soubor(KachleGeneraceManager.SOUBOR_ONLINE).isFile());
		Assert.assertTrue(soubor(KachleGeneraceManager.SOUBOR_OFFLINE).isFile());
		Assert.assertFalse("starý soubor se nezakládá", soubor("tiles.sqlite").exists());
		Assert.assertNotNull(manager.load(ONLINE));
		Assert.assertNotNull(manager.load(OFFLINE, TYP));
		Assert.assertTrue(manager.exists(ONLINE));
		Assert.assertTrue(manager.exists(OFFLINE, TYP));
		// Každý soubor má jen své dlaždice.
		final KachleDBManager jenOnline = new KachleDBManager(holder, KachleGeneraceManager.SOUBOR_ONLINE);
		final KachleDBManager jenOffline = new KachleDBManager(holder, KachleGeneraceManager.SOUBOR_OFFLINE);
		Assert.assertNotNull(jenOnline.load(ONLINE));
		Assert.assertNull(jenOnline.load(OFFLINE, TYP));
		Assert.assertNotNull(jenOffline.load(OFFLINE, TYP));
		Assert.assertNull(jenOffline.load(ONLINE));
	}

	@Test
	public void dlazdiceZeStarehoSouboruSeCtouAZapisySeDoNehoNedelaji() throws Exception {
		final KachleDBManager stary = new KachleDBManager(holder);
		Assert.assertTrue(stary.save(Arrays.asList(new ItemToSave(ONLINE, png()), new ItemToSave(OFFLINE, TYP, png()))));
		final byte[] pred = Files.readAllBytes(soubor("tiles.sqlite").toPath());
		Assert.assertNotNull(manager.load(ONLINE));
		Assert.assertNotNull(manager.load(OFFLINE, TYP));
		Assert.assertTrue(manager.exists(ONLINE));
		Assert.assertTrue(manager.exists(OFFLINE, TYP));
		final Ka jina = new Ka(KaLoc.ofJZ(new Mou(0x50000000, 0x20000000), 13), EKaType.TURIST_M);
		Assert.assertNull(manager.load(jina));
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(jina, png()))));
		Assert.assertNotNull(manager.load(jina));
		Assert.assertNull("nová dlaždice není ve starém souboru", stary.load(jina));
		manager.predotevri();
		stary.pozastav();
		Assert.assertArrayEquals("starý soubor se nezměnil", pred, Files.readAllBytes(soubor("tiles.sqlite").toPath()));
	}

	@Test
	public void novaDlazdiceMaPrednostPredStarou() throws Exception {
		final KachleDBManager stary = new KachleDBManager(holder);
		Assert.assertTrue(stary.save(Collections.singleton(new ItemToSave(OFFLINE, TYP, png()))));
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(OFFLINE, TYP, png()))));
		Assert.assertNotNull(new KachleDBManager(holder, KachleGeneraceManager.SOUBOR_OFFLINE).load(OFFLINE, TYP));
		Assert.assertNotNull(manager.load(OFFLINE, TYP));
	}

	@Test
	public void bezStarehoSouboruSeNezaklada() throws Exception {
		Assert.assertNull(manager.load(ONLINE));
		Assert.assertFalse(manager.exists(OFFLINE, TYP));
		manager.predotevri();
		Assert.assertFalse(soubor("tiles.sqlite").exists());
	}

	@Test
	public void optimalizaceSeTykaStarehoSouboru() throws Exception {
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

	private boolean cekejNa(final java.util.function.BooleanSupplier podminka) throws Exception {
		for (int i = 0; i < 200 && !podminka.getAsBoolean(); i++) {
			Thread.sleep(50);
		}
		return podminka.getAsBoolean();
	}

	@Test
	public void onlineDlazdiceNalezenaVeStarymSouboruSePovysiDoNoveho() throws Exception {
		final KachleDBManager stary = new KachleDBManager(holder);
		Assert.assertTrue(stary.save(Collections.singleton(new ItemToSave(ONLINE, png()))));
		final KachleDBManager novy = new KachleDBManager(holder, KachleGeneraceManager.SOUBOR_ONLINE);
		Assert.assertNull(novy.load(ONLINE));
		Assert.assertNotNull(manager.load(ONLINE));
		Assert.assertTrue("dlaždice se zapíše do nového souboru", cekejNa(() -> novy.load(ONLINE) != null));
		Assert.assertNotNull("ve starém zůstává", stary.load(ONLINE));
	}

	@Test
	public void offlineDlazdiceSeNepovysuje() throws Exception {
		final KachleDBManager stary = new KachleDBManager(holder);
		Assert.assertTrue(stary.save(Collections.singleton(new ItemToSave(OFFLINE, TYP, png()))));
		Assert.assertNotNull(manager.load(OFFLINE, TYP));
		Thread.sleep(2500);
		Assert.assertNull(new KachleDBManager(holder, KachleGeneraceManager.SOUBOR_OFFLINE).load(OFFLINE, TYP));
		Assert.assertNull(new KachleDBManager(holder, KachleGeneraceManager.SOUBOR_ONLINE).load(OFFLINE, TYP));
	}

	@Test
	public void dlazdiceKterouNovySouborMaSePovysujeZnovu() throws Exception {
		final KachleDBManager stary = new KachleDBManager(holder);
		Assert.assertTrue(stary.save(Collections.singleton(new ItemToSave(ONLINE, png()))));
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(ONLINE, png()))));
		final KachleDBManager novy = new KachleDBManager(holder, KachleGeneraceManager.SOUBOR_ONLINE);
		final byte[] pred = Files.readAllBytes(soubor(KachleGeneraceManager.SOUBOR_ONLINE).toPath());
		Assert.assertNotNull(manager.load(ONLINE));
		Thread.sleep(2500);
		Assert.assertNotNull(novy.load(ONLINE));
		novy.pozastav();
		Assert.assertArrayEquals(pred, Files.readAllBytes(soubor(KachleGeneraceManager.SOUBOR_ONLINE).toPath()));
	}

	@Test
	public void rotacePriStartuZachovaPovysenouDlazdiciANepouzitouZahodi() throws Exception {
		final Ka pouzita = ONLINE;
		final Ka nepouzita = new Ka(KaLoc.ofJZ(new Mou(0x50000000, 0x20000000), 13), EKaType.TURIST_M);
		Assert.assertTrue(manager.save(Arrays.asList(new ItemToSave(pouzita, png()), new ItemToSave(nepouzita, png()), new ItemToSave(OFFLINE, TYP, png()))));
		manager.predotevri();
		// Další start: soubory přesahují limit, generace 1 se stane předchozí.
		final KachleGeneraceManager druhy = new KachleGeneraceManager(holder, 100, 100);
		Assert.assertNotNull(druhy.load(pouzita));
		Assert.assertNotNull("offline z předchozí generace", druhy.load(OFFLINE, TYP));
		Assert.assertTrue(soubor("tiles-online-1.sqlite").isFile());
		Assert.assertEquals(Arrays.asList("online=2", "offline=2"), Files.readAllLines(soubor("generace.txt").toPath()));
		final KachleDBManager druhaGenerace = new KachleDBManager(holder, "tiles-online-2.sqlite");
		Assert.assertTrue("povýšení do aktuální generace", cekejNa(() -> druhaGenerace.load(pouzita) != null));
		Assert.assertNull("nepoužitá se nepovýšila", druhaGenerace.load(nepouzita));
		Assert.assertNotNull("pořád čitelná z předchozí", druhy.load(nepouzita));
		// Další start: generace 2 je nad limitem, generace 1 se smaže.
		final KachleGeneraceManager treti = new KachleGeneraceManager(holder, 100, 100);
		Assert.assertNotNull(treti.load(pouzita));
		Assert.assertNull("nepoužitá zmizela s generací 1", treti.load(nepouzita));
		Assert.assertFalse(soubor("tiles-online-1.sqlite").exists());
		Assert.assertTrue(soubor("tiles-online-2.sqlite").isFile());
	}

	@Test
	public void rotaceNesmazeStarySouborDokudNerotovalyObeRole() throws Exception {
		final KachleDBManager stary = new KachleDBManager(holder);
		Assert.assertTrue(stary.save(Arrays.asList(new ItemToSave(ONLINE, png()), new ItemToSave(OFFLINE, TYP, png()))));
		Assert.assertTrue(manager.save(Collections.singleton(new ItemToSave(new Ka(KaLoc.ofJZ(new Mou(0x50000000, 0x20000000), 13), EKaType.TURIST_M), png()))));
		manager.predotevri();
		// Rotuje jen online (offline soubor existuje, ale je pod limitem 1 GB).
		final KachleGeneraceManager druhy = new KachleGeneraceManager(holder, 100, 1L << 30);
		Assert.assertNotNull(druhy.load(OFFLINE, TYP));
		Assert.assertTrue(soubor("tiles.sqlite").isFile());
		Assert.assertEquals(Arrays.asList("online=2", "offline=1"), Files.readAllLines(soubor("generace.txt").toPath()));
	}
}
