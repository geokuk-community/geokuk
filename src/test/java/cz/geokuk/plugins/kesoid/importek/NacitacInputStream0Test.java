package cz.geokuk.plugins.kesoid.importek;

import static com.google.common.truth.Truth.assertThat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.zip.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;

public class NacitacInputStream0Test {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Načítač, který čte jen začátek a stream nezavírá, jako parser při chybě. */
	private static final class NezaviraciNacitac extends NacitacInputStream0 {
		final List<SledovanyStream> otevrene = new ArrayList<>();

		@Override
		protected void nacti(final InputStream aIstm, final String name, final IImportBuilder builder, final Future<?> future) throws IOException {
			aIstm.read();
		}

		@Override
		protected InputStream wrapByProgressor(final InputStream istm, final String sourceName, final ProgressModel aProgressModel) {
			final SledovanyStream s = new SledovanyStream(istm);
			otevrene.add(s);
			return s;
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

	private static final class SledovanyStream extends FilterInputStream {
		boolean zavreny;

		SledovanyStream(final InputStream in) {
			super(in);
		}

		@Override
		public void close() throws IOException {
			zavreny = true;
			super.close();
		}
	}

	@Test
	public void souborSePoNacteniZavre() throws Exception {
		final File soubor = tmp.newFile("a.gpx");
		Files.write(soubor.toPath(), "<gpx/>".getBytes(StandardCharsets.UTF_8));
		final NezaviraciNacitac nacitac = new NezaviraciNacitac();
		nacitac.nacti(soubor, null, null, null);
		assertThat(nacitac.otevrene).hasSize(1);
		assertThat(nacitac.otevrene.get(0).zavreny).isTrue();
	}

	@Test
	public void polozkaZipuSePoNacteniZavre() throws Exception {
		final File zip = tmp.newFile("a.zip");
		try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
			out.putNextEntry(new ZipEntry("a.gpx"));
			out.write("<gpx/>".getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		final NezaviraciNacitac nacitac = new NezaviraciNacitac();
		try (ZipFile zf = new ZipFile(zip)) {
			nacitac.nacti(zf, zf.getEntry("a.gpx"), null, null, null);
		}
		assertThat(nacitac.otevrene).hasSize(1);
		assertThat(nacitac.otevrene.get(0).zavreny).isTrue();
	}
}
