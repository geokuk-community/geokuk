package cz.geokuk.util.exception;

import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.Arrays;

import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class FErrorPrehledTest {

	/** Přehled problémů jde po zavření znovu otevřít (Nápověda > Přehled problémů). */
	@Test
	public void prehledJdeOtevritIPoZavreni() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		try {
			SwingUtilities.invokeAndWait(FError::zobrazPrehled);
			final JErrorDialog dialog = prehled();
			Assert.assertTrue(dialog.isVisible());
			SwingUtilities.invokeAndWait(() -> dialog.setVisible(false));
			SwingUtilities.invokeAndWait(FError::zobrazPrehled);
			Assert.assertSame(dialog, prehled());
			Assert.assertTrue(dialog.isVisible());
		} finally {
			SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).filter(w -> w instanceof JErrorDialog).forEach(Window::dispose));
		}
	}

	private static JErrorDialog prehled() {
		return (JErrorDialog) Arrays.stream(Window.getWindows()).filter(w -> w instanceof JErrorDialog && w.isDisplayable()).findFirst().orElse(null);
	}
}
