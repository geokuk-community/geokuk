package cz.geokuk.core.lookandfeel;

import java.awt.GraphicsEnvironment;

import javax.swing.*;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

import org.junit.*;

/** Změna vzhledu se projeví ve všech otevřených oknech. */
public class LafSupportTest {

	private LookAndFeel puvodni;

	@Before
	public void setUp() {
		Assume.assumeFalse("potřebuje displej", GraphicsEnvironment.isHeadless());
		puvodni = UIManager.getLookAndFeel();
	}

	@After
	public void uklid() throws Exception {
		if (puvodni != null) {
			SwingUtilities.invokeAndWait(() -> {
				try {
					UIManager.setLookAndFeel(puvodni);
				} catch (final UnsupportedLookAndFeelException e) {
					throw new IllegalStateException(e);
				}
			});
		}
	}

	@Test
	public void otevrenyDialogDostaneNovyVzhledZrusenyNe() throws Exception {
		// Statická inicializace LafSupport nastavuje vzhled z nastavení, musí proběhnout před testem.
		Class.forName(LafSupport.class.getName());
		SwingUtilities.invokeAndWait(() -> {
			try {
				UIManager.setLookAndFeel(new MetalLookAndFeel());
				final JFrame hlavni = new JFrame();
				final JButton vHlavnim = new JButton("a");
				hlavni.add(vHlavnim);
				hlavni.pack();
				final JDialog dialog = new JDialog(hlavni, "nemodální");
				final JButton vDialogu = new JButton("b");
				dialog.add(vDialogu);
				dialog.pack();
				final JDialog zruseny = new JDialog(hlavni, "zavřený");
				final JButton vZrusenem = new JButton("c");
				zruseny.add(vZrusenem);
				zruseny.pack();
				zruseny.dispose();
				final String metal = vZrusenem.getUI().getClass().getName();

				UIManager.setLookAndFeel(new NimbusLookAndFeel());
				LafSupport.updateThisSwingSet();

				final String nimbus = UIManager.getUI(new JButton()).getClass().getName();
				Assert.assertNotEquals(metal, nimbus);
				Assert.assertEquals(nimbus, vHlavnim.getUI().getClass().getName());
				Assert.assertEquals("otevřený dialog", nimbus, vDialogu.getUI().getClass().getName());
				Assert.assertEquals("zrušené okno se nepřepíná", metal, vZrusenem.getUI().getClass().getName());
				hlavni.dispose();
			} catch (final UnsupportedLookAndFeelException e) {
				throw new IllegalStateException(e);
			}
		});
	}
}
