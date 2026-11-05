package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import cz.geokuk.plugins.mapy.kachle.podklady.KachloStav;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;

/** Do stavu posledního zobrazení jde výřez, až když na něm nic nečeká: hotové dlaždice od středu, bez chybných. */
public class StavZobrazeniVyrezuTest {

	private static JKachle dlazdice(final int x, final int px, final int py) {
		final JKachle k = new JKachle(null, new Ka(KaLoc.ofXyz(x, 100, 14), EKaType.TURIST_M));
		k.setBounds(px, py, 256, 256);
		return k;
	}

	private static void hotova(final JKachle k) {
		k.prijmi(new KachloStav(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)), Priority.KACHLE);
	}

	@Test
	public void hotoveOdStreduBezChybnych() {
		final JKachle okraj = dlazdice(1, 0, 0);
		final JKachle stred = dlazdice(2, 256, 0);
		final JKachle chybna = dlazdice(3, 512, 0);
		hotova(okraj);
		hotova(stred);
		chybna.prijmi(new KachloStav(new RuntimeException("chyba")), Priority.KACHLE);
		final List<Ka> v = JKachlovnik.hotoveVyrezu(new Component[] { okraj, stred, chybna }, 768, 256);
		Assert.assertEquals(Arrays.asList(stred.getKa(), okraj.getKa()), v);
	}

	@Test
	public void dokudSeNaDlazdiciCekaNicSeNebere() {
		final JKachle hotovaDlazdice = dlazdice(1, 0, 0);
		hotova(hotovaDlazdice);
		final JKachle cekajici = dlazdice(2, 256, 0);
		Assert.assertNull(JKachlovnik.hotoveVyrezu(new Component[] { hotovaDlazdice, cekajici }, 512, 256));
		hotova(cekajici);
		Assert.assertEquals(2, JKachlovnik.hotoveVyrezu(new Component[] { hotovaDlazdice, cekajici }, 512, 256).size());
	}
}
