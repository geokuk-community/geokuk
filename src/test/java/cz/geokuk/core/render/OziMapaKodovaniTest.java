package cz.geokuk.core.render;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/**
 * Soubor .map se zapisuje v kódování Windows (native.encoding), které OziExplorer čte, ne ve výchozím kódování Javy. Bajty se ověřují přímo, testy běží s
 * -Dfile.encoding=UTF-8.
 */
public class OziMapaKodovaniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private String puvodni;

	@Before
	public void zapamatuj() {
		puvodni = System.getProperty("native.encoding");
	}

	@After
	public void obnov() {
		if (puvodni == null) {
			System.clearProperty("native.encoding");
		} else {
			System.setProperty("native.encoding", puvodni);
		}
	}

	private byte[] zapis(final String jmeno) throws Exception {
		final File mapa = new File(tmp.getRoot(), "mapa.map");
		OziExplorerRenderSwingWorker.zapisVystupy(() -> new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", new File(tmp.getRoot(), "mapa.png"), mapa, p -> p.println(jmeno));
		return Files.readAllBytes(mapa.toPath());
	}

	@Test
	public void jmenoSDiakritikouVKodovaniWindows() throws Exception {
		System.setProperty("native.encoding", "Cp1250");
		final byte[] bajty = zapis("Brno-střed.png");
		Assert.assertEquals("Brno-střed.png", new String(bajty, "windows-1250").trim());
		Assert.assertEquals("ř je v windows-1250 jeden bajt 0xF8", (byte) 0xF8, bajty["Brno-st".length()]);
		Assert.assertNotEquals("Brno-střed.png", new String(bajty, StandardCharsets.UTF_8).trim());
	}

	@Test
	public void neznameKodovaniSystemuDaVychozi() {
		System.setProperty("native.encoding", "neexistuje-123");
		Assert.assertEquals(java.nio.charset.Charset.defaultCharset(), OziExplorerRenderSwingWorker.kodovaniMapy());
		System.clearProperty("native.encoding");
		Assert.assertEquals(java.nio.charset.Charset.defaultCharset(), OziExplorerRenderSwingWorker.kodovaniMapy());
	}
}
