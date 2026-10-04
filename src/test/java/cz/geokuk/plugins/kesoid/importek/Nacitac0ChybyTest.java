package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.Future;
import java.util.zip.*;

import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;

/** Vadný soubor, na kterém knihovna přeteče zásobník nebo paměť, nesmí ukončit načítání ostatních. */
public class Nacitac0ChybyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final java.util.function.LongSupplier puvodniVolnaPamet = Nacitac0.volnaPamet;

	@After
	public void obnovVolnouPamet() {
		Nacitac0.volnaPamet = puvodniVolnaPamet;
	}

	private File zip() throws IOException {
		final File zip = tmp.newFile("a.zip");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
			out.putNextEntry(new ZipEntry("a.jpg"));
			out.closeEntry();
		}
		return zip;
	}

	private static final class PadajiciNacitac extends Nacitac0 {
		private final Error chyba;

		PadajiciNacitac(final Error chyba) {
			this.chyba = chyba;
		}

		@Override
		protected void nacti(final File file, final IImportBuilder builder, final Future<?> future, final ProgressModel aProgressModel) {
			throw chyba;
		}

		@Override
		protected void nacti(final ZipFile zipFile, final ZipEntry zipEntry, final IImportBuilder builder, final Future<?> future, final ProgressModel aProgressModel) {
			throw chyba;
		}

		@Override
		boolean umiNacist(final File file) {
			return true;
		}

		@Override
		boolean umiNacist(final ZipEntry zipEntry) {
			return true;
		}
	}

	@Test
	public void preteceniZasobnikuVSouboru() throws Exception {
		new PadajiciNacitac(new StackOverflowError()).nactiBezVyjimky(tmp.newFile("a.jpg"), null, null, null);
	}

	@Test
	public void nedostatekPametiVSouboru() throws Exception {
		new PadajiciNacitac(new OutOfMemoryError("Java heap space")).nactiBezVyjimky(tmp.newFile("a.jpg"), null, null, null);
	}

	@Test
	public void preteceniZasobnikuVZipu() throws Exception {
		try (ZipFile zf = new ZipFile(zip())) {
			new PadajiciNacitac(new StackOverflowError()).nactiBezVyjimky(zf, zf.getEntry("a.jpg"), null, null, null);
		}
	}

	@Test
	public void nedostatekPametiVZipu() throws Exception {
		try (ZipFile zf = new ZipFile(zip())) {
			new PadajiciNacitac(new OutOfMemoryError("Java heap space")).nactiBezVyjimky(zf, zf.getEntry("a.jpg"), null, null, null);
		}
	}

	@Test
	public void plnaHaldaVSouboruUkonciImport() throws Exception {
		Nacitac0.volnaPamet = () -> 0;
		final OutOfMemoryError oom = new OutOfMemoryError("Java heap space");
		try {
			new PadajiciNacitac(oom).nactiBezVyjimky(tmp.newFile("a.jpg"), null, null, null);
			Assert.fail("Nedostatek paměti se nesmí schovat za poškozený soubor");
		} catch (final OutOfMemoryError e) {
			Assert.assertSame(oom, e);
		}
	}

	@Test
	public void plnaHaldaVZipuUkonciImport() throws Exception {
		Nacitac0.volnaPamet = () -> 0;
		final OutOfMemoryError oom = new OutOfMemoryError("Java heap space");
		try (ZipFile zf = new ZipFile(zip())) {
			new PadajiciNacitac(oom).nactiBezVyjimky(zf, zf.getEntry("a.jpg"), null, null, null);
			Assert.fail("Nedostatek paměti se nesmí schovat za poškozený soubor");
		} catch (final OutOfMemoryError e) {
			Assert.assertSame(oom, e);
		}
	}
}
