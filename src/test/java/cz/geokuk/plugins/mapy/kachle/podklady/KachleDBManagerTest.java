package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import cz.geokuk.core.coordinates.Mou;
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
}
