package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.JLabel;

import org.junit.Assert;
import org.junit.Test;

/** Výška panelu s alelami nezávisí na tom, které alely jsou v datech. */
public class JToolbarOvladaceAlelTest {

	private static JLabel tlacitko(final int vyska) {
		final JLabel l = new JLabel();
		l.setPreferredSize(new Dimension(16, vyska));
		return l;
	}

	@Test
	public void vyskaPodleNejvyssiIkonyIZeSkrytych() {
		final JToolbarOvladaceAlel panel = new JToolbarOvladaceAlel(null);
		panel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
		final JLabel vysoka = tlacitko(24);
		panel.add(tlacitko(16));
		panel.add(vysoka);
		Assert.assertEquals(24, panel.getPreferredSize().height);
		vysoka.setVisible(false);
		Assert.assertEquals(24, panel.getPreferredSize().height);
		panel.removeAll();
		panel.add(tlacitko(16));
		Assert.assertEquals("Po přenačtení keší se toolbar nezmenší", 24, panel.getPreferredSize().height);
	}
}
