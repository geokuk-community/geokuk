package cz.geokuk.plugins.kesoid.mapicon;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Obrázek ikony v neznámém formátu nesmí shodit kreslení. */
public class ImageProviderCachedTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void neznamyFormatDaPrazdnyObrazek() throws Exception {
		final File f = tmp.newFile("ikona.png");
		Files.write(f.toPath(), "není obrázek".getBytes(StandardCharsets.UTF_8));
		Assert.assertNotNull(new ImageProviderCached("test").getImage(f.toURI().toURL()));
	}
}
