package cz.geokuk.plugins.geocoding;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;

public class OdkazyNaMapyTest {

	private static final Wgs MISTO = new Wgs(50.186164, 15.040611);

	@Test
	public void mapyComMaDelkuASirkuVIdAStredu() {
		assertEquals("https://mapy.com/cs/turisticka?source=coor&id=15.040611%2C50.186164&x=15.040611&y=50.186164&z=16", OdkazyNaMapy.mapyCom(MISTO));
	}

	@Test
	public void googleMaSirkuADelku() {
		assertEquals("https://www.google.com/maps/search/?api=1&query=50.186164,15.040611", OdkazyNaMapy.google(MISTO));
	}

	@Test
	public void cuzkMaSJtskSNazvemAZakladniMapou() {
		final String url = OdkazyNaMapy.cuzk(MISTO);
		final Matcher m = Pattern.compile("https://ags\\.cuzk\\.gov\\.cz/geoprohlizec/mapa\\.html\\?xy=(-\\d+\\.\\d\\d),(-\\d+\\.\\d\\d)&name=Ke%C5%A1ka&b=zm").matcher(url);
		assertTrue(url, m.matches());
		try {
			assertEquals(-697_471.57, Double.parseDouble(m.group(1)), 0.3);
			assertEquals(-1_037_986.22, Double.parseDouble(m.group(2)), 0.3);
		} catch (final NumberFormatException e) {
			fail(url);
		}
	}

	@Test
	public void odkazyNepouzivajiCarkuAniMistniFormat() {
		final Locale puvodni = Locale.getDefault();
		Locale.setDefault(new Locale("cs", "CZ"));
		try {
			assertEquals("https://www.google.com/maps/search/?api=1&query=50.186164,15.040611", OdkazyNaMapy.google(MISTO));
			assertTrue(OdkazyNaMapy.cuzk(MISTO).contains("xy=-697471."));
		} finally {
			Locale.setDefault(puvodni);
		}
	}

	@Test
	public void cuzkMimoRepublikuSeNenabizi() {
		assertNull(OdkazyNaMapy.cuzk(new Wgs(48.8566, 2.3522)));
		final List<OdkazyNaMapy.Odkaz> mimo = OdkazyNaMapy.pro(new Wgs(48.8566, 2.3522));
		assertEquals(2, mimo.size());
		for (final OdkazyNaMapy.Odkaz o : mimo) {
			assertFalse(o.nazev, o.nazev.contains("ČÚZK"));
		}
	}

	@Test
	public void poradiOdpovidaPodkladumVNabidceMapy() {
		final List<OdkazyNaMapy.Odkaz> odkazy = OdkazyNaMapy.pro(MISTO);
		assertEquals(3, odkazy.size());
		assertEquals("Mapy.com – Turistická", odkazy.get(0).nazev);
		assertEquals("ČÚZK Geoprohlížeč", odkazy.get(1).nazev);
		assertEquals("Google Mapy", odkazy.get(2).nazev);
		for (final OdkazyNaMapy.Odkaz o : odkazy) {
			assertTrue(o.url, o.url.startsWith("https://"));
			assertFalse(o.url, o.url.contains(" "));
		}
	}

	/** Hranice obdélníku, ve kterém se ČÚZK nabízí: na hranici ano, těsně za ní ne. */
	@Test
	public void cuzkSeNabizeVcetneHranicAleNeZaNimi() {
		assertNotNull(OdkazyNaMapy.cuzk(new Wgs(48.4, 15)));
		assertNull(OdkazyNaMapy.cuzk(new Wgs(48.399, 15)));
		assertNotNull(OdkazyNaMapy.cuzk(new Wgs(51.2, 15)));
		assertNull(OdkazyNaMapy.cuzk(new Wgs(51.201, 15)));
		assertNotNull(OdkazyNaMapy.cuzk(new Wgs(50, 11.9)));
		assertNull(OdkazyNaMapy.cuzk(new Wgs(50, 11.899)));
		assertNotNull(OdkazyNaMapy.cuzk(new Wgs(50, 19.1)));
		assertNull(OdkazyNaMapy.cuzk(new Wgs(50, 19.101)));
	}
}
