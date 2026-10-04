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

		for (final String pole : new String[] { "souradnice", "souradnicePozice", "celkovePoctyVsude", "filtrovanePocetyVsude", "celkovePoctyVyrez", "filtrovanePocetyVyrez", "jZdrojeKesoiduCas" }) {
			((JTextComponent) pole(radek, pole)).setText("12345");
		}
		((JTextComponent) pole(radek, "souradnice")).setText("N50°04.800 E014°25.200");
		((JTextComponent) pole(radek, "celkovePoctyVsude")).setText("123456/104337");
		((JComponent) pole(radek, "jZdrojeKesoiduPocetNenactenych")).setVisible(false);
		((JPanel) pole(radek, "jFilterProgressPanel")).add(new JProgressBar());
		final JLabel varovani = (JLabel) pole(radek, "varovaniPoctuPrekrocenych");
		varovani.setText("Překročen limit 30000 waypointů");
		varovani.setVisible(true);
		okno.setSize(sirka, 400);
		Assert.assertEquals(predDaty.height, radek.getPreferredSize().height);
	}

	private static Object pole(final JStatusBar radek, final String jmeno) throws Exception {
		final Field f = JStatusBar.class.getDeclaredField(jmeno);
		f.setAccessible(true);
		return f.get(radek);
	}
}
