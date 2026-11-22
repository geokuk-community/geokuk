package cz.geokuk.plugins.kesoid.detail;

import java.lang.reflect.InvocationTargetException;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.Wpt;

/** Řádek doplňku v detailu kešoidu: ukáže se s textem doplňku k zobrazenému waypointu a skryje se bez doplňku nebo bez textu. */
public class JKesoidDetailContainerDoplnekTest {

	@Test
	public void radekDoplnkuPodleDoplnkuAWaypointu() throws Exception {
		naEdt(() -> {
			final JKesoidDetailContainer detail = new JKesoidDetailContainer();
			final JLabel radek = detail.radekDoplnku();
			final Wpt sektor = new Wpt();
			final Wpt jiny = new Wpt();
			detail.ukazDoplnek(sektor);
			Assert.assertFalse("bez doplňku řádek není", radek.isVisible());

			detail.onEvent(new DoplnekDetailuEvent(w -> w == sektor ? "Sektor 100°" : null));
			Assert.assertTrue("registrace doplňku obnoví zobrazený detail", radek.isVisible());
			Assert.assertEquals("Sektor 100°", radek.getText());

			detail.ukazDoplnek(jiny);
			Assert.assertFalse("doplněk k waypointu nic nemá", radek.isVisible());
			detail.ukazDoplnek(sektor);
			Assert.assertTrue(radek.isVisible());

			detail.onEvent(new DoplnekDetailuEvent(null));
			Assert.assertFalse("po odregistraci se řádek skryje", radek.isVisible());
		});
	}

	private static void naEdt(final Runnable r) throws Exception {
		try {
			SwingUtilities.invokeAndWait(r);
		} catch (final InvocationTargetException e) {
			if (e.getCause() instanceof Error) {
				throw (Error) e.getCause();
			}
			throw new AssertionError(e.getCause());
		}
	}
}
