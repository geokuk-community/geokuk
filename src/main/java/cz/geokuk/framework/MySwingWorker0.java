/**
 *
 */
package cz.geokuk.framework;

import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

/**
 * @author Martin Veverka
 *
 */
public abstract class MySwingWorker0<T, V> extends SwingWorker<T, V> {

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.swing.SwingWorker#done()
	 */
	@Override
	protected final void done() {
		try {
			donex();
		} catch (final Exception e) {
			final Throwable pricina = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
			throw new RuntimeException(popis(pricina), e);
		}
	}

	/** Text pro Přehled problémů: zpráva výjimky bez jména třídy Javy, jen když zprávu nemá, aspoň její druh. */
	static String popis(Throwable pricina) {
		// Výjimka vytvořená jen z příčiny má za zprávu příčinu i se jménem třídy, vlastní text má až příčina.
		for (int i = 0; i < 10 && pricina.getCause() != null && pricina.getCause() != pricina; i++) {
			final String vlastni = pricina.getMessage();
			if (vlastni != null && !vlastni.trim().isEmpty() && !vlastni.equals(pricina.getCause().toString())) {
				break;
			}
			pricina = pricina.getCause();
		}
		final String zprava = pricina.getLocalizedMessage();
		return zprava != null && !zprava.trim().isEmpty() ? "Chyba při práci na pozadí: " + zprava
				: "Chyba při práci na pozadí (" + pricina.getClass().getSimpleName() + ")";
	}

	protected void donex() throws Exception {}
}
