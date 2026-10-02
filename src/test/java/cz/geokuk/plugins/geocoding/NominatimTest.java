package cz.geokuk.plugins.geocoding;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;

/** Čtení odpovědí Nominatimu ve formátu XML. */
public class NominatimTest {

	private static ByteArrayInputStream xml(final String s) {
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void hledani() throws IOException {
		final List<Nalezenec> nalezenci = Nominatim.ctiHledani(xml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<searchresults querystring=\"Vranovice\"><place lat=\"48.9667\" lon=\"16.6060\" display_name=\"Vranovice, okres Brno-venkov, Jihomoravský kraj, Česko\" class=\"boundary\" type=\"administrative\">"
				+ "<village>Vranovice</village><county>okres Brno-venkov</county><state>Jihomoravský kraj</state><country>Česko</country></place>"
				+ "<place lat=\"49.6\" lon=\"13.4\" display_name=\"Vranovice, Plzeňský kraj, Česko\" class=\"place\" type=\"village\"/></searchresults>"));
		assertEquals(2, nalezenci.size());
		final Nalezenec n = nalezenci.get(0);
		assertEquals("Vranovice, okres Brno-venkov, Jihomoravský kraj, Česko", n.adresa);
		assertEquals(48.9667, n.wgs.lat, 1e-9);
		assertEquals(16.6060, n.wgs.lon, 1e-9);
		assertEquals("boundary/administrative", n.locationType);
		assertEquals("Jihomoravský kraj", n.administrativeArea);
		assertEquals("okres Brno-venkov", n.subAdministrativeArea);
		assertEquals("Vranovice", n.locality);
		assertNull(n.thoroughfare);
		assertNull("bez adresních částí", nalezenci.get(1).locality);
	}

	@Test
	public void zpetne() throws IOException {
		final Nalezenec n = Nominatim.ctiZpetne(xml("<reversegeocode><result lat=\"49.2077\" lon=\"16.6128\">Lidická 1880/50, Černá Pole, Brno, Jihomoravský kraj, 602 00, Česko</result>"
				+ "<addressparts><house_number>1880/50</house_number><road>Lidická</road><suburb>Černá Pole</suburb><city>Brno</city><county>okres Brno-město</county>"
				+ "<state>Jihomoravský kraj</state><postcode>602 00</postcode><country>Česko</country></addressparts></reversegeocode>"));
		assertEquals("Lidická 1880/50, Černá Pole, Brno, Jihomoravský kraj, 602 00, Česko", n.adresa);
		assertEquals("Jihomoravský kraj", n.administrativeArea);
		assertEquals("okres Brno-město", n.subAdministrativeArea);
		assertEquals("Brno", n.locality);
		assertEquals("Lidická 1880/50", n.thoroughfare);
	}

	@Test
	public void zpetneBezAdresy() throws IOException {
		assertNull(Nominatim.ctiZpetne(xml("<reversegeocode><error>Unable to geocode</error></reversegeocode>")));
	}

	@Test(expected = IOException.class)
	public void doctypeSeNecte() throws IOException {
		Nominatim.ctiHledani(xml("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><searchresults>&e;</searchresults>"));
	}

	@Test
	public void odkazNaMapu() {
		assertEquals("https://www.openstreetmap.org/?mlat=50.080000&mlon=14.420000#map=17/50.080000/14.420000", Nominatim.odkazNaMapu(new Wgs(50.08, 14.42)));
	}
}
