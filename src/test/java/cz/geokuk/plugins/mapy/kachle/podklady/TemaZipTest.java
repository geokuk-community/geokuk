package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.rendertheme.ZipRenderTheme;
import org.mapsforge.map.rendertheme.ZipXmlThemeResourceProvider;
import org.mapsforge.map.rendertheme.rule.RenderThemeHandler;

/**
 * Zipy tématu pro aplikace (c:geo, aDrake) se načtou tak, jak je čtou aplikace: přes {@link ZipXmlThemeResourceProvider}. Zipy vznikají až po testech
 * (nastroje/tema/sestav-tema.py), složku s nimi předá CI vlastností {@code geokuk.tema.zip}; bez ní se test přeskočí.
 */
public class TemaZipTest {

	private static final Pattern ZDROJ = Pattern.compile("src=\"([a-z]+):([^\"]+)\"");

	private static File zip(final String jmeno) {
		final String slozka = System.getProperty("geokuk.tema.zip");
		Assume.assumeTrue("zipy tématu sestavuje CI po testech", slozka != null && !slozka.isEmpty());
		final File f = new File(slozka, jmeno);
		Assert.assertTrue(f.getAbsolutePath(), f.isFile());
		return f;
	}

	private static String text(final InputStream in) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final byte[] b = new byte[8192];
		for (int n; (n = in.read(b)) > 0;) {
			out.write(b, 0, n);
		}
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}

	/** Načte obě témata ze zipu jako aplikace a vrátí je jako text; ověří, že každý zdroj je v zipu a téma jde rozparsovat. */
	private static String[] nacti(final File zip) throws Exception {
		final List<String> temata;
		try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
			temata = ZipXmlThemeResourceProvider.scanXmlThemes(in);
		}
		Assert.assertTrue(zip + ": " + temata, temata.contains("GeoKuk.xml") && temata.contains("GeoKuk_detail.xml"));
		final ZipXmlThemeResourceProvider provider;
		try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
			provider = new ZipXmlThemeResourceProvider(in);
		}
		final String[] texty = new String[2];
		int i = 0;
		for (final String tema : new String[] { "GeoKuk.xml", "GeoKuk_detail.xml" }) {
			final ZipRenderTheme t = new ZipRenderTheme(tema, provider);
			final String xml;
			try (InputStream in = t.getRenderThemeAsStream()) {
				xml = text(in);
			}
			final Matcher m = ZDROJ.matcher(xml);
			while (m.find()) {
				Assert.assertEquals(zip.getName() + "/" + tema + ": " + m.group(), "file", m.group(1));
				try (InputStream s = provider.createInputStream(t.getRelativePathPrefix(), m.group(1) + ":" + m.group(2))) {
					Assert.assertNotNull(zip.getName() + "/" + tema + ": chybí " + m.group(2), s);
				}
			}
			Assert.assertNotNull(RenderThemeHandler.getRenderTheme(AwtGraphicFactory.INSTANCE, new DisplayModel(), t));
			texty[i++] = xml;
		}
		return texty;
	}

	/** Zip k mapám GeoKuku kreslí trasy z vrstvy GeoKuku (fmrel*), ne z tagů osmc_*. */
	@Test
	public void zipKMapamGeoKukuKresliTrasyZVrstvy() throws Exception {
		for (final String xml : nacti(zip("GeoKuk-tema.zip"))) {
			Assert.assertTrue(xml.contains("k=\"fmrelhikingred"));
			Assert.assertTrue(xml.contains("k=\"fmrelbicycle"));
			Assert.assertFalse(xml.contains("osmc_"));
		}
	}

	/** Zip k mapám s trasami v tagech osmc_* kreslí trasy jen z nich. */
	@Test
	public void zipPawsKresliTrasyZTaguOsmc() throws Exception {
		for (final String xml : nacti(zip("GeoKuk-tema-PAWS.zip"))) {
			Assert.assertTrue(xml.contains("k=\"osmc_color\""));
			Assert.assertFalse(xml.contains("fmrel"));
		}
	}
}
