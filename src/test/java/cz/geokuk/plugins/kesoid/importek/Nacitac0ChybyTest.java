package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.Future;
import java.util.zip.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;

/** Vadný soubor, na kterém knihovna přeteče zásobník nebo paměť, nesmí ukončit načítání ostatních. */
public class Nacitac0ChybyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

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
		final File zip = tmp.newFile("a.zip");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
			out.putNextEntry(new ZipEntry("a.jpg"));
			out.closeEntry();
		}
		try (ZipFile zf = new ZipFile(zip)) {
			new PadajiciNacitac(new StackOverflowError()).nactiBezVyjimky(zf, zf.getEntry("a.jpg"), null, null, null);
		}
	}
}
