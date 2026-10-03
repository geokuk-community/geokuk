package cz.geokuk.util.file;

import static com.google.common.truth.Truth.assertThat;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.management.UnixOperatingSystemMXBean;

public class FileManagerTest {

	private static final int POKUSU = 50;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static long otevrenychSouboru() {
		final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
		Assume.assumeTrue(os instanceof UnixOperatingSystemMXBean);
		return ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount();
	}

	@Test
	public void kopiePrenesObsahACas() throws Exception {
		final File zdroj = tmp.newFile("a.txt");
		Files.write(zdroj.toPath(), "obsah".getBytes(StandardCharsets.UTF_8));
		zdroj.setLastModified(1_000_000_000_000L);
		final File cil = new File(tmp.getRoot(), "b.txt");
		FileManager.getInstance(2).copyFileToFile(zdroj, cil);
		assertThat(new String(Files.readAllBytes(cil.toPath()), StandardCharsets.UTF_8)).isEqualTo("obsah");
		assertThat(cil.lastModified()).isEqualTo(zdroj.lastModified());
	}

	@Test
	public void nepovedenaKopieNenechaOtevrenySoubor() throws Exception {
		final File zdroj = tmp.newFile("a.txt");
		final File cil = new File(tmp.getRoot(), "neni/b.txt");
		final FileManager fm = FileManager.getInstance(4096);
		final long pred = otevrenychSouboru();
		for (int i = 0; i < POKUSU; i++) {
			try {
				fm.copyFileToFile(zdroj, cil);
			} catch (final IOException e) {
				// cílová složka neexistuje
			}
		}
		assertThat(otevrenychSouboru() - pred).isLessThan((long) POKUSU / 2);
	}

	@Test
	public void nepovedenaKopieProuduZavreProud() throws Exception {
		final boolean[] zavreny = new boolean[1];
		final InputStream in = new ByteArrayInputStream(new byte[0]) {
			@Override
			public void close() {
				zavreny[0] = true;
			}
		};
		try {
			FileManager.getInstance(4096).copyInputStreamToFile(in, new File(tmp.getRoot(), "neni/b.txt"));
		} catch (final IOException e) {
			// cílová složka neexistuje
		}
		assertThat(zavreny[0]).isTrue();
	}
}
