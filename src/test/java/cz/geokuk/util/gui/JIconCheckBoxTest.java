package cz.geokuk.util.gui;

import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.junit.Assert;
import org.junit.Test;

/** Ikona vyšší než nastavené maximum se zmenší se zachováním poměru stran. */
public class JIconCheckBoxTest {

	private static Icon ikona(final int sirka, final int vyska) {
		return new ImageIcon(new BufferedImage(sirka, vyska, BufferedImage.TYPE_INT_ARGB));
	}

	@Test
	public void vysokaIkonaSeZmensi() {
		final JIconCheckBox cb = new JIconCheckBox();
		cb.setMaxVyskaIkony(24);
		cb.setIcon(ikona(41, 41));
		Assert.assertEquals(24, cb.getIcon().getIconHeight());
		Assert.assertEquals(24, cb.getIcon().getIconWidth());
		Assert.assertEquals(24, cb.getSelectedIcon().getIconHeight());
	}

	@Test
	public void pomerStranZustane() {
		final Icon mensi = JIconCheckBox.zmensi(ikona(60, 30), 24);
		Assert.assertEquals(48, mensi.getIconWidth());
		Assert.assertEquals(24, mensi.getIconHeight());
	}

	@Test
	public void nizsiIkonaZustane() {
		final Icon ikona = ikona(16, 16);
		Assert.assertSame(ikona, JIconCheckBox.zmensi(ikona, 24));
		Assert.assertSame(ikona, JIconCheckBox.zmensi(ikona, 0));
	}
}
