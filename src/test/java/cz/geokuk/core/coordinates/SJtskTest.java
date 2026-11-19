package cz.geokuk.core.coordinates;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SJtskTest {

	private static void shoda(final double lat, final double lon, final double y, final double x, final double tolerance) {
		final SJtsk.Bod b = SJtsk.zWgs(lat, lon);
		assertEquals("y pro " + lat + "," + lon, y, b.y, tolerance);
		assertEquals("x pro " + lat + "," + lon, x, b.x, tolerance);
	}

	/** Hodnoty nezávisle spočítané knihovnou PROJ (operace S-JTSK ↔ WGS 84); souhlas do 1 m. */
	@Test
	public void souhlasiSNezavislymVypocetemPROJ() {
		shoda(50.0875, 14.4213, -742_805.85, -1_043_009.51, 1.0);
		shoda(49.1951, 16.6068, -598_248.78, -1_160_744.71, 1.0);
		shoda(50.186295, 15.041156, -697_431.08, -1_037_976.77, 1.0);
	}

	@Test
	public void bodOdVedouciho() {
		final double[] wgs = SJtsk.naWgs(-697_471.57, -1_037_986.22);
		assertEquals(50.186164, wgs[0], 1e-6);
		assertEquals(15.040611, wgs[1], 1e-6);
		final SJtsk.Bod zpet = SJtsk.zWgs(wgs[0], wgs[1]);
		assertEquals(-697_471.57, zpet.y, 0.01);
		assertEquals(-1_037_986.22, zpet.x, 0.01);
	}

	@Test
	public void kruhovyPrevodNaCeleRepublice() {
		for (double lat = 48.6; lat <= 51.0; lat += 0.3) {
			for (double lon = 12.2; lon <= 18.8; lon += 0.5) {
				final SJtsk.Bod b = SJtsk.zWgs(lat, lon);
				final double[] zpet = SJtsk.naWgs(b.y, b.x);
				assertEquals(lat, zpet[0], 1e-7);
				assertEquals(lon, zpet[1], 1e-7);
			}
		}
	}
}
