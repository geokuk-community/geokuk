package cz.geokuk.core.program;

import java.awt.*;
import java.lang.reflect.Field;

import javax.swing.JPanel;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/** Ve stavovém řádku užším než všechna pole se žádné pole neořízne ani neskryje. */
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
}
