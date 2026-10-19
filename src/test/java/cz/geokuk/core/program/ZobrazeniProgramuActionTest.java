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
	public void znackyZvetseni() {
		Assert.assertEquals("1", ZobrazeniProgramuAction.znackaZvetseni(100));
		Assert.assertEquals("1,5", ZobrazeniProgramuAction.znackaZvetseni(150));
		Assert.assertEquals("1,25", ZobrazeniProgramuAction.znackaZvetseni(125));
		Assert.assertEquals("2,5×", ZobrazeniProgramuAction.popisZvetseniIkon(250));
	}

	@Test
	public void posuvnikMapyOd80Do200Po5() {
		final JSlider s = ZobrazeniProgramuAction.posuvnikMapy();
		Assert.assertEquals(80, s.getMinimum());
		Assert.assertEquals(200, s.getMaximum());
		Assert.assertEquals(5, s.getMinorTickSpacing());
		Assert.assertTrue(s.getSnapToTicks());
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

	/** Dialog zabalený na 80 %: po posunu na 200 % má posuvník i popisek plnou šířku a popisek se nezalomí. */
	@Test
	public void posunNa200NicNeorizne() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JSlider pismo = ZobrazeniProgramuAction.posuvnikMapy();
			pismo.setValue(80);
			final JPanel radek = ZobrazeniProgramuAction.sProcenty(pismo);
			final JPanel ramecek = zabal(new JLabel("Mapa (offline):"), radek);
			pismo.setValue(200);
			rozvrhni(ramecek);
			plnaSirka(radek, pismo);
		});
	}

	@Test
	public void posunIkonNa45NicNeorizne() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final cz.geokuk.util.gui.JPosuvnikPredvoleb ikony = new cz.geokuk.util.gui.JPosuvnikPredvoleb(ZobrazeniProgramuAction.PREDVOLBY_IKON, 100,
					ZobrazeniProgramuAction::znackaZvetseni, ZobrazeniProgramuAction::popisZvetseniIkon);
			final JPanel ramecek = zabal(new JLabel("Ikony keší:"), ikony);
			ikony.setHodnota(450);
			rozvrhni(ramecek);
			plnaSirka(ikony, ikony.getSlider());
		});
	}

	/** Jako dialog: řádek popisek – prvek v GridBagLayout, velikost podle preferované při otevření. */
	private static JPanel zabal(final JComponent popisek, final JComponent prvek) {
		final JPanel p = new JPanel(new GridBagLayout());
		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		p.add(popisek, c);
		c.gridx = 1;
		p.add(prvek, c);
		p.setSize(p.getPreferredSize());
		rozvrhni(p);
		return p;
	}

	private static void rozvrhni(final Container c) {
		c.doLayout();
		for (final Component k : c.getComponents()) {
			if (k instanceof Container) {
				rozvrhni((Container) k);
			}
		}
	}

	private static void plnaSirka(final JPanel radek, final JSlider posuvnik) {
		Assert.assertEquals("posuvník", posuvnik.getPreferredSize().width, posuvnik.getWidth());
		for (final Component k : radek.getComponents()) {
			if (k instanceof JLabel) {
				Assert.assertTrue("popisek celý: " + ((JLabel) k).getText(), k.getWidth() >= ((JLabel) k).getUI().getPreferredSize((JLabel) k).width
						&& k.getHeight() >= ((JLabel) k).getUI().getPreferredSize((JLabel) k).height);
				Assert.assertEquals("popisek vedle posuvníku", posuvnik.getY(), k.getY(), posuvnik.getHeight());
				Assert.assertTrue("popisek v řádku", k.getX() + k.getWidth() <= radek.getWidth() && k.getY() + k.getHeight() <= radek.getHeight());
			}
		}
	}
}
