package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Component;
import java.util.*;

import javax.swing.JPanel;

import org.junit.Assert;
import org.junit.Test;

/** Dlaždice se získávají od středu okna: na pomalém podkladu se nejdřív zaplní to, kam se uživatel dívá. */
public class JKachlovnikPoradiTest {

	@Test
	public void odStreduKOkrajum() {
		// Okno 1000×600, mřížka dlaždic 256 px posunutá jako při posunu mapy.
		final List<Component> kachle = new ArrayList<>();
		for (int y = -100; y < 600; y += 256) {
			for (int x = -50; x < 1000; x += 256) {
				final JPanel k = new JPanel();
				k.setBounds(x, y, 256, 256);
				kachle.add(k);
			}
		}
		JKachlovnik.seradOdStredu(kachle, 1000, 600);
		final Component prvni = kachle.get(0);
		Assert.assertTrue("první dlaždice obsahuje střed okna", prvni.getBounds().contains(500, 300));
		double predchozi = -1;
		for (final Component k : kachle) {
			final double d = Math.hypot(k.getX() + 128 - 500, k.getY() + 128 - 300);
			Assert.assertTrue(d >= predchozi);
			predchozi = d;
		}
		final Component posledni = kachle.get(kachle.size() - 1);
		Assert.assertTrue("poslední je v rohu", posledni.getX() < 0 || posledni.getX() > 700);
	}
}
