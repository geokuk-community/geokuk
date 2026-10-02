package cz.geokuk.util.file;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Nečitelné nebo zacyklené místo nesmí shodit celý sken datové složky. */
public class DirScannerTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final Root.Def VSE = new Root.Def(10, null, null);

	private List<KeFile> scan() {
		return new DirScanner().scan(new Root(tmp.getRoot(), VSE));
	}

	@Test
	public void najdeSoubory() throws Exception {
		Files.write(tmp.newFile("a.gpx").toPath(), "x".getBytes(StandardCharsets.UTF_8));
		Files.write(new File(tmp.newFolder("podslozka"), "b.gpx").toPath(), "y".getBytes(StandardCharsets.UTF_8));
		Assert.assertEquals(2, scan().size());
	}

	@Test
	public void zacyklenyOdkazNevadi() throws Exception {
		Files.write(tmp.newFile("a.gpx").toPath(), "x".getBytes(StandardCharsets.UTF_8));
		final File podslozka = tmp.newFolder("smycka");
		try {
			Files.createSymbolicLink(Paths.get(podslozka.getPath(), "zpet"), tmp.getRoot().toPath());
		} catch (final UnsupportedOperationException | FileSystemException e) {
			Assume.assumeNoException("systém nebo uživatel odkazy vytvářet neumí", e);
		}
		Assert.assertTrue("ostatní soubory se najdou", scan().size() >= 1);
	}

	@Test
	public void vynechanaSlozkaSeNeprochazi() throws Exception {
		Files.write(tmp.newFile("a.gpx").toPath(), "x".getBytes(StandardCharsets.UTF_8));
		final File cesty = tmp.newFolder("cesty");
		Files.write(new File(cesty, "cesta.gpx").toPath(), "y".getBytes(StandardCharsets.UTF_8));
		final java.util.Set<File> vynechane = java.util.Collections.singleton(cesty.toPath().toAbsolutePath().normalize().toFile());
		Assert.assertEquals(1, new DirScanner().scan(new Root(tmp.getRoot(), VSE, vynechane)).size());
		Assert.assertEquals("vynechaná složka jako kořen se projde", 1, new DirScanner().scan(new Root(cesty, VSE, vynechane)).size());
	}

	@Test
	public void chybejiciSlozkaDaPrazdnySeznam() {
		Assert.assertTrue(new DirScanner().scan(new Root(new File(tmp.getRoot(), "neni"), VSE)).isEmpty());
	}
}
