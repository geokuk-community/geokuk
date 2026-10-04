package cz.geokuk.util.gui;

import java.awt.*;

import javax.swing.*;

import org.junit.Assert;
import org.junit.Test;

/** Komponenty, které se do řádku nevejdou, přejdou celé na další řádek a nic se neořízne. */
public class ZalamovaciLayoutTest {

	private final ZalamovaciLayout layout = new ZalamovaciLayout();
	private final JPanel rodic = new JPanel(new BorderLayout());
	private final JPanel panel = new JPanel(layout);

	private JComponent pridej(final int sirka) {
		final JPanel c = new JPanel();
		c.setPreferredSize(new Dimension(sirka, 20));
		panel.add(c);
		return c;
	}

	private void rozvrhni(final int sirka) {
		rodic.add(panel, BorderLayout.SOUTH);
		rodic.setSize(sirka, 200);
		rodic.doLayout();
		panel.doLayout();
	}

	@Test
	public void coSeVejdeJeVJednomRadku() {
		pridej(100);
		final JComponent b = pridej(100);
		rozvrhni(300);
		Assert.assertEquals(20, panel.getHeight());
		Assert.assertEquals(new Rectangle(100, 0, 100, 20), b.getBounds());
	}

	@Test
	public void coSeNevejdePrejdeCeleNaDalsiRadek() {
		pridej(200);
		final JComponent b = pridej(100);
		final JComponent c = pridej(150);
		rozvrhni(320);
		Assert.assertEquals(40, panel.getHeight());
		Assert.assertEquals(new Rectangle(200, 0, 100, 20), b.getBounds());
		Assert.assertEquals(new Rectangle(0, 20, 150, 20), c.getBounds());
	}

	@Test
	public void rezervovanaNeviditelnaKomponentaDrziPocetRadku() {
		pridej(200);
		final JComponent rezervovana = pridej(100);
		layout.rezervuj(rezervovana);
		rezervovana.setVisible(false);
		final JComponent c = pridej(100);
		rozvrhni(320);
		Assert.assertEquals(40, panel.getHeight());
		Assert.assertEquals(new Rectangle(0, 20, 100, 20), c.getBounds());

		rezervovana.setVisible(true);
		rozvrhni(320);
		Assert.assertEquals(40, panel.getHeight());
	}

	@Test
	public void neviditelnaNerezervovanaMistoNezabira() {
		pridej(200);
		pridej(100).setVisible(false);
		final JComponent c = pridej(100);
		rozvrhni(320);
		Assert.assertEquals(20, panel.getHeight());
		Assert.assertEquals(new Rectangle(200, 0, 100, 20), c.getBounds());
	}
}
