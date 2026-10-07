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
		// Okna a čekající překreslení z jiných testů by přepínání vzhledu zasáhlo také.
		SwingUtilities.invokeAndWait(() -> {
			for (final java.awt.Window okno : java.awt.Window.getWindows()) {
				okno.dispose();
			}
			// Statická inicializace LafSupport nastavuje vzhled z nastavení, musí proběhnout před testem a v EDT.
			try {
				Class.forName(LafSupport.class.getName());
			} catch (final ClassNotFoundException e) {
				throw new IllegalStateException(e);
			}
		});
		SwingUtilities.invokeAndWait(() -> {});
		final javax.swing.LookAndFeel puvodni = UIManager.getLookAndFeel();
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
				UIManager.setLookAndFeel(puvodni);
			} catch (final UnsupportedLookAndFeelException e) {
				throw new IllegalStateException(e);
			}
		});
		SwingUtilities.invokeAndWait(() -> {});
	}

	@Test
	public void poZmeneVzhleduMaKruhFokusuProPosluchaceAqua() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				Class.forName(LafSupport.class.getName());
				UIManager.setLookAndFeel(new MetalLookAndFeel());
				Assert.assertNotNull("Metal", UIManager.getColor(LafSupport.KRUH_FOKUSU_AQUA));
				UIManager.setLookAndFeel(new NimbusLookAndFeel());
				Assert.assertNotNull("Nimbus", UIManager.getColor(LafSupport.KRUH_FOKUSU_AQUA));
			} catch (final ClassNotFoundException | UnsupportedLookAndFeelException e) {
				throw new IllegalStateException(e);
			}
		});
	}
}
