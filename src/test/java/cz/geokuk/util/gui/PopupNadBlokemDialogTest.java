package cz.geokuk.util.gui;

import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/** Popup nad blokem se nezavře za dialogu otevřeného z popupu ani hned po něm, jen až myš popup navštíví a opustí. */
public class PopupNadBlokemDialogTest {

	@Test(timeout = 30000)
	public void popupZustaneZaDialoguAPoNem() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		final JFrame okno = new JFrame();
		final JPanel blok = new JPanel();
		final JPanel obsah = new JPanel();
		obsah.setPreferredSize(new java.awt.Dimension(200, 100));
		final PopupNadBlokem popup = new PopupNadBlokem(blok, () -> {});
		try {
			SwingUtilities.invokeAndWait(() -> {
				okno.setBounds(0, 0, 800, 600);
				okno.getContentPane().add(blok, java.awt.BorderLayout.SOUTH);
				okno.setVisible(true);
			});
			SwingUtilities.invokeAndWait(() -> {
				popup.ukaz(obsah, null, false);
				final Rectangle r = new Rectangle(obsah.getLocationOnScreen(), obsah.getSize());
				final Point uvnitr = new Point(r.x + 10, r.y + 10);
				final Point mimo = new Point(r.x + r.width + 500, r.y - 300);
				popup.sDialogem(() -> {
					popup.tik(mimo, 0);
					popup.tik(mimo, 5000);
					return null;
				});
				Assert.assertNotNull("za dialogu zůstal", popup.viditelny());
				popup.tik(mimo, 6000);
				popup.tik(mimo, 9000);
				Assert.assertNotNull("po dialogu zůstal, dokud se myš nevrátí", popup.viditelny());
				popup.tik(uvnitr, 10000);
				popup.tik(mimo, 11000);
				popup.tik(mimo, 11000 + PopupNadBlokem.PRODLEVA_ZAVRENI_MS);
				Assert.assertNull("po návratu a odjetí myši se zavřel", popup.viditelny());
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				popup.zastav();
				okno.dispose();
			});
		}
	}
}
