package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Poškozený soubor symbolů se po neúspěšném čtení zavře, ať ho jde na Windows přepsat. */
public class GrafikaOfflineMapyPoskozenyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void poskozenySouborZustaneZavreny() throws Exception {
		final Object os = ManagementFactory.getOperatingSystemMXBean();
		Assume.assumeTrue("počet otevřených souborů zná jen Unix", os instanceof com.sun.management.UnixOperatingSystemMXBean);
		final com.sun.management.UnixOperatingSystemMXBean unix = (com.sun.management.UnixOperatingSystemMXBean) os;
		final File soubor = tmp.newFile("symboly.bin");
		Files.write(soubor.toPath(), "není gzip".getBytes(StandardCharsets.UTF_8));
		final long pred = unix.getOpenFileDescriptorCount();
		for (int i = 0; i < 200; i++) {
			new GrafikaOfflineMapy(soubor);
		}
		Assert.assertTrue("otevřené soubory: " + pred + " → " + unix.getOpenFileDescriptorCount(), unix.getOpenFileDescriptorCount() - pred < 50);
	}
}
