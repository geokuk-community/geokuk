package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleManager;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleZiskavac;
import cz.geokuk.plugins.mapy.kachle.podklady.KachloStav;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;

/** Náhled dlaždice z hotové dlaždice jiného měřítka v cache na disku, i pro online podklady. */
public class NahledZCacheTest {

	private static final KaLoc LOC = KaLoc.ofJZ(new Mou(0x23800000, 0x28100000), 13);
	private static final Ka KA = new Ka(LOC, EKaType.OPEN_STREET);
	private static final Ka RODIC = new Ka(LOC.rodic(), EKaType.OPEN_STREET);

	private static KachleZiskavac ziskavacSCache(final Map<Ka, Image> disk) {
		final KachleZiskavac ziskavac = new KachleZiskavac();
		ziskavac.setKachleManager(new KachleManager() {
			@Override
			public boolean exists(final Ka ki) {
				return disk.containsKey(ki);
			}

			@Override
			public Image load(final Ka ki) {
				return disk.get(ki);
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				return true;
			}
		});
		return ziskavac;
	}

	@Test
	public void onlineDlazdiceDostaneNahledZCache() throws Exception {
		Assert.assertFalse(EKaType.OPEN_STREET.isOffline());
		final Map<Ka, Image> disk = new HashMap<>();
		disk.put(RODIC, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		final KachleZiskavac ziskavac = ziskavacSCache(disk);
		Assert.assertNull("v paměti rodič není", ziskavac.nahledZPameti(RODIC));
		Assert.assertNotNull(ziskavac.nahledZPametiNeboCache(RODIC));

		final JKachle jkachle = new JKachle(null, KA);
		Assert.assertFalse(jkachle.maNahled());
		final CountDownLatch hotovo = new CountDownLatch(1);
		ziskavac.naPozadiProNahled(() -> {
			jkachle.doplnNahledZCache(ziskavac);
			hotovo.countDown();
		});
		Assert.assertTrue(hotovo.await(10, TimeUnit.SECONDS));
		Assert.assertTrue(jkachle.maNahled());
	}

	@Test
	public void bezDlazdiceVCacheNahledNeni() {
		final KachleZiskavac ziskavac = ziskavacSCache(new HashMap<>());
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.doplnNahledZCache(ziskavac);
		Assert.assertFalse(jkachle.maNahled());
	}

	@Test
	public void nahledSePoDoruceniObrazkuNeukaze() {
		final Map<Ka, Image> disk = new HashMap<>();
		disk.put(RODIC, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		final KachleZiskavac ziskavac = ziskavacSCache(disk);
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.prijmi(new KachloStav(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)), Priority.KACHLE);
		jkachle.doplnNahledZCache(ziskavac);
		Assert.assertFalse(jkachle.maNahled());
	}

	@Test
	public void nahledSePoChybeNeukaze() {
		final Map<Ka, Image> disk = new HashMap<>();
		disk.put(RODIC, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		final KachleZiskavac ziskavac = ziskavacSCache(disk);
		final JKachle jkachle = new JKachle(null, KA);
		jkachle.prijmi(new KachloStav(new RuntimeException("chyba")), Priority.KACHLE);
		jkachle.doplnNahledZCache(ziskavac);
		Assert.assertFalse(jkachle.maNahled());
	}
}
