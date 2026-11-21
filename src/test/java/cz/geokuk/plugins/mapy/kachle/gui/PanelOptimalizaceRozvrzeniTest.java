package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import org.junit.Assert;
import org.junit.Test;

/** Text průběhu optimalizace se mění bez nového zabalení okna, proto nesmí měnit velikost panelu ani přetéct. */
public class PanelOptimalizaceRozvrzeniTest {

	private static final String[] PRUBEHY = { OptimalizaceTexty.prubeh(1, 1_845_313, 1L << 20, 0, 1_000),
			OptimalizaceTexty.prubeh(912_345, 1_845_313, 1200L << 20, 300L << 20, 600_000),
			OptimalizaceTexty.prubeh(123_456_789, 987_654_321, 999L << 30, 512L << 30, 86_400_000) };

	@Test
	public void prubehNemeniVelikostPaneluAVejdeSe() throws Exception {
		for (final float meritko : new float[] { 1f, 1.25f, 1.5f }) {
			try {
				SwingUtilities.invokeAndWait(() -> over(meritko));
			} catch (final InvocationTargetException e) {
				if (e.getCause() instanceof Error) {
					throw (Error) e.getCause();
				}
				throw new AssertionError(e.getCause());
			}
		}
	}

	private static void over(final float meritko) {
		final Object puvodni = UIManager.get("TextArea.font");
		final Font pismo = UIManager.getFont("TextArea.font");
		UIManager.put("TextArea.font", new FontUIResource(pismo.deriveFont(pismo.getSize2D() * meritko)));
		try {
			final PanelOptimalizace panel = new PanelOptimalizace(null, () -> {});
			panel.nastav(PanelOptimalizace.Faze.BEZI, "Probíhá optimalizace, může to chvíli trvat.");
			final Dimension zabaleno = panel.getPreferredSize();
			panel.setSize(zabaleno);
			for (final String text : PRUBEHY) {
				panel.ukazPrubeh(text);
				Assert.assertEquals(meritko + ": " + text, zabaleno, panel.getPreferredSize());
				rozvrhni(panel);
				final JTextArea zprava = zprava(panel, text);
				Assert.assertTrue(meritko + ": " + text, zprava.getPreferredSize().height <= zprava.getHeight());
			}
		} finally {
			UIManager.put("TextArea.font", puvodni);
		}
	}

	private static void rozvrhni(final Component c) {
		c.doLayout();
		if (c instanceof Container) {
			for (final Component d : ((Container) c).getComponents()) {
				rozvrhni(d);
			}
		}
	}

	private static JTextArea zprava(final Component c, final String text) {
		if (c instanceof JTextArea && text.equals(((JTextArea) c).getText())) {
			return (JTextArea) c;
		}
		if (c instanceof Container) {
			for (final Component d : ((Container) c).getComponents()) {
				final JTextArea z = zprava(d, text);
				if (z != null) {
					return z;
				}
			}
		}
		return null;
	}
}
