package cz.geokuk.core.program;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Zápis geokuk.ico do složky s programem a ikona v zdrojích jaru. */
public class IkonaProgramuSlozkaTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final byte[] IKONA = { 0, 0, 1, 0, 1, 0, 9, 9 };

	@Test
	public void chybejiciIkonuZapise() throws Exception {
		final Path slozka = tmp.getRoot().toPath();
		Assert.assertTrue(IkonaProgramu.zapis(slozka, IKONA));
		Assert.assertArrayEquals(IKONA, Files.readAllBytes(slozka.resolve("geokuk.ico")));
	}

	@Test
	public void odlisnouIkonuPrepise() throws Exception {
		final Path slozka = tmp.getRoot().toPath();
		Files.write(slozka.resolve("geokuk.ico"), new byte[] { 1, 2, 3 });
		Assert.assertTrue(IkonaProgramu.zapis(slozka, IKONA));
		Assert.assertArrayEquals(IKONA, Files.readAllBytes(slozka.resolve("geokuk.ico")));
	}

	@Test
	public void stejnouIkonuNeprepisuje() throws Exception {
		final Path slozka = tmp.getRoot().toPath();
		final Path ico = slozka.resolve("geokuk.ico");
		Files.write(ico, IKONA);
		final File soubor = ico.toFile();
		Assert.assertTrue(soubor.setLastModified(1_000_000_000_000L));
		Assert.assertFalse(IkonaProgramu.zapis(slozka, IKONA));
		Assert.assertEquals(1_000_000_000_000L, soubor.lastModified());
	}

	@Test
	public void poZapisuNezustaneDocasnySoubor() throws Exception {
		final Path slozka = tmp.getRoot().toPath();
		IkonaProgramu.zapis(slozka, IKONA);
		Assert.assertArrayEquals(new String[] { "geokuk.ico" }, slozka.toFile().list());
	}

	@Test
	public void ikonaJeVeZdrojichJaruAShodnaSDistribuci() throws Exception {
		try (InputStream in = IkonaProgramu.class.getResourceAsStream("/geokuk.ico")) {
			Assert.assertNotNull("geokuk.ico chybí v resources", in);
			final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			final byte[] buf = new byte[8192];
			for (int n; (n = in.read(buf)) > 0;) {
				out.write(buf, 0, n);
			}
			Assert.assertArrayEquals(Files.readAllBytes(new File("distribuce/windows/geokuk.ico").toPath()), out.toByteArray());
		}
	}

	@Test
	public void aktualizujZapisIkonuZJaru() throws Exception {
		final Path slozka = tmp.getRoot().toPath();
		IkonaProgramu.aktualizuj(slozka);
		Assert.assertTrue(Files.size(slozka.resolve("geokuk.ico")) > 1000);
	}
}
