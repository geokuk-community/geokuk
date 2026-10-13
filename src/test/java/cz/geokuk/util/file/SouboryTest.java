package cz.geokuk.util.file;

import java.io.File;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class SouboryTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void zalozSmazANastavCas() throws Exception {
		final File slozka = new File(tmp.getRoot(), "a/b/c");
		Soubory.zalozSlozky(slozka);
		Assert.assertTrue(slozka.isDirectory());
		Soubory.zalozSlozky(slozka); // existující složka nevadí

		final File soubor = new File(slozka, "x.txt");
		Assert.assertTrue(soubor.createNewFile());
		Soubory.nastavCas(soubor, 1_000_000_000_000L);
		Assert.assertEquals(1_000_000_000_000L, soubor.lastModified());

		Soubory.smaz(soubor);
		Assert.assertFalse(soubor.exists());
		Soubory.smaz(soubor); // neexistující soubor nevadí
	}
}
