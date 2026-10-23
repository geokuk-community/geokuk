package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/** Pata panelu: „Přes mapu:“ se stejnými přepínači jako menu Mapy; přepnutí nic nestahuje. */
public class JMapyPopupPresMapuTest {

	private static final class Ovladani implements OvladaniMap {
		boolean trasy;
		boolean stinovani = true;
		final List<String> volani = new ArrayList<>();

		@Override
		public void otevriSlozku() {}

		@Override
		public boolean isPrekryvTrasDostupny() {
			return true;
		}

		@Override
		public boolean isPrekryvTras() {
			return trasy;
		}

		@Override
		public void setPrekryvTras(final boolean zapnuto) {
			trasy = zapnuto;
			volani.add("trasy " + zapnuto);
		}

		@Override
		public boolean isStinovani() {
			return stinovani;
		}

		@Override
		public void setStinovani(final boolean zapnuto) {
			stinovani = zapnuto;
			volani.add("stinovani " + zapnuto);
		}
	}

	private static <T extends Component> List<T> najdi(final Container c, final Class<T> typ) {
		final List<T> v = new ArrayList<>();
		for (final Component k : c.getComponents()) {
			if (typ.isInstance(k)) {
				v.add(typ.cast(k));
			}
			if (k instanceof Container) {
				v.addAll(najdi((Container) k, typ));
			}
		}
		return v;
	}

	@Test
	public void pataMaPrepinacePresMapu() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Ovladani o = new Ovladani();
			p.setOvladani(o);
			p.setStinovaniDostupne(true);
			Assert.assertTrue("Přes mapu:", najdi(p, JLabel.class).stream().anyMatch(l -> "Přes mapu:".equals(l.getText())));
			final JCheckBox trasy = najdi(p, JCheckBox.class).stream().filter(c -> "Turistické a cyklotrasy".equals(c.getText())).findFirst().get();
			final JCheckBox stinovani = najdi(p, JCheckBox.class).stream().filter(c -> "Stínování terénu".equals(c.getText())).findFirst().get();
			Assert.assertFalse(trasy.isSelected());
			Assert.assertTrue(stinovani.isSelected());
			trasy.doClick();
			stinovani.doClick();
			Assert.assertEquals(java.util.Arrays.asList("trasy true", "stinovani false"), o.volani);
			Assert.assertTrue(najdi(p, JCheckBox.class).stream().noneMatch(c -> "Stínovat terén".equals(c.getText())));
		});
	}
}
