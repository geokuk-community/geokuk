package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Dimension;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.ReliefCuzkEvent;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;

/** Reliéf ČÚZK pod nejmenším měřítkem nevytváří dlaždice, na server se tedy neptá. */
public class JKachlovnikReliefuTest {

	private static Coord meritko(final int z) {
		return new Coord(z, new Wgs(50.08, 14.42).toMou(), new Dimension(800, 600), 0);
	}

	@Test
	public void podMeritkemReliefuZadneDlazdice() {
		final JKachlovnikReliefu k = new JKachlovnikReliefu();
		k.setSize(800, 600);
		k.setKachloType(EKaType.OPEN_STREET);
		k.onEvent(new ReliefCuzkEvent(true));
		k.inject(new KachleModel());
		k.setSoord(meritko(EKaType.RELIEF_CUZK.getMinMoumer() - 1));
		k.init(false);
		Assert.assertEquals(0, k.getComponentCount());
		final JKachlovnikReliefu.Rendrovaci r = new JKachlovnikReliefu.Rendrovaci();
		r.setSize(800, 600);
		r.setKachloType(EKaType.OPEN_STREET);
		r.inject(new KachleModel());
		r.setSoord(meritko(5));
		r.init(false);
		Assert.assertEquals(0, r.getComponentCount());
	}

	@Test
	public void meritko() {
		Assert.assertFalse(JKachlovnikReliefu.jeVMeritku(meritko(11)));
		Assert.assertTrue(JKachlovnikReliefu.jeVMeritku(meritko(12)));
		Assert.assertTrue(JKachlovnikReliefu.jeVMeritku(meritko(20)));
	}
}
