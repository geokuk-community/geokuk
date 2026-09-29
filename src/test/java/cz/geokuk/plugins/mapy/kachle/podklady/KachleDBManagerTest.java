package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import javax.imageio.ImageIO;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

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
}
