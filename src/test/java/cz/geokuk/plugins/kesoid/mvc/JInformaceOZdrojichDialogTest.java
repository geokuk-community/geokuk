package cz.geokuk.plugins.kesoid.mvc;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Constructor;

import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.importek.StavZdroju;
import cz.geokuk.plugins.kesoid.importek.TypZdroje;

/** Okno Přehled zdrojů je táž tabulka jako ve stavovém řádku a sleduje stav zdrojů. */
public class JInformaceOZdrojichDialogTest {

	@Test
	public void ukazujeAktualniStavATabulkaPrepina() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final StavyZdrojuProTesty data = StavyZdrojuProTesty.vzorek();
		final StavyZdrojuProTesty.Zaznam ovladani = new StavyZdrojuProTesty.Zaznam();
		SwingUtilities.invokeAndWait(() -> {
			final JInformaceOZdrojichDialog dialog = new JInformaceOZdrojichDialog();
			try {
				dialog.initAfterEventReceiverRegistration();
				dialog.getTabulka().setOvladani(ovladani);
				Assert.assertEquals("bez stavu jen typy", TypZdroje.values().length, dialog.getTabulka().getRadky().size());
				dialog.onEvent(udalost(data.snimek()));
				Assert.assertEquals(TypZdroje.values().length + 5, dialog.getTabulka().getRadky().size());
				JTabulkaZdroju.klikTyp(data.snimek(), TypZdroje.GSAK, ovladani);
				Assert.assertEquals("typ GSAK false", ovladani.volani.get(0));
			} catch (final Exception e) {
				throw new RuntimeException(e);
			} finally {
				dialog.dispose();
			}
		});
	}

	private static StavZdrojuEvent udalost(final StavZdroju stav) throws Exception {
		final Constructor<StavZdrojuEvent> k = StavZdrojuEvent.class.getDeclaredConstructor(StavZdroju.class);
		k.setAccessible(true);
		return k.newInstance(stav);
	}
}
