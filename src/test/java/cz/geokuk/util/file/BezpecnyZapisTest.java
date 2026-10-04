package cz.geokuk.util.file;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Zápis souboru nesmí zničit původní obsah, když se nepovede. */
public class BezpecnyZapisTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static String obsah(final File soubor) throws IOException {
		return new String(Files.readAllBytes(soubor.toPath()), StandardCharsets.UTF_8);
	}

	/** Kdo soubor čte během přepisování, vidí vždy celý starý nebo celý nový obsah (přejmenování, ne přepis na místě). */
	@Test
	public void ctenarNevidiRozepsanySoubor() throws Exception {
		// Ve Windows čtení během přejmenování selhává sdílením souboru, ne poloviční obsah.
		Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("windows"));
		final File soubor = new File(tmp.getRoot(), "data.bin");
		final int velikost = 1 << 20;
		BezpecnyZapis.zapis(soubor, out -> vypln(out, 'A', velikost));
		final java.util.concurrent.atomic.AtomicBoolean konec = new java.util.concurrent.atomic.AtomicBoolean();
		final java.util.concurrent.atomic.AtomicReference<String> chyba = new java.util.concurrent.atomic.AtomicReference<>();
		final Thread ctenar = new Thread(() -> {
			while (!konec.get() && chyba.get() == null) {
				try {
					final byte[] b = Files.readAllBytes(soubor.toPath());
					if (b.length != velikost || b[0] != b[b.length - 1]) {
						chyba.set("rozepsaný soubor: " + b.length + " B");
					}
				} catch (final IOException e) {
					chyba.set("soubor chvíli neexistoval: " + e);
				}
			}
		});
		ctenar.start();
		for (int i = 0; i < 50 && chyba.get() == null; i++) {
			final char znak = i % 2 == 0 ? 'B' : 'A';
			BezpecnyZapis.zapis(soubor, out -> vypln(out, znak, velikost));
		}
		konec.set(true);
		ctenar.join();
		Assert.assertNull(chyba.get());
	}

	private static void vypln(final java.io.OutputStream out, final char znak, final int velikost) throws IOException {
		final byte[] blok = new byte[64 * 1024];
		java.util.Arrays.fill(blok, (byte) znak);
		for (int i = 0; i < velikost; i += blok.length) {
			out.write(blok);
		}
	}

	@Test
	public void zapiseSoubor() throws Exception {
		final File soubor = new File(tmp.getRoot(), "data.txt");
		BezpecnyZapis.zapisText(soubor, StandardCharsets.UTF_8, wrt -> wrt.print("Žluťoučký kůň"));
		Assert.assertEquals("Žluťoučký kůň", obsah(soubor));
	}

	@Test
	public void prepiseStarySoubor() throws Exception {
		final File soubor = tmp.newFile("data.txt");
		Files.write(soubor.toPath(), "staré a dlouhé".getBytes(StandardCharsets.UTF_8));
		BezpecnyZapis.zapisText(soubor, StandardCharsets.UTF_8, wrt -> wrt.print("nové"));
		Assert.assertEquals("nové", obsah(soubor));
		Assert.assertEquals(1, tmp.getRoot().list().length);
	}

	@Test
	public void chybaBehemZapisuNechaPuvodniSoubor() throws Exception {
		final File soubor = tmp.newFile("data.txt");
		Files.write(soubor.toPath(), "původní".getBytes(StandardCharsets.UTF_8));
		try {
			BezpecnyZapis.zapisText(soubor, StandardCharsets.UTF_8, wrt -> {
				wrt.print("polovina");
				throw new IOException("disk je plný");
			});
			Assert.fail("chyba zápisu se má ohlásit");
		} catch (final IOException e) {
			Assert.assertEquals("disk je plný", e.getMessage());
		}
		Assert.assertEquals("původní", obsah(soubor));
		Assert.assertEquals("dočasný soubor nesmí zůstat", 1, tmp.getRoot().list().length);
	}

	@Test
	public void chybaPrivelmiVelkemObsahu() throws Exception {
		final File soubor = tmp.newFile("data.txt");
		Files.write(soubor.toPath(), "původní".getBytes(StandardCharsets.UTF_8));
		try {
			BezpecnyZapis.zapis(soubor, out -> {
				out.write("kus".getBytes(StandardCharsets.UTF_8));
				throw new IOException("selhalo");
			});
			Assert.fail();
		} catch (final IOException e) {
			// čekáno
		}
		Assert.assertEquals("původní", obsah(soubor));
	}

	@Test
	public void vytvoriChybejiciSlozku() throws Exception {
		final File soubor = new File(new File(tmp.getRoot(), "nova/hlubsi"), "data.txt");
		BezpecnyZapis.zapisText(soubor, StandardCharsets.UTF_8, wrt -> wrt.print("x"));
		Assert.assertEquals("x", obsah(soubor));
	}

	@Test(expected = IOException.class)
	public void nezapisovatelneMistoSeOhlasi() throws Exception {
		final File soubor = tmp.newFile("data.txt");
		BezpecnyZapis.zapisText(new File(soubor, "nelze"), StandardCharsets.UTF_8, wrt -> wrt.print("x"));
	}
}
