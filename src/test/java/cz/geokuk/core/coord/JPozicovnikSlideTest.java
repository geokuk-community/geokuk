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
		for (final Component c : menu.getComponents()) {
			akce.add(((JMenuItem) c).getAction().getClass());
		}
		Assert.assertEquals(akce.toString(), CenterPoziceAction.class, akce.get(0));
		Assert.assertEquals(0, slide.getComponentCount());
	}
}
