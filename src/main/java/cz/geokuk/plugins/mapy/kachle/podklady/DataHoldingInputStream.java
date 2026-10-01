package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.*;
import java.net.SocketTimeoutException;

/**
 * Stream drží veškerá data, která se načetla a dokáže je pak vydat jako bytové pole.
 *
 * @author Martin Veverka
 *
 */
class DataHoldingInputStream extends FilterInputStream {

	private final ByteArrayOutputStream baos = new ByteArrayOutputStream(256 * 256);

	/** Časový limit čtení hlídá jen jedno čtení; server, který posílá po bajtech, by bez celkového limitu blokoval stahování hodiny. */
	private final long konecNanos;

	private final long limitMs;

	protected DataHoldingInputStream(final InputStream in, final long limitMs) {
		super(in);
		this.limitMs = limitMs;
		konecNanos = System.nanoTime() + limitMs * 1_000_000;
	}

	public byte[] getData() {
		return baos.toByteArray();
	}

	@Override
	public int read() throws IOException {
		hlidejCas();
		final int c = super.read();
		if (c >= 0) { // konec streamu není data
			baos.write(c);
		}
		return c;
	}

	@Override
	public int read(final byte[] b, final int off, final int len) throws IOException {
		hlidejCas();
		final int delka = super.read(b, off, len);
		if (delka > 0) {
			baos.write(b, off, delka);
		}
		return delka;
	}

	private void hlidejCas() throws IOException {
		if (System.nanoTime() - konecNanos > 0) {
			throw new SocketTimeoutException("Stahování dlaždice trvá déle než " + limitMs / 1000 + " s.");
		}
	}

}
