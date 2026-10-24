package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/** Stav souboru cache dlaždic přečtený z hlavičky SQLite bez otevření databáze. */
public final class StavSouboruCache {

	private static final byte[] HLAVICKA = "SQLite format 3\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

	final long velikost;
	final int velikostStranky;
	final long stranek;
	final long volnych;

	public StavSouboruCache(final long velikost, final int velikostStranky, final long stranek, final long volnych) {
		this.velikost = velikost;
		this.velikostStranky = velikostStranky;
		this.stranek = stranek;
		this.volnych = volnych;
	}

	/** Null, když soubor není, nejde přečíst nebo nemá hlavičku SQLite. */
	static StavSouboruCache precti(final File soubor) {
		final byte[] h = new byte[100];
		final long delka;
		try (RandomAccessFile raf = new RandomAccessFile(soubor, "r")) {
			delka = raf.length();
			if (delka < h.length) {
				return null;
			}
			raf.readFully(h);
		} catch (final IOException e) {
			return null;
		}
		for (int i = 0; i < HLAVICKA.length; i++) {
			if (h[i] != HLAVICKA[i]) {
				return null;
			}
		}
		final ByteBuffer b = ByteBuffer.wrap(h);
		int stranka = b.getShort(16) & 0xffff;
		if (stranka == 1) {
			stranka = 65536;
		}
		if (stranka < 512 || Integer.bitCount(stranka) != 1) {
			return null;
		}
		final long zDelky = delka / stranka;
		long stranek = b.getInt(28) & 0xffffffffL;
		// Počet stránek v hlavičce platí jen tehdy, když ho naposledy zapsala nejnovější verze SQLite.
		if (stranek == 0 || b.getInt(24) != b.getInt(92)) {
			stranek = zDelky;
		}
		final long volnych = Math.min(b.getInt(36) & 0xffffffffL, stranek);
		return new StavSouboruCache(delka, stranka, stranek, volnych);
	}

	/** Podíl volných stránek 0 až 1. */
	public double podilVolnych() {
		return stranek == 0 ? 0 : (double) volnych / stranek;
	}

	/** Odhad bajtů, které zůstanou po zhutnění. */
	public long zivaData() {
		return (stranek - volnych) * velikostStranky;
	}

	public int velikostStranky() {
		return velikostStranky;
	}

	/** Velikost souboru v bajtech. */
	public long velikost() {
		return velikost;
	}

	@Override
	public String toString() {
		return String.format("%d MB, stránka %d B, volných %.0f %%", velikost >> 20, velikostStranky, podilVolnych() * 100);
	}
}
