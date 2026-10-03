package cz.geokuk.core.coordinates;

import java.util.Random;

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

	@Test
	public void souradniceMimoRozsahNejsouSouradnice() {
		neplatne("95 14");
		neplatne("N 95° 00.000 E 014° 00.000");
		neplatne("50 200");
		neplatne("N 50° 00.000 E 181° 00.000");
		neplatne("N 50° 60.000 E 014° 00.000");
		neplatne("N 50° 10' 60\" E 014° 00' 00\"");
		neplatne("1" + new String(new char[400]).replace('\0', '0') + " 14");
		ocekavej("S 90 W 180", -90, -180);
		ocekavej("N 50° 59.999 E 014° 59' 59.9\"", 50 + 59.999 / 60, 14 + 59 / 60.0 + 59.9 / 3600);
	}

	/** Náhodné vstupy z typických znaků: parser nespadne a nevrátí souřadnice mimo rozsah. */
	@Test
	public void nahodneVstupy() {
		final String znaky = "0123456789  .,°'\"NSEWnsew-x";
		final Random random = new Random(20261003);
		final WgsParser parser = new WgsParser();
		for (int i = 0; i < 50_000; i++) {
			final StringBuilder sb = new StringBuilder();
			final int delka = 3 + random.nextInt(25);
			for (int j = 0; j < delka; j++) {
				sb.append(znaky.charAt(random.nextInt(znaky.length())));
			}
			final String vstup = sb.toString();
			final Wgs wgs = parser.parsruj(vstup);
			if (wgs != null) {
				Assert.assertTrue(vstup + " -> " + wgs, Math.abs(wgs.lat) <= 90);
			}
		}
	}

	/** Náhodné stupně a minuty s písmeny světových stran: platné se načtou přesně, ostatní vůbec. */
	@Test
	public void nahodneStupneAMinuty() {
		final Random random = new Random(3102026);
		final WgsParser parser = new WgsParser();
		for (int i = 0; i < 20_000; i++) {
			final int lat = random.nextInt(120);
			final int lon = random.nextInt(240);
			final int latMin = random.nextInt(80);
			final int lonMin = random.nextInt(80);
			final String vstup = String.format("N %d° %d.5 E %d° %d.5", lat, latMin, lon, lonMin);
			final Wgs wgs = parser.parsruj(vstup);
			final boolean platne = lat + (latMin + 0.5) / 60 <= 90 && lon + (lonMin + 0.5) / 60 <= 180 && latMin < 60 && lonMin < 60;
			if (platne) {
				Assert.assertNotNull(vstup, wgs);
				Assert.assertEquals(vstup, lat + (latMin + 0.5) / 60, wgs.lat, PRESNOST);
				Assert.assertEquals(vstup, FGeoKonvertor.normalizujUhel(lon + (lonMin + 0.5) / 60), wgs.lon, PRESNOST);
			} else {
				Assert.assertNull(vstup, wgs);
			}
		}
	}
}
