package cz.geokuk.core.profile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Poškozené nastavení vedle programu nesmí zabránit spuštění. */
public class FPreferencesInNearFileTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void vadnySouborSeOdlozi() throws Exception {
		final File soubor = tmp.newFile("geokuk-preferences.xml");
		Files.write(soubor.toPath(), "<preferences><root ".getBytes(StandardCharsets.UTF_8));
		final String varovani = FPreferencesInNearFile.nacti(soubor);
		Assert.assertNotNull("uživatel se to musí dozvědět", varovani);
		Assert.assertFalse("vadný soubor se nesmí načítat znovu", soubor.exists());
		Assert.assertTrue(new File(soubor.getPath() + ".vadne").isFile());
	}

	@Test
	public void chybejiciSouborSeOhlasiBezOdlozeni() {
		final File soubor = new File(tmp.getRoot(), "neni.xml");
		Assert.assertNotNull(FPreferencesInNearFile.nacti(soubor));
		Assert.assertFalse(new File(soubor.getPath() + ".vadne").exists());
	}
}
