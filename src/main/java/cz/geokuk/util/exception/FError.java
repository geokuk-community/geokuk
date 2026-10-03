/**
 *
 */
package cz.geokuk.util.exception;

import java.awt.GraphicsEnvironment;
import java.util.function.BiConsumer;

import javax.swing.SwingUtilities;

/**
 * @author Martin Veverka
 *
 */
public class FError {

	private static JErrorDialog jErrorDialog;

	public static void report(final String text) {
		report(text, null);
	}

	/** Kam jdou hlášení do Přehledu problémů. */
	static BiConsumer<String, AExcId> prijemce = FError::zobraz;

	public static void report(final String text, final AExcId excid) {
		prijemce.accept(text, excid);
	}

	private static void zobraz(final String text, final AExcId excid) {
		if (GraphicsEnvironment.isHeadless()) {
			return; // bez displeje zůstane jen výpis výjimky, který už je uložený
		}
		SwingUtilities.invokeLater(() -> {
			if (jErrorDialog == null) {
				jErrorDialog = new JErrorDialog();
			}
			jErrorDialog.setVisible(true);
			jErrorDialog.addProblem(text, excid);
		});

	}

}
