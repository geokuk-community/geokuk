package cz.geokuk.core.coordinates;

import org.junit.Assert;
import org.junit.Test;

/** Souřadnice, jak je uživatelé vkládají z listingů, GPS a map. */
public class WgsParserTest {

	private static final double PRESNOST = 1e-6;

	private static void ocekavej(final String vstup, final double lat, final double lon) {
		final Wgs wgs = new WgsParser().parsruj(vstup);
		Assert.assertNotNull(vstup, wgs);
		Assert.assertEquals(vstup + " lat", lat, wgs.lat, PRESNOST);
		Assert.assertEquals(vstup + " lon", lon, wgs.lon, PRESNOST);
	}

	private static void neplatne(final String vstup) {
		Assert.assertNull(vstup, new WgsParser().parsruj(vstup));
	}

	@Test
	public void formatListinguGeocaching() {
		ocekavej("N 50° 05.123 E 014° 25.456", 50 + 5.123 / 60, 14 + 25.456 / 60);
		ocekavej("N 50° 05.123' E 014° 25.456'", 50 + 5.123 / 60, 14 + 25.456 / 60);
		ocekavej("N50 05.123 E14 25.456", 50 + 5.123 / 60, 14 + 25.456 / 60);
	}

	@Test
	public void desetinneStupne() {
		ocekavej("50.08520, 14.42430", 50.0852, 14.4243);
		ocekavej("N49.156, 16.788E", 49.156, 16.788);
		ocekavej("49,12345°16,12345", 49.12345, 16.12345);
	}

	@Test
	public void stupneMinutyVteriny() {
		ocekavej("49°16'21\" 16°18'25\"", 49 + 16 / 60.0 + 21 / 3600.0, 16 + 18 / 60.0 + 25 / 3600.0);
		ocekavej("N 49°16'21\", E 16°18'25\"", 49 + 16 / 60.0 + 21 / 3600.0, 16 + 18 / 60.0 + 25 / 3600.0);
	}

	@Test
	public void malaPismena() {
		ocekavej("n49°16.5 e16°18.5", 49 + 16.5 / 60, 16 + 18.5 / 60);
	}

	@Test
	public void jizniAZapadniPolokouleJsouZaporne() {
		ocekavej("S 33° 51.000 W 070° 39.000", -(33 + 51 / 60.0), -(70 + 39 / 60.0));
		ocekavej("-33.85, -70.65", -33.85, -70.65);
	}

	@Test
	public void prohozenePoradiSeOpravi() {
		ocekavej("E 014° 25.456 N 50° 05.123", 50 + 5.123 / 60, 14 + 25.456 / 60);
		ocekavej("17°W49°S", -49, -17);
	}

	@Test
	public void nesmyslyNejsouSouradnice() {
		neplatne("");
		neplatne("ahoj");
		neplatne("aaa49bbb16ccc");
		neplatne("33°Z49°E");
	}
}
