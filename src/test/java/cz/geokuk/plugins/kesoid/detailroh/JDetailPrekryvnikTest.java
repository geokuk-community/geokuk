package cz.geokuk.plugins.kesoid.detailroh;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.junit.*;

import cz.geokuk.core.coord.PoziceChangedEvent;
import cz.geokuk.core.coord.Poziceq;
import cz.geokuk.core.coord.ZmenaSouradnicMysiEvent;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.cesty.PridavaniBoduEvent;

/** Pohyb myši při nastavené pozici nepřekresluje detail, dokud se jeho střed nezmění. */
public class JDetailPrekryvnikTest {

	private class ZaznamPrekresleni extends RepaintManager {
		final List<Rectangle> oblasti = new ArrayList<>();

		@Override
		public void addDirtyRegion(final JComponent c, final int x, final int y, final int w, final int h) {
			// Jen detail tohoto testu; časovač detailu z jiného testu se může ozvat později.
			if (c == detail) {
				oblasti.add(new Rectangle(x, y, w, h));
			}
		}
	}

	private RepaintManager puvodni;
	private ZaznamPrekresleni zaznam;
	private JDetailPrekryvnik detail;

	@Before
	public void setUp() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		puvodni = RepaintManager.currentManager(null);
		zaznam = new ZaznamPrekresleni();
		RepaintManager.setCurrentManager(zaznam);
		detail = new JDetailPrekryvnik();
		detail.setSize(new Dimension(200, 200));
		final Constructor<PoziceChangedEvent> k = PoziceChangedEvent.class.getDeclaredConstructor(Poziceq.class);
		k.setAccessible(true);
		detail.onEvent(k.newInstance(new Poziceq(new Wgs(50.08, 14.42))));
		// Změna velikosti se zpracuje na EDT později; počkat na ni, ať se nezapočítá do pohybu myši.
		SwingUtilities.invokeAndWait(() -> {});
		zaznam.oblasti.clear();
	}

	@After
	public void tearDown() throws Exception {
		final Field f = JDetailPrekryvnik.class.getDeclaredField("zpozdovaciTimer");
		f.setAccessible(true);
		final Timer timer = (Timer) f.get(detail);
		if (timer != null) {
			timer.stop();
		}
		if (puvodni != null) {
			RepaintManager.setCurrentManager(puvodni);
		}
	}

	private void mys(final int i) {
		final Wgs w = new Wgs(50.0 + i * 0.0001, 14.0);
		detail.onEvent(new ZmenaSouradnicMysiEvent(new java.awt.Point(i, i), w.toMou(), null));
	}

	@Test
	public void sPoziciSePohybMysiNepreklresluje() {
		for (int i = 0; i < 100; i++) {
			mys(i);
		}
		Assert.assertTrue(zaznam.oblasti.toString(), zaznam.oblasti.isEmpty());
	}

	@Test
	public void pridavaniBoduPouzivaJedenTimer() throws Exception {
		detail.onEvent(new PridavaniBoduEvent(true));
		final Field f = JDetailPrekryvnik.class.getDeclaredField("zpozdovaciTimer");
		f.setAccessible(true);
		mys(1);
		final Object timer = f.get(detail);
		Assert.assertNotNull(timer);
		for (int i = 2; i < 50; i++) {
			mys(i);
		}
		Assert.assertSame(timer, f.get(detail));
	}
}
