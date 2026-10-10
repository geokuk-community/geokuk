package cz.geokuk.core.program;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.framework.MyPreferences;
import cz.geokuk.start.Start;

/** Volby zobrazení se ukládají pod klíči, které čte spouštěč. */
public class ZobrazeniProgramuActionTest {

	private final MyPreferences pref = MyPreferences.current().node("test-zobrazeni-programu");

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	@Test
	public void vychoziJeObojeZapnuto() {
		Assert.assertFalse("beze změny", ZobrazeniProgramuAction.uloz(pref, true, true));
		Assert.assertTrue(pref.getBoolean(Start.ZVETSENI_KLIC, false));
		Assert.assertTrue(pref.getBoolean(Start.DIRECT3D_KLIC, false));
	}

	@Test
	public void vypnutiSeUloziAHlasiZmenu() {
		Assert.assertTrue(ZobrazeniProgramuAction.uloz(pref, false, true));
		Assert.assertFalse(pref.getBoolean(Start.ZVETSENI_KLIC, true));
		Assert.assertTrue(ZobrazeniProgramuAction.uloz(pref, false, false));
		Assert.assertFalse(pref.getBoolean(Start.DIRECT3D_KLIC, true));
		Assert.assertFalse(ZobrazeniProgramuAction.uloz(pref, false, false));
	}

	@Test
	public void popisUkazujeMeritko() {
		Assert.assertEquals("Zvětšovat podle Windows (125 %)", ZobrazeniProgramuAction.popisZvetseni(1.25));
		Assert.assertEquals("Zvětšovat podle Windows", ZobrazeniProgramuAction.popisZvetseni(1.0));
	}

	@Test
	public void akceJeVzdyDostupna() {
		Assert.assertTrue(new ZobrazeniProgramuAction().isEnabled());
	}

	@Test
	public void dialogMaNapovedu() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeLater(() -> new ZobrazeniProgramuAction().actionPerformed(null));
		Dialog dialog = null;
		for (int i = 0; i < 200 && dialog == null; i++) {
			Thread.sleep(25);
			for (final Window w : Window.getWindows()) {
				if (w instanceof Dialog && w.isShowing() && "Zobrazení programu".equals(((Dialog) w).getTitle())) {
					dialog = (Dialog) w;
				}
			}
		}
		Assert.assertNotNull(dialog);
		final Dialog d = dialog;
		final List<String> texty = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			sesbirej(d, texty);
			d.dispose();
		});
		Assert.assertTrue(texty.toString(), texty.contains("Nápověda"));
		Assert.assertEquals(texty.toString(), PametProgramuAction.lzeNastavit() && ZobrazeniProgramuAction.jeWindows(), texty.contains("Okno programu (platí po restartu)"));
	}

	private static void sesbirej(final Container kde, final List<String> texty) {
		for (final Component c : kde.getComponents()) {
			if (c instanceof AbstractButton) {
				texty.add(((AbstractButton) c).getText());
			}
			if (c instanceof JComponent && ((JComponent) c).getBorder() instanceof CompoundBorder
					&& ((CompoundBorder) ((JComponent) c).getBorder()).getOutsideBorder() instanceof TitledBorder) {
				texty.add(((TitledBorder) ((CompoundBorder) ((JComponent) c).getBorder()).getOutsideBorder()).getTitle());
			}
			if (c instanceof Container) {
				sesbirej((Container) c, texty);
			}
		}
	}
}
