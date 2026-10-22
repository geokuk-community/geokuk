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

	/** Po načtení keší ukáže přehled čas nejmladšího načteného zdroje. */
	@Test
	public void poNacteniUkazeCasDat() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final long cas = new java.util.GregorianCalendar(2026, java.util.Calendar.OCTOBER, 21, 14, 30).getTimeInMillis();
		final cz.geokuk.plugins.kesoid.importek.InformaceOZdrojich informace = org.mockito.Mockito.mock(cz.geokuk.plugins.kesoid.importek.InformaceOZdrojich.class);
		org.mockito.Mockito.when(informace.getYungest()).thenReturn(cas);
		final cz.geokuk.plugins.kesoid.KesBag bag = new cz.geokuk.plugins.kesoid.KesBag(new cz.geokuk.plugins.kesoid.genetika.Genom());
		bag.setInformaceOZdrojich(informace);
		SwingUtilities.invokeAndWait(() -> {
			final JInformaceOZdrojichDialog dialog = new JInformaceOZdrojichDialog();
			try {
				dialog.initAfterEventReceiverRegistration();
				dialog.onEvent(new KeskyNactenyEvent(bag));
				final java.lang.reflect.Field f = JTabulkaZdroju.class.getDeclaredField("casDat");
				f.setAccessible(true);
				final javax.swing.JLabel popisek = (javax.swing.JLabel) f.get(dialog.getTabulka());
				Assert.assertEquals("Nejnovější data: 2026-10-21 14:30", popisek.getText());
				Assert.assertTrue(popisek.isVisible());
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
