package cz.geokuk.plugins.geocoding;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;

import javax.swing.*;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;

public class JAdrDialogTest {

	/** V režimu offline hledání hned řekne, že potřebuje připojení, a nezůstane viset na „Hledá se“. */
	@Test
	public void offlineHledaniOhlasi() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final String[] stav = new String[1];
		SwingUtilities.invokeAndWait(() -> {
			final JAdrDialog dialog = new JAdrDialog();
			try {
				dialog.inject(new GeocodingModel());
				dialog.setReferencniBod(new Wgs(50, 14));
				pole(dialog, "entry", JTextField.class).setText("Praha");
				dialog.search();
				stav[0] = pole(dialog, "status", JLabel.class).getText();
			} finally {
				dialog.dispose();
			}
		});
		Assert.assertTrue(stav[0], stav[0].contains("Online"));
	}

	private static <T> T pole(final JAdrDialog dialog, final String jmeno, final Class<T> typ) {
		try {
			final Field f = JAdrDialog.class.getDeclaredField(jmeno);
			f.setAccessible(true);
			return typ.cast(f.get(dialog));
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
