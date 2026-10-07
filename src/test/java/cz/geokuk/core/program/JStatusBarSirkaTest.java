package cz.geokuk.core.program;

import java.awt.*;
import java.lang.reflect.Field;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/** Ve stavovém řádku užším než všechna pole se žádné pole neořízne ani neskryje a výška nezávisí na datech. */
public class JStatusBarSirkaTest {

	@Test
	public void vUzkemOkneJeVidetVse() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final Field f = JStatusBar.class.getDeclaredField("odPozice");
		f.setAccessible(true);
		((JPanel) f.get(radek)).setVisible(true);
		final JPanel okno = new JPanel(new BorderLayout());
		okno.add(radek, BorderLayout.SOUTH);
		okno.setSize(radek.getPreferredSize().width * 6 / 10, 400);
		okno.doLayout();
		radek.doLayout();
		Assert.assertTrue(radek.getHeight() > radek.getComponent(0).getHeight());
		for (final Component panel : radek.getComponents()) {
			if (!panel.isVisible()) {
				continue;
			}
			Assert.assertTrue(panel.toString(), uvnitr(panel, radek));
			((Container) panel).doLayout();
			for (final Component pole : ((Container) panel).getComponents()) {
				if (pole.isVisible()) {
					Assert.assertTrue(pole.toString(), uvnitr(pole, panel));
				}
			}
		}
	}

	private static boolean uvnitr(final Component c, final Component rodic) {
		return c.getX() >= 0 && c.getY() >= 0 && c.getX() + c.getWidth() <= rodic.getWidth() && c.getY() + c.getHeight() <= rodic.getHeight();
	}

	@Test
	public void vyskaNezavisiNaDatech() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final JPanel okno = new JPanel(new BorderLayout());
		okno.add(radek, BorderLayout.SOUTH);
		final int sirka = radek.getPreferredSize().width * 6 / 10;
		okno.setSize(sirka, 400);
		final Dimension predDaty = radek.getPreferredSize();

		for (final String pole : new String[] { "souradnice", "souradnicePozice", "celkovePoctyVsude", "filtrovanePocetyVsude", "celkovePoctyVyrez", "filtrovanePocetyVyrez" }) {
			((JTextComponent) pole(radek, pole)).setText("12345");
		}
		((JTextComponent) pole(radek, "souradnice")).setText("N50°04.800 E014°25.200");
		((JTextComponent) pole(radek, "celkovePoctyVsude")).setText("123456/104337");
		((JPanel) pole(radek, "jFilterProgressPanel")).add(new JProgressBar());
		final JLabel varovani = (JLabel) pole(radek, "varovaniPoctuPrekrocenych");
		varovani.setText("Limit 30000 waypointů");
		varovani.setVisible(true);
		okno.setSize(sirka, 400);
		Assert.assertEquals(predDaty.height, radek.getPreferredSize().height);
	}

	@Test
	public void varovaniSeVzdyVejdeCele() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final JPanel okno = new JPanel(new BorderLayout());
		okno.add(radek, BorderLayout.SOUTH);
		final JLabel varovani = (JLabel) pole(radek, "varovaniPoctuPrekrocenych");
		final java.lang.reflect.Method varuj = JStatusBar.class.getDeclaredMethod("setVarujPrekroceni", boolean.class, boolean.class, int.class);
		varuj.setAccessible(true);
		final int plna = radek.getPreferredSize().width;
		for (int sirka = plna / 2; sirka <= plna + 50; sirka += 7) {
			varuj.invoke(radek, false, false, 0);
			okno.setSize(sirka, 400);
			okno.doLayout();
			final int vyska = radek.getPreferredSize().height;
			varuj.invoke(radek, true, false, 30_000);
			okno.doLayout();
			radek.doLayout();
			Assert.assertEquals("šířka " + sirka, vyska, radek.getHeight());
			Assert.assertEquals("šířka " + sirka, varovani.getPreferredSize().width, varovani.getWidth());
			Assert.assertTrue("šířka " + sirka, varovani.getX() + varovani.getWidth() <= radek.getWidth());
		}
	}

	@Test
	public void pravyBlokStojiVzdyVpravoDole() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final JPanel okno = new JPanel(new BorderLayout());
		okno.add(radek, BorderLayout.SOUTH);
		final JPanel pravy = (JPanel) pole(radek, "pravyBlok");
		final int plna = radek.getPreferredSize().width;
		final int sirkaPraveho = pravy.getPreferredSize().width;
		for (int sirka = plna / 2; sirka <= plna + 50; sirka += 7) {
			okno.setSize(sirka, 400);
			okno.doLayout();
			radek.doLayout();
			Assert.assertEquals("šířka " + sirka, sirkaPraveho, pravy.getWidth());
			Assert.assertEquals("šířka " + sirka, radek.getWidth(), pravy.getX() + pravy.getWidth());
			Assert.assertEquals("šířka " + sirka, radek.getHeight(), pravy.getY() + pravy.getHeight());
			for (final Component panel : radek.getComponents()) {
				if (panel != pravy && panel.isVisible() && panel.getWidth() > 0) {
					Assert.assertFalse("šířka " + sirka + " " + panel, panel.getBounds().intersects(pravy.getBounds()));
				}
			}
		}
	}

	/** Na monitoru 1920 px (okno bez 16 px rámečku) se běžný stavový řádek vejde na jeden řádek i s rezervou 40 px na širší písmo. */
	@Test
	public void naFullHdJedenRadek() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		// Šířky písma se liší podle systému; cílem je běžné písmo Windows.
		Assume.assumeTrue("jen Windows", System.getProperty("os.name", "").startsWith("Windows"));
		final JStatusBar radek = new JStatusBar();
		((JPanel) pole(radek, "odPozice")).setVisible(true);
		final JPanel okno = new JPanel(new BorderLayout());
		okno.add(radek, BorderLayout.SOUTH);
		okno.setSize(1920 - 16, 400);
		okno.doLayout();
		radek.doLayout();
		int vyskaRadku = 0;
		for (final Component c : radek.getComponents()) {
			vyskaRadku = Math.max(vyskaRadku, c.getPreferredSize().height);
		}
		Assert.assertEquals("jeden řádek", vyskaRadku, radek.getHeight());
		final int sirka = radek.getPreferredSize().width;
		Assert.assertTrue("rezerva na širší písmo: " + sirka, sirka <= 1920 - 16 - 40);
	}

	private static Object pole(final JStatusBar radek, final String jmeno) throws Exception {
		final Field f = JStatusBar.class.getDeclaredField(jmeno);
		f.setAccessible(true);
		return f.get(radek);
	}
}
