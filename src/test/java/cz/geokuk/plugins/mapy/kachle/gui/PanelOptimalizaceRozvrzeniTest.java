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
		for (final boolean neproporcionalni : new boolean[] { false, true }) {
			for (final float meritko : new float[] { 1f, 1.25f, 1.5f }) {
				try {
					SwingUtilities.invokeAndWait(() -> over(meritko, neproporcionalni));
				} catch (final InvocationTargetException e) {
					if (e.getCause() instanceof Error) {
						throw (Error) e.getCause();
					}
					throw new AssertionError(e.getCause());
				}
			}
		}
	}

	/** Text průběhu je popisek: nebere fokus, takže se v něm nekreslí kurzor. */
	@Test
	public void textPrubehuNebereFokusANemaKurzor() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final PanelOptimalizace panel = new PanelOptimalizace(null, () -> {});
			final String text = "Probíhá optimalizace, může to chvíli trvat.";
			panel.nastav(PanelOptimalizace.Faze.BEZI, text);
			final JTextArea zprava = zprava(panel, text);
			Assert.assertFalse(zprava.isFocusable());
			Assert.assertFalse(zprava.requestFocusInWindow());
			Assert.assertFalse(zprava.getCaret().isVisible());
		});
	}

	/** S {@code neproporcionalni} má JTextArea neproporcionální písmo Monospaced 13 jako ve vzhledu Windows. */
	private static void over(final float meritko, final boolean neproporcionalni) {
		final Object puvodniTextu = UIManager.get("TextArea.font");
		final Object puvodniPopisku = UIManager.get("Label.font");
		final Font textu = neproporcionalni ? new Font(Font.MONOSPACED, Font.PLAIN, 13) : UIManager.getFont("TextArea.font");
		final Font popisku = UIManager.getFont("Label.font");
		UIManager.put("TextArea.font", new FontUIResource(textu.deriveFont(textu.getSize2D() * meritko)));
		UIManager.put("Label.font", new FontUIResource(popisku.deriveFont(popisku.getSize2D() * meritko)));
		final String popis = meritko + (neproporcionalni ? " Monospaced: " : ": ");
		try {
			final PanelOptimalizace panel = new PanelOptimalizace(null, () -> {});
			panel.nastav(PanelOptimalizace.Faze.BEZI, "Probíhá optimalizace, může to chvíli trvat.");
			final Dimension zabaleno = panel.getPreferredSize();
			panel.setSize(zabaleno);
			for (final String text : PRUBEHY) {
				panel.ukazPrubeh(text);
				final JTextArea zprava = zprava(panel, text);
				Assert.assertEquals(popis + "styl písma", Font.PLAIN, zprava.getFont().getStyle());
				// Bez okna by preferovaná velikost zůstala v mezipaměti.
				zprava.invalidate();
				Assert.assertEquals(popis + text, zabaleno, panel.getPreferredSize());
				rozvrhni(panel);
				Assert.assertTrue(popis + text, zprava.getPreferredSize().width <= zprava.getWidth());
				Assert.assertTrue(popis + text, zprava.getPreferredSize().height <= zprava.getHeight());
				// Okno může být širší kvůli ostatním textům; zaručená je jen šířka podle počtu sloupců.
				final int vyska = zprava.getHeight();
				zprava.setSize(zprava.getColumns() * zprava.getFontMetrics(zprava.getFont()).charWidth('m') + zprava.getInsets().left + zprava.getInsets().right, vyska);
				Assert.assertTrue(popis + "v nejužším okně: " + text, zprava.getPreferredSize().height <= vyska);
			}
		} finally {
			UIManager.put("TextArea.font", puvodniTextu);
			UIManager.put("Label.font", puvodniPopisku);
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
