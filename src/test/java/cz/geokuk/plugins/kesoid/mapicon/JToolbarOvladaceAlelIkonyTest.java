package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.*;

import javax.swing.*;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.framework.Factory;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.genetika.Genotyp;
import cz.geokuk.plugins.kesoid.mvc.*;
import cz.geokuk.util.gui.JIconCheckBox;

/** Ikony alel na toolbaru se po načtení kešek skládají mimo EDT, ovladače se vytvoří hned. */
public class JToolbarOvladaceAlelIkonyTest {

	private static final Icon IKONA = new ImageIcon(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB));

	private final List<Boolean> naEdt = Collections.synchronizedList(new ArrayList<>());

	private final IkonBag bag = new IkonBag() {
		@Override
		public Icon seekIkon(final Genotyp genotyp) {
			naEdt.add(SwingUtilities.isEventDispatchThread());
			return IKONA;
		}
	};

	private final KesBag kesky = prazdne(bag);

	private static KesBag prazdne(final IkonBag bag) {
		final KesBag k = new KesBag(bag.getGenom());
		k.done();
		return k;
	}

	private final Factory factory = new Factory() {
		@Override
		public <T> T init(final T obj) {
			if (obj instanceof SwitchKesoidUrciteAlelyAction) {
				final SwitchKesoidUrciteAlelyAction a = (SwitchKesoidUrciteAlelyAction) obj;
				a.onEvent(new IkonyNactenyEvent(bag, ASada.STANDARD));
				a.onEvent(new KeskyNactenyEvent(kesky));
				a.initAfterEventReceiverRegistration();
			}
			return obj;
		}

		@Override
		public <T> T initNow(final T obj) {
			return init(obj);
		}
	};

	@Test
	public void ikonySeSkladajiMimoEdt() throws Exception {
		final JToolbarOvladaceAlel panel = new JToolbarOvladaceAlel(null);
		panel.inject(factory);
		SwingUtilities.invokeAndWait(() -> panel.onEvent(new KeskyNactenyEvent(kesky)));
		final int pocet = SwingUtilitiesOvladace.pocet(panel);
		Assert.assertTrue(pocet > 0);

		final long konec = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < konec && SwingUtilitiesOvladace.sIkonou(panel) < pocet) {
			Thread.sleep(20);
		}
		Assert.assertEquals("všechny ovladače dostaly ikonu", pocet, SwingUtilitiesOvladace.sIkonou(panel));
		Assert.assertFalse(naEdt.isEmpty());
		Assert.assertFalse("ikona se skládala na EDT", naEdt.contains(Boolean.TRUE));
	}

	private static final class SwingUtilitiesOvladace {
		static int pocet(final JComponent panel) throws Exception {
			final int[] n = new int[1];
			SwingUtilities.invokeAndWait(() -> {
				for (final Component c : panel.getComponents()) {
					if (c instanceof JIconCheckBox) {
						n[0]++;
					}
				}
			});
			return n[0];
		}

		static int sIkonou(final JComponent panel) throws Exception {
			final int[] n = new int[1];
			SwingUtilities.invokeAndWait(() -> {
				for (final Component c : panel.getComponents()) {
					if (c instanceof JIconCheckBox && ((JIconCheckBox) c).getIcon() != null && ((JIconCheckBox) c).getIcon().getIconWidth() >= IKONA.getIconWidth()) {
						n[0]++;
					}
				}
			});
			return n[0];
		}
	}
}
