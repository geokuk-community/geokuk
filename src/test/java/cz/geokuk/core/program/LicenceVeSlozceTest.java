package cz.geokuk.core.program;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LicenceVeSlozceTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void doplniChybejiciSoubory() throws IOException {
		final File slozka = new File(tmp.getRoot(), "licence");
		LicenceVeSlozce.doplni(slozka);
		for (final String jmeno : new String[] { "README.txt", "LICENSE", "THIRD-PARTY.txt", "MAPOVE-PODKLADY.txt", "LGPL-3.0.txt" }) {
			Assert.assertTrue(jmeno, new File(slozka, jmeno).length() > 0);
		}
		Assert.assertTrue(text(new File(slozka, "LICENSE")).contains("GNU GENERAL PUBLIC LICENSE"));
	}

	@Test
	public void zastaralySouborObnovi() throws IOException {
		final File slozka = new File(tmp.getRoot(), "licence");
		LicenceVeSlozce.doplni(slozka);
		final File soubor = new File(slozka, "MAPOVE-PODKLADY.txt");
		final String aktualni = text(soubor);
		Files.write(soubor.toPath(), "stará verze".getBytes(StandardCharsets.UTF_8));
		LicenceVeSlozce.doplni(slozka);
		Assert.assertEquals(aktualni, text(soubor));
	}

	private static String text(final File soubor) throws IOException {
		return new String(Files.readAllBytes(soubor.toPath()), StandardCharsets.UTF_8);
	}
}
