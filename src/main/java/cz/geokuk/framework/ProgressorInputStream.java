package cz.geokuk.framework;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Vstupní proud, který hlásí počet přečtených bajtů do pruhu průběhu; zavřením se pruh ukončí. */
public class ProgressorInputStream extends FilterInputStream {

	private final Progressor progressor;
	/** Velikost dat zjištěná na začátku, 0 když není známa. */
	private final long celkem;
	private long precteno;

	public ProgressorInputStream(final ProgressModel progressModel, final String message, final InputStream in) {
		this(progressModel, message, "", in);
	}

	public ProgressorInputStream(final ProgressModel progressModel, final String message, final String tooltip, final InputStream in) {
		super(in);
		long velikost;
		try {
			velikost = in.available();
		} catch (final IOException e) {
			velikost = 0;
		}
		celkem = velikost;
		progressor = progressModel.start((int) celkem, message, tooltip);
	}

	@Override
	public int read() throws IOException {
		final int bajt = in.read();
		if (bajt >= 0) {
			pricti(1);
		}
		return bajt;
	}

	@Override
	public int read(final byte[] b, final int off, final int len) throws IOException {
		final int pocet = in.read(b, off, len);
		if (pocet > 0) {
			pricti(pocet);
		}
		return pocet;
	}

	@Override
	public long skip(final long n) throws IOException {
		final long pocet = in.skip(n);
		if (pocet > 0) {
			pricti(pocet);
		}
		return pocet;
	}

	@Override
	public synchronized void reset() throws IOException {
		in.reset();
		precteno = Math.max(0, celkem - in.available());
		progressor.setProgress((int) Math.min(precteno, Integer.MAX_VALUE));
	}

	@Override
	public void close() throws IOException {
		try {
			in.close();
		} finally {
			progressor.finish();
		}
	}

	private void pricti(final long pocet) {
		precteno += pocet;
		progressor.setProgress((int) Math.min(precteno, Integer.MAX_VALUE));
	}
}
