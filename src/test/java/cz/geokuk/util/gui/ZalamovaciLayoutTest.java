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

	@Test
	public void plovouciNerozhodujeORadcichADostaneZbyleMisto() {
		final JComponent a = pridej(200);
		final JComponent prubeh = pridej(300);
		layout.plovouci(prubeh);
		final JComponent b = pridej(100);
		rozvrhni(320);
		Assert.assertEquals(20, panel.getHeight());
		Assert.assertEquals(new Rectangle(0, 0, 200, 20), a.getBounds());
		Assert.assertEquals(new Rectangle(200, 0, 100, 20), b.getBounds());
		Assert.assertEquals(new Rectangle(300, 0, 20, 20), prubeh.getBounds());
	}

	@Test
	public void pravaKomponentaStojiVpravoDoleAOstatniSeZalamujiVedleNi() {
		final JComponent a = pridej(200);
		final JComponent b = pridej(100);
		final JComponent pravy = pridej(150);
		layout.vpravo(pravy);
		rozvrhni(500);
		Assert.assertEquals(20, panel.getHeight());
		Assert.assertEquals(new Rectangle(350, 0, 150, 20), pravy.getBounds());
		Assert.assertEquals(new Rectangle(200, 0, 100, 20), b.getBounds());

		rozvrhni(420);
		Assert.assertEquals(40, panel.getHeight());
		Assert.assertEquals(new Rectangle(0, 0, 200, 20), a.getBounds());
		Assert.assertEquals(new Rectangle(0, 20, 100, 20), b.getBounds());
		Assert.assertEquals(new Rectangle(270, 20, 150, 20), pravy.getBounds());
	}

	@Test
	public void plovouciKonciPredPravouKomponentou() {
		pridej(200);
		final JComponent prubeh = pridej(300);
		layout.plovouci(prubeh);
		final JComponent pravy = pridej(100);
		layout.vpravo(pravy);
		rozvrhni(400);
		Assert.assertEquals(new Rectangle(200, 0, 100, 20), prubeh.getBounds());
		Assert.assertEquals(new Rectangle(300, 0, 100, 20), pravy.getBounds());
	}
}
