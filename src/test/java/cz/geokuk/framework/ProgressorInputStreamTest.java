package cz.geokuk.framework;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class ProgressorInputStreamTest {

	/** Pamatuje si poslední průběh a ukončení pruhu. */
	private static final class Zaznam extends ProgressModel {
		int max = -1;
		int posledni = -1;
		boolean ukoncen;

		@Override
		public Progressor start(final int aMax, final String text) {
			max = aMax;
			return new Progressor() {
				@Override
				public void setProgress(final int p) {
					posledni = p;
				}

				@Override
				public void addProgress(final int p) {}

				@Override
				public void incProgress() {}

				@Override
				public int getProgress() {
					return posledni;
				}

				@Override
				public void finish() {
					ukoncen = true;
				}

				@Override
				public void setMax(final int m) {}

				@Override
				public void setText(final String t) {}

				@Override
				public void setTooltip(final String t) {}
			};
		}
	}

	@Test
	public void hlasiPrectenoAZavrenimUkonci() throws Exception {
		final Zaznam z = new Zaznam();
		try (InputStream in = new ProgressorInputStream(z, "test", new ByteArrayInputStream(new byte[100]))) {
			assertThat(z.max).isEqualTo(100);
			in.read();
			in.read(new byte[9]);
			assertThat(z.posledni).isEqualTo(10);
			in.skip(40);
			assertThat(z.posledni).isEqualTo(50);
			in.read(new byte[100], 0, 100);
			assertThat(z.posledni).isEqualTo(100);
		}
		assertThat(z.ukoncen).isTrue();
	}

	@Test
	public void resetVratiPrubeh() throws Exception {
		final Zaznam z = new Zaznam();
		try (InputStream in = new ProgressorInputStream(z, "test", new ByteArrayInputStream(new byte[100]))) {
			in.read(new byte[20]);
			in.mark(100);
			in.read(new byte[30]);
			in.reset();
			assertThat(z.posledni).isEqualTo(20);
		}
	}

	/** Soubor přes 2 GB nesmí hlásit záporný průběh. */
	@Test
	public void velkySouborNepretece() throws Exception {
		final Zaznam z = new Zaznam();
		final InputStream obri = new InputStream() {
			@Override
			public int read() {
				return 0;
			}

			@Override
			public long skip(final long n) {
				return n;
			}

			@Override
			public int available() throws IOException {
				return Integer.MAX_VALUE;
			}
		};
		try (InputStream in = new ProgressorInputStream(z, "test", obri)) {
			in.skip(3_000_000_000L);
			assertThat(z.posledni).isAtLeast(0);
			in.skip(1_500_000_000L);
			in.skip(1_500_000_000L);
			assertThat(z.posledni).isEqualTo(Integer.MAX_VALUE);
		}
	}

	/** Pruh se ukončí, i když zavření proudu selže. */
	@Test
	public void chybaPriZavreniUkonciPruh() throws Exception {
		final Zaznam z = new Zaznam();
		final InputStream vadny = new ByteArrayInputStream(new byte[10]) {
			@Override
			public void close() throws IOException {
				throw new IOException("test");
			}
		};
		try {
			new ProgressorInputStream(z, "test", vadny).close();
			org.junit.Assert.fail("chyba zavření se má ohlásit");
		} catch (final IOException e) {
			assertThat(e.getMessage()).isEqualTo("test");
		}
		assertThat(z.ukoncen).isTrue();
	}
}
