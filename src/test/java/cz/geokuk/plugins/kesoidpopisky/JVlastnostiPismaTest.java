package cz.geokuk.plugins.kesoidpopisky;

import java.awt.*;

import javax.swing.JLabel;

import org.junit.Assert;
import org.junit.Test;

/** Písmo a barvy popisků se přenáší mezi modelem a výběrem písma oběma směry. */
public class JVlastnostiPismaTest {

	@Test
	public void pismoZModeluDoVyberuAZpet() {
		final VlastnostiPismaModel model = new VlastnostiPismaModel();
		final JVlastnostiPisma panel = new JVlastnostiPisma(model);
		final Font serif = new Font(Font.SERIF, Font.BOLD, 20);
		model.setFont(serif);
		Assert.assertEquals(serif, panel.fontChooser.getVybranePismo());
		final Font mono = new Font(Font.MONOSPACED, Font.ITALIC, 10);
		panel.fontChooser.setVybranePismo(mono);
		Assert.assertEquals(mono, model.getFont());
	}

	@Test
	public void ukazkaPismaMaBarvyZModelu() {
		final VlastnostiPismaModel model = new VlastnostiPismaModel();
		final JVlastnostiPisma panel = new JVlastnostiPisma(model);
		model.setForeground(Color.RED);
		model.setBackground(Color.YELLOW);
		Assert.assertEquals(Color.RED, najdiUkazku(panel).getForeground());
	}

	@Test
	public void vyberBarevNemaVychoziNahled() {
		Assert.assertFalse(obsahujeVychoziNahled(new JVlastnostiPisma()));
	}

	private static JLabel najdiUkazku(final Container c) {
		for (final Component k : c.getComponents()) {
			if (k instanceof JLabel && "Žluťoučký kůň 123".equals(((JLabel) k).getText())) {
				return (JLabel) k;
			}
			final JLabel l = k instanceof Container ? najdiUkazku((Container) k) : null;
			if (l != null) {
				return l;
			}
		}
		return null;
	}

	private static boolean obsahujeVychoziNahled(final Container c) {
		for (final Component k : c.getComponents()) {
			if (k.getClass().getName().endsWith("DefaultPreviewPanel") || k instanceof Container && obsahujeVychoziNahled((Container) k)) {
				return true;
			}
		}
		return false;
	}
}
