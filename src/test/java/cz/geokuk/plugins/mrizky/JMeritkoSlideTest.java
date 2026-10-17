package cz.geokuk.plugins.mrizky;

import java.awt.Component;
import java.awt.Container;

import org.junit.Assert;
import org.junit.Test;

/** Měřítko na obrazovce je vlevo dole. */
public class JMeritkoSlideTest {

	private static JMeritko najdi(final Container c) {
		for (final Component k : c.getComponents()) {
			if (k instanceof JMeritko) {
				return (JMeritko) k;
			}
			if (k instanceof Container) {
				final JMeritko m = najdi((Container) k);
				if (m != null) {
					return m;
				}
			}
		}
		return null;
	}

	@Test
	public void meritkoJeVlevoDole() {
		final JMeritkoSlide slide = new JMeritkoSlide();
		slide.setSize(1000, 600);
		slide.doLayout();
		final java.awt.Container vlevo = (java.awt.Container) slide.getComponent(0);
		vlevo.setSize(1000, vlevo.getPreferredSize().height);
		vlevo.doLayout();
		final JMeritko m = najdi(slide);
		Assert.assertNotNull(m);
		Assert.assertEquals(JMeritkoSlide.OKRAJ, m.getX());
	}
}
