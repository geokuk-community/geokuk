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
