package cz.geokuk.core.render;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Neúspěšný nebo zrušený rendr nesmí smazat výstupy dřívějších rendrů. */
public class RenderVystupyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File obrazek;
	private File mapa;

	@Before
	public void setUp() throws Exception {
		obrazek = tmp.newFile("mapa.png");
		mapa = tmp.newFile("mapa.map");
		Files.write(obrazek.toPath(), "stary obrazek".getBytes(StandardCharsets.UTF_8));
		Files.write(mapa.toPath(), "stara kalibrace".getBytes(StandardCharsets.UTF_8));
	}

	private static BufferedImage zrusit() throws InterruptedException {
		throw new InterruptedException("zrušeno");
	}

	@Test
	public void zrusenyRendrObrazkuNechaDrivejsiSoubory() throws Exception {
		try {
			OziExplorerRenderSwingWorker.zapisVystupy(RenderVystupyTest::zrusit, "png", obrazek, null, p -> Assert.fail());
			Assert.fail();
		} catch (final InterruptedException e) {
			// očekáváno
		}
		Assert.assertEquals("stary obrazek", new String(Files.readAllBytes(obrazek.toPath()), StandardCharsets.UTF_8));
		Assert.assertEquals("stara kalibrace", new String(Files.readAllBytes(mapa.toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void zrusenyRendrProOziNechaDrivejsiSoubory() throws Exception {
		try {
			OziExplorerRenderSwingWorker.zapisVystupy(RenderVystupyTest::zrusit, "png", obrazek, mapa, p -> Assert.fail());
			Assert.fail();
		} catch (final InterruptedException e) {
			// očekáváno
		}
		Assert.assertTrue(obrazek.isFile());
		Assert.assertTrue(mapa.isFile());
	}

	@Test
	public void rendrObrazkuNesahaNaKalibraci() throws Exception {
		OziExplorerRenderSwingWorker.zapisVystupy(() -> new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", obrazek, null, p -> Assert.fail());
		Assert.assertEquals("stara kalibrace", new String(Files.readAllBytes(mapa.toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void chybaZapisuKalibraceSmazeObaNoveSoubory() throws Exception {
		try {
			OziExplorerRenderSwingWorker.zapisVystupy(() -> new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", obrazek, mapa, p -> {
				throw new IOException("plný disk");
			});
			Assert.fail();
		} catch (final IOException e) {
			// očekáváno
		}
		Assert.assertFalse(obrazek.exists());
		Assert.assertFalse(mapa.exists());
	}
}
