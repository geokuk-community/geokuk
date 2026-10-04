package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.FlowLayout;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.util.gui.JIconCheckBox;

/** Výška ovladačů alel na toolbaru nezávisí na tom, jaké alely a ikony data přinesou. */
public class JToolbarOvladaceAlelTest {

	private static JIconCheckBox ovladac(final int vyskaIkony) {
		final JIconCheckBox cb = new JIconCheckBox();
		cb.setMaxVyskaIkony(JToolbarOvladaceAlel.VYSKA_IKONY);
		cb.setIcon(new ImageIcon(new BufferedImage(vyskaIkony, vyskaIkony, BufferedImage.TYPE_INT_ARGB)));
		return cb;
	}

	@Test
	public void vyskaJePevna() {
		final JToolbarOvladaceAlel panel = new JToolbarOvladaceAlel(null);
		panel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
		final int prazdny = panel.getPreferredSize().height;
		panel.add(ovladac(16));
		Assert.assertEquals(prazdny, panel.getPreferredSize().height);
		panel.add(ovladac(41));
		Assert.assertEquals(prazdny, panel.getPreferredSize().height);
		Assert.assertEquals(prazdny, ovladac(41).getPreferredSize().height);
	}
}
