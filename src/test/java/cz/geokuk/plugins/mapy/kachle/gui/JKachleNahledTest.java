package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import cz.geokuk.plugins.mapy.kachle.podklady.KachloStav;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;

public class JKachleNahledTest {

	private static JKachle kachleSNahledem() {
		final KaLoc loc = KaLoc.ofJZ(new Mou(0x23800000, 0x28100000), 13);
		final Ka ka = new Ka(loc, EKaType.OFFLINE_MF);
		final Map<Ka, java.awt.Image> cache = new HashMap<>();
		cache.put(new Ka(loc.rodic(), EKaType.OFFLINE_MF), new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
		final List<NahledDlazdice> nahled = NahledDlazdice.najdi(ka, cache::get, 256);
		Assert.assertFalse(nahled.isEmpty());
		final JKachle jkachle = new JKachle(null, ka);
		jkachle.nastavNahled(nahled);
		return jkachle;
	}

	@Test
	public void nahledSeZahodiPoDoruceniObrazku() {
		final JKachle jkachle = kachleSNahledem();
		Assert.assertTrue(jkachle.maNahled());
		jkachle.prijmi(new KachloStav(new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)), Priority.KACHLE);
		Assert.assertFalse(jkachle.maNahled());
	}

	@Test
	public void nahledSeZahodiPoChybe() {
		final JKachle jkachle = kachleSNahledem();
		jkachle.prijmi(new KachloStav(new RuntimeException("chyba")), Priority.KACHLE);
		Assert.assertFalse(jkachle.maNahled());
		Assert.assertTrue(jkachle.jeChybna());
	}
}
