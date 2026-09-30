package cz.geokuk.plugins.cesty;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.cesty.data.Cesta;
import cz.geokuk.plugins.kesoid.importek.NacitacGpxPristup;

/** Import cest z GPX, jak ho vytváří jiné programy nebo ruční úpravy. */
public class ImportCestNalezyTest {

	private static List<Cesta> nacti(final String gpx) throws Exception {
		final DocImportBuilder builder = new DocImportBuilder();
		NacitacGpxPristup.nacti(new ByteArrayInputStream(gpx.getBytes(StandardCharsets.UTF_8)), builder);
		return builder.getCesty();
	}

	private static final String ZACATEK = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n";

	@Test
	public void prazdnaCestaSeNenacte() throws Exception {
		final List<Cesta> cesty = nacti(ZACATEK + "<trk><name>Prázdná</name><trkseg></trkseg></trk>\n"
				+ "<trk><name>Dobrá</name><trkseg><trkpt lat=\"50.0\" lon=\"14.0\"/><trkpt lat=\"50.1\" lon=\"14.1\"/></trkseg></trk>\n</gpx>");
		for (final Cesta c : cesty) {
			Assert.assertFalse("prázdná cesta " + c.getNazev() + " se nemá načíst", c.isEmpty());
			c.isJednobodova(); // u prázdné padá NPE
		}
		Assert.assertEquals(1, cesty.size());
	}

	@Test
	public void vadnyBodNezahodiOstatniCesty() throws Exception {
		final List<Cesta> cesty = nacti(ZACATEK + "<trk><name>Dobrá</name><trkseg><trkpt lat=\"50.0\" lon=\"14.0\"/><trkpt lat=\"50.1\" lon=\"14.1\"/></trkseg></trk>\n"
				+ "<trk><name>Vadná</name><trkseg><trkpt lat=\"x\" lon=\"14.0\"/><trkpt lat=\"50.2\" lon=\"14.2\"/></trkseg></trk>\n</gpx>");
		Assert.assertTrue("dobrá cesta se má načíst", cesty.stream().anyMatch(c -> "Dobrá".equals(c.getNazev())));
	}
}
