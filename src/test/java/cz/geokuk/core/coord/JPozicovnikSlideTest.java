package cz.geokuk.core.coord;

import java.awt.Component;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.Factory;

public class JPozicovnikSlideTest {

	/** Kontextové menu u kříže nabízí i vystředění na kříž. */
	@Test
	public void menuKrizeObsahujeCentrovani() throws Exception {
		final JPozicovnikSlide slide = new JPozicovnikSlide();
		slide.inject(new Factory() {
			@Override
			public <T> T init(final T obj) {
				return obj;
			}

			@Override
			public <T> T initNow(final T obj) {
				return obj;
			}
		});
		slide.onEvent(new PoziceChangedEvent(new Poziceq(new Wgs(50, 14))));
		final Field blizko = JPozicovnikSlide.class.getDeclaredField("mysJePoblizKrize");
		blizko.setAccessible(true);
		blizko.setBoolean(slide, true);

		final JPopupMenu menu = new JPopupMenu();
		slide.addPopouItems(menu, null);

		final List<Object> akce = new ArrayList<>();
		JMenu dalsiMapy = null;
		final List<String> texty = new ArrayList<>();
		for (final Component c : menu.getComponents()) {
			if (c instanceof JMenu) {
				dalsiMapy = (JMenu) c;
			} else {
				akce.add(((JMenuItem) c).getAction().getClass());
				texty.add(((JMenuItem) c).getText());
			}
		}
		Assert.assertEquals(akce.toString(), CenterPoziceAction.class, akce.get(0));
		Assert.assertEquals(0, slide.getComponentCount());
		Assert.assertTrue(texty.toString(), texty.contains("Otevřít v Mapy.com") && texty.contains("Otevřít v Google Mapách"));
		Assert.assertTrue("Mapy.com před Google", texty.indexOf("Otevřít v Mapy.com") + 1 == texty.indexOf("Otevřít v Google Mapách"));
		Assert.assertNotNull("podnabídka Další mapy u kříže", dalsiMapy);
		Assert.assertEquals("Další mapy", dalsiMapy.getText());
		Assert.assertEquals("u kříže je OpenStreetMap samostatná položka, v podnabídce ne", java.util.Arrays.asList("ČÚZK Geoprohlížeč"), texty(dalsiMapy));
	}

	private static List<String> texty(final JMenu menu) {
		final List<String> nazvy = new ArrayList<>();
		for (final Component c : menu.getMenuComponents()) {
			nazvy.add(((JMenuItem) c).getText());
		}
		return nazvy;
	}

	/** Bez kříže a bez známé polohy myši se odkazy na další mapy nenabízejí. */
	@Test
	public void bezPolohyNeniOdkazyNaDalsiMapy() {
		final JPozicovnikSlide slide = new JPozicovnikSlide();
		slide.inject(new Factory() {
			@Override
			public <T> T init(final T obj) {
				return obj;
			}

			@Override
			public <T> T initNow(final T obj) {
				return obj;
			}
		});
		final JPopupMenu menu = new JPopupMenu();
		slide.addPopouItems(menu, null);
		Assert.assertEquals(0, menu.getComponentCount());
	}

	/** Mimo kříž se odkazy nabízejí pro polohu kurzoru a OpenStreetMap je v nich, protože ho jiná položka nenabízí. */
	@Test
	public void mimoKrizJeVPodnabidceOpenStreetMap() {
		final JPozicovnikSlide slide = new JPozicovnikSlide();
		slide.inject(new Factory() {
			@Override
			public <T> T init(final T obj) {
				return obj;
			}

			@Override
			public <T> T initNow(final T obj) {
				return obj;
			}
		});
		slide.setSoord(new Coord(12, new Wgs(50.0755, 14.4378).toMou(), new java.awt.Dimension(800, 600), 0));
		slide.onEvent(new ZmenaSouradnicMysiEvent(new java.awt.Point(400, 300), null, null));
		final JPopupMenu menu = new JPopupMenu();
		slide.addPopouItems(menu, null);
		Assert.assertEquals(3, menu.getComponentCount());
		Assert.assertEquals("Otevřít v Mapy.com", ((JMenuItem) menu.getComponent(0)).getText());
		Assert.assertEquals("Otevřít v Google Mapách", ((JMenuItem) menu.getComponent(1)).getText());
		Assert.assertEquals(java.util.Arrays.asList("OpenStreetMap", "ČÚZK Geoprohlížeč"), texty((JMenu) menu.getComponent(2)));
	}
}
