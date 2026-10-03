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
			throw new RuntimeException("Výjimka při zpracování na pozadí: " + pricina, e);
		}
	}

	protected void donex() throws Exception {}
}
