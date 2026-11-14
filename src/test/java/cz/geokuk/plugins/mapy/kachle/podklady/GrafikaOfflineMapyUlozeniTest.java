package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Uložení symbolů tématu přes dočasný soubor. */
public class GrafikaOfflineMapyUlozeniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"4\" height=\"4\"><rect width=\"4\" height=\"4\"/></svg>";

	/** Dočasný soubor jiné instance (stejné jméno jako dřív) uložení nepřekáží a zůstane nedotčený. */
	@Test
	public void ciziDocasnySouborUlozeniNeprekazi() throws Exception {
		final File soubor = new File(tmp.getRoot(), "symboly-0000000a.bin");
		final File cizi = new File(tmp.getRoot(), soubor.getName() + ".tmp");
		Assert.assertTrue(cizi.mkdir());
		final GrafikaOfflineMapy grafika = new GrafikaOfflineMapy(soubor);
		grafika.renderSvg(new ByteArrayInputStream(SVG.getBytes(StandardCharsets.UTF_8)), 1f, 4, 4, 100, 1);
		grafika.uloz();
		Assert.assertTrue("symboly se uložily", soubor.isFile());
		Assert.assertEquals(1, new GrafikaOfflineMapy(soubor).pocetSymbolu());
		Assert.assertTrue(cizi.isDirectory());
	}
}
