package cz.geokuk.plugins.cesty;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.cesty.data.Cesta;

/** Import cest z GPX: plán trasy (rte) a více souborů najednou. */
public class ImportCestTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File soubor(final String jmeno, final String obsah) throws Exception {
		final File f = tmp.newFile(jmeno);
		Files.write(f.toPath(), obsah.getBytes(StandardCharsets.UTF_8));
		return f;
	}

	private static final String RTE = "<?xml version=\"1.0\"?><gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\"><rte><name>Plán</name>"
			+ "<rtept lat=\"50.0\" lon=\"14.0\"><name>Start</name></rtept><rtept lat=\"50.01\" lon=\"14.01\"/><rtept lat=\"50.02\" lon=\"14.0\"/></rte></gpx>";

	/** Seznam keší .ggt se načte v UTF-8 i v kódování Windows ze starších verzí. */
	@Test
	public void ggtVUtf8IWindows1250() throws Exception {
		final cz.geokuk.plugins.kesoid.KesBag kese = cz.geokuk.plugins.kesoid.importek.ImportKesiTestPristup
				.importuj(cz.geokuk.plugins.kesoid.importek.ImportKesiTestPristup.kesBezHodnoceni("Žluťoučký"));
		final File utf8 = soubor("utf8.ggt", "Žluťoučký\n");
		final File cp1250 = tmp.newFile("cp1250.ggt");
		Files.write(cp1250.toPath(), "Žluťoučký\r\n".getBytes("windows-1250"));
		for (final File f : Arrays.asList(utf8, cp1250)) {
			final List<Cesta> cesty = new CestyZperzistentnovac().nacti(Collections.singletonList(f), kese);
			int bodu = 0;
			for (final cz.geokuk.plugins.cesty.data.Bod b : cesty.get(0).getBody()) {
				bodu++;
			}
			Assert.assertEquals(f.getName(), 1, bodu);
		}
	}

	@Test
	public void trasaRteSeNacte() throws Exception {
		final List<Cesta> cesty = new CestyZperzistentnovac().nacti(Collections.singletonList(soubor("plan.gpx", RTE)), null);
		Assert.assertEquals(1, cesty.size());
		Assert.assertEquals("Plán", cesty.get(0).getNazev());
		int bodu = 0;
		for (final cz.geokuk.plugins.cesty.data.Bod b : cesty.get(0).getBody()) {
			bodu++;
		}
		Assert.assertEquals(3, bodu);
	}

	@Test
	public void vadnySouborNezahodiOstatni() throws Exception {
		final File dobry = soubor("dobry.gpx", RTE);
		final File vadny = soubor("vadny.gpx", RTE.substring(0, RTE.length() / 2));
		final List<Cesta> cesty = new CestyZperzistentnovac().nacti(Arrays.asList(vadny, dobry), null);
		Assert.assertEquals(1, cesty.size());
	}

	@Test(expected = RuntimeException.class)
	public void souborBezTrasySeOhlasi() throws Exception {
		new CestyZperzistentnovac().nacti(Collections.singletonList(soubor("jen-wpt.gpx",
				"<?xml version=\"1.0\"?><gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\"><wpt lat=\"50.0\" lon=\"14.0\"/></gpx>")), null);
	}
}
