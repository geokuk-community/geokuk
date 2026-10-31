package cz.geokuk.plugins.mapy.kachle;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.*;
import cz.geokuk.plugins.mapy.kachle.podklady.*;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleManager.ItemToSave;
import cz.geokuk.util.file.Filex;

/** Opakované injektování získavače do modelu nesmí ztratit složku cache: správa cache zůstane táž a dál zapisuje do své složky. */
public class KachleModelInjectTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static KachleManager spravaZiskavace(final KachleZiskavac ziskavac) throws Exception {
		final Field f = KachleZiskavac.class.getDeclaredField("kachleManager");
		f.setAccessible(true);
		return (KachleManager) f.get(ziskavac);
	}

	@Test
	public void opakovaneInjectNeztratiSlozkuCache() throws Exception {
		final File slozka = tmp.newFolder("cache");
		final KachleModel model = new KachleModel();
		model.getKachleCacheFolderHolder().setKachleCacheDir(new Filex(slozka, false, true));
		final KachleZiskavac prvni = new KachleZiskavac();
		final KachleZiskavac druhy = new KachleZiskavac();

		model.inject(prvni);
		model.inject(druhy);
		model.inject(prvni);

		Assert.assertSame("získavač používá správu cache modelu", model.kachleManager, spravaZiskavace(prvni));
		Assert.assertSame("druhý získavač také", model.kachleManager, spravaZiskavace(druhy));
		Assert.assertEquals("složka cache v modelu zůstala", slozka, model.getKachleCacheFolderHolder().getKachleCacheFolder().getEffectiveFile());

		final Ka ka = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13), EKaType.TURIST_M);
		final ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", png);
		Assert.assertTrue(spravaZiskavace(prvni).save(Collections.singleton(new ItemToSave(ka, png.toByteArray()))));
		Assert.assertTrue("dlaždice je v souboru cache ve složce modelu", new File(slozka, "tiles.sqlite").isFile());
		Assert.assertNotNull(model.kachleManager.load(ka));
	}
}
