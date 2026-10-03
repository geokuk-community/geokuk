package cz.geokuk.plugins.kesoidpopisky;

import java.awt.Font;

import org.junit.Assert;
import org.junit.Test;

/** Písmo popisků se přenáší mezi modelem a výběrem písma oběma směry. */
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
}
