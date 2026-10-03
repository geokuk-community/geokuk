package cz.geokuk.core.coordinates;

import org.junit.Assert;
import org.junit.Test;

/** Vzdálenosti, azimut, formátování souřadnic a převod na UTM a zpět (reference pyproj, EPSG:326xx/327xx). */
public class VzdalenostAUtmTest {

	private static final Wgs PRAHA = new Wgs(50.0755, 14.4378);
	private static final Wgs BRNO = new Wgs(49.1951, 16.6068);

	@Test
	public void vzdalenostPrahaBrno() {
		Assert.assertEquals(184_539, Wgs.vzdalenost(PRAHA, BRNO), 5);
		Assert.assertEquals(Wgs.vzdalenost(PRAHA, BRNO), Wgs.vzdalenost(BRNO, PRAHA), 1e-6);
	}

	@Test
	public void vzdalenostKrajniPripady() {
		Assert.assertEquals(0, Wgs.vzdalenost(PRAHA, PRAHA), 1e-6);
		Assert.assertEquals(111_319.5, Wgs.vzdalenost(new Wgs(0, 0), new Wgs(1, 0)), 0.5);
		Assert.assertEquals(20_037_508, Wgs.vzdalenost(new Wgs(0, 0), new Wgs(0, 180)), 1);
		Assert.assertEquals(Wgs.vzdalenost(new Wgs(10, 179.9), new Wgs(10, -179.9)), Wgs.vzdalenost(new Wgs(10, -0.1), new Wgs(10, 0.1)), 1e-3);
	}

	@Test
	public void vzdalenostTextem() {
		Assert.assertEquals("0 m", Wgs.vzdalenostStr(PRAHA, PRAHA));
		Assert.assertEquals("111 m", Wgs.vzdalenostStr(new Wgs(50, 14), new Wgs(50.001, 14)));
		Assert.assertEquals("55.7 km", Wgs.vzdalenostStr(new Wgs(0, 0), new Wgs(0.5, 0)));
		Assert.assertEquals("185 km", Wgs.vzdalenostStr(PRAHA, BRNO));
	}

	@Test
	public void azimutSvetovychStran() {
		final Wgs stred = new Wgs(50, 14);
		Assert.assertEquals(0, Wgs.azimut(stred, new Wgs(50.01, 14)), 0.01);
		Assert.assertEquals(90, Wgs.azimut(stred, new Wgs(50, 14.01)), 0.01);
		Assert.assertEquals(180, Wgs.azimut(stred, new Wgs(49.99, 14)), 0.01);
		Assert.assertEquals(270, Wgs.azimut(stred, new Wgs(50, 13.99)), 0.01);
		Assert.assertEquals("90°", Wgs.azimutStr(stred, new Wgs(50, 14.01)));
	}

	@Test
	public void normalizaceUhlu() {
		Assert.assertEquals(0, FGeoKonvertor.normalizujUhel(360), 1e-9);
		Assert.assertEquals(-180, FGeoKonvertor.normalizujUhel(180), 1e-9);
		Assert.assertEquals(-180, FGeoKonvertor.normalizujUhel(540), 1e-9);
		Assert.assertEquals(170, FGeoKonvertor.normalizujUhel(-190), 1e-9);
		Assert.assertEquals(-10, FGeoKonvertor.normalizujUhel(350), 1e-9);
	}

	@Test(expected = RuntimeException.class)
	public void normalizaceNaN() {
		FGeoKonvertor.normalizujUhel(Double.NaN);
	}

	@Test
	public void formatStupneMinuty() {
		Assert.assertEquals("50°04.530", Wgs.toGeoFormat(50.0755));
		Assert.assertEquals("14°26.268", Wgs.toGeoFormat(14.4378));
		Assert.assertEquals("05°00.000", Wgs.toGeoFormat(5));
		Assert.assertEquals("minuty se nezaokrouhlí na 60", "15°00.000", Wgs.toGeoFormat(14.99999999));
		Assert.assertEquals("N50°00.000 W00°07.800", new Wgs(49.9999999999, -0.13).toString());
	}

	@Test
	public void formatStupneMinutyVteriny() {
		Assert.assertEquals("50°04'31\"", Wgs.toDdMmSsFormat(50.0755));
		Assert.assertEquals("49°16'21\"", Wgs.toDdMmSsFormat(49 + 16 / 60.0 + 21.5 / 3600));
	}

	@Test
	public void utmPrahy() {
		final Utm utm = PRAHA.toUtm();
		Assert.assertEquals(33, utm.polednikovaZona);
		Assert.assertEquals('U', utm.rovnobezkovaZona);
		Assert.assertEquals(459_772, utm.ux, 2);
		Assert.assertEquals(5_547_177, utm.uy, 2);
	}

	@Test
	public void utmJizniPolokoule() {
		final Utm utm = new Wgs(-33.8688, 151.2093).toUtm();
		Assert.assertEquals(56, utm.polednikovaZona);
		Assert.assertEquals('H', utm.rovnobezkovaZona);
		Assert.assertEquals(334_369, utm.ux, 2);
		Assert.assertEquals(6_250_948, utm.uy, 2);
	}

	@Test
	public void utmTamAZpet() {
		for (final Wgs w : new Wgs[] { PRAHA, BRNO, new Wgs(-33.8688, 151.2093), new Wgs(40.7128, -74.006), new Wgs(-22.9068, -43.1729), new Wgs(0.5, 0.5) }) {
			final Wgs zpet = w.toUtm().toWgs();
			Assert.assertEquals(w + " lat", w.lat, zpet.lat, 2e-5);
			Assert.assertEquals(w + " lon", w.lon, zpet.lon, 3e-5);
		}
	}

	/** Lat, lon, zóna, x, y podle nezávislého výpočtu (pyproj); body i u okraje zóny, kde se uplatní členy vyšších řádů. */
	private static final double[][] UTM_REFERENCE = { //
			{ 50.0, 12.05, 33, 288598.3, 5542801.4 }, //
			{ 49.0, 17.95, 33, 715757.3, 5431649.4 }, //
			{ 48.5, 22.5, 34, 610806.2, 5372962.0 }, //
			{ 60.17, 24.94, 35, 385700.4, 6672126.7 }, //
			{ 51.5, -0.13, 30, 699195.9, 5709335.0 }, //
			{ -45.0, 170.0, 59, 421184.7, 5016563.2 }, //
			{ 64.1, -21.9, 27, 456137.6, 7108467.4 }, //
			{ 36.0, -5.6, 30, 265643.1, 3987075.2 }, //
			{ 0.1, 0.1, 31, 177164.3, 11067.3 }, //
	};

	@Test
	public void utmPodleNezavislehoVypoctu() {
		for (final double[] r : UTM_REFERENCE) {
			final Utm utm = new Wgs(r[0], r[1]).toUtm();
			final String bod = r[0] + ", " + r[1];
			Assert.assertEquals(bod + " zóna", (int) r[2], utm.polednikovaZona);
			// Převod na UTM vrací celé metry (oříznuté).
			Assert.assertEquals(bod + " x", r[3], utm.ux, 1.5);
			Assert.assertEquals(bod + " y", r[4], utm.uy, 1.5);
			final Wgs zpet = new Utm(r[3], r[4], (int) r[2], utm.rovnobezkovaZona).toWgs();
			Assert.assertEquals(bod + " zpět lat", r[0], zpet.lat, 1e-5);
			Assert.assertEquals(bod + " zpět lon", r[1], zpet.lon, 1e-5);
		}
	}
}
