package cz.geokuk.core.program;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.TreeSet;

import javax.swing.JPanel;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.MetalTheme;
import javax.swing.plaf.metal.OceanTheme;

import org.junit.Assert;
import org.junit.Test;

/**
 * Stavový řádek při 100 %, 125 % a 150 % zvětšení obrazovky na FHD (okno v logických bodech 1920, 1536, 1280) ve třech tématech: vejde se do jednoho řádku a nic nepřesahuje okraj; potřebuje
 * displej.
 */
public class JStatusBarUsporyTest {

	private static final int[] SIRKY = { 1920, 1536, 1280 };

	private static MetalTheme[] temata() {
		return new MetalTheme[] { new DefaultMetalTheme(), new OceanTheme(), new cz.geokuk.core.lookandfeel.CharcoalTheme() };
	}

	private static JStatusBar lista(final int sirka) {
		final JStatusBar sb = new JStatusBar();
		final JPanel rodic = new JPanel(new BorderLayout());
		rodic.setSize(sirka, 100);
		rodic.add(sb, BorderLayout.SOUTH);
		for (int i = 0; i < 3; i++) {
			rodic.doLayout();
			sb.doLayout();
		}
		return sb;
	}

	private static int radku(final JStatusBar sb) {
		final TreeSet<Integer> ys = new TreeSet<>();
		for (final Component c : sb.getComponents()) {
			if (c.isVisible() && c.getWidth() > 0) {
				ys.add(c.getY());
			}
		}
		return ys.size();
	}

	private interface Kontrola {
		void proved(String tema, int sirka, JStatusBar sb) throws Exception;
	}

	private static void vsudy(final Kontrola k) throws Exception {
		final LookAndFeel puvodni = UIManager.getLookAndFeel();
		final MetalTheme puvodniTema = MetalLookAndFeel.getCurrentTheme();
		try {
			for (final MetalTheme tema : temata()) {
				MetalLookAndFeel.setCurrentTheme(tema);
				UIManager.setLookAndFeel(new MetalLookAndFeel());
				for (final int sirka : SIRKY) {
					SwingUtilities.invokeAndWait(() -> {
						try {
							k.proved(tema.getName(), sirka, lista(sirka));
						} catch (final Exception e) {
							throw new RuntimeException(e);
						}
					});
				}
			}
		} finally {
			MetalLookAndFeel.setCurrentTheme(puvodniTema);
			UIManager.setLookAndFeel(puvodni);
		}
	}

	@Test
	public void vJednomRadkuPri100a125a150Procentech() throws Exception {
		vsudy((tema, sirka, sb) -> Assert.assertEquals(tema + " " + sirka + " stupeň " + sb.getStupenUspor(), 1, radku(sb)));
	}

	@Test
	public void stupenUsporRosteSUzsimOknem() throws Exception {
		vsudy((tema, sirka, sb) -> {
			final int ocekavany = sirka == 1920 ? 0 : sirka == 1536 ? 2 : 3;
			Assert.assertEquals(tema + " " + sirka, ocekavany, sb.getStupenUspor());
		});
	}

	@Test
	public void nicNepresahujeOkrajListyAniSeNekryje() throws Exception {
		vsudy((tema, sirka, sb) -> {
			final BufferedImage obraz = new BufferedImage(sirka + 300, sb.getHeight(), BufferedImage.TYPE_INT_ARGB);
			final Graphics2D g = obraz.createGraphics();
			g.setColor(Color.MAGENTA);
			g.fillRect(0, 0, obraz.getWidth(), obraz.getHeight());
			sb.paint(g);
			g.dispose();
			for (int y = 0; y < obraz.getHeight(); y++) {
				for (int x = sirka; x < obraz.getWidth(); x++) {
					Assert.assertEquals(tema + " " + sirka + ": přesah pixelu " + x + "," + y, Color.MAGENTA.getRGB(), obraz.getRGB(x, y));
				}
			}
			final Rectangle lista = new Rectangle(0, 0, sirka, sb.getHeight());
			final java.util.List<Rectangle> viditelne = new java.util.ArrayList<>();
			for (final Component c : sb.getComponents()) {
				if (c.isVisible() && c.getWidth() > 0) {
					Assert.assertTrue(tema + " " + sirka + ": " + c.getBounds(), lista.contains(c.getBounds()));
					for (final Rectangle jiny : viditelne) {
						Assert.assertFalse(tema + " " + sirka + ": " + c.getBounds() + " × " + jiny, jiny.intersects(c.getBounds()));
					}
					viditelne.add(c.getBounds());
				}
			}
		});
	}

	@Test
	public void usporneStupneSeVybiraji() throws Exception {
		vsudy((tema, sirka, sb) -> {
			final int[] w = sb.sirkyStupnu();
			for (int s = 1; s < w.length; s++) {
				Assert.assertTrue(tema + ": stupeň " + s + " je užší než předchozí", w[s] < w[s - 1]);
			}
			Assert.assertTrue("plná podoba se vejde na 100 %", w[0] <= 1920);
			Assert.assertTrue("úsporná podoba se vejde na 150 %", w[w.length - 1] <= 1280);
		});
	}
}
