package cz.geokuk.core.coordinates;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SJtskTest {

	private static void shoda(final double lat, final double lon, final double y, final double x, final double tolerance) {
		final SJtsk.Bod b = SJtsk.zWgs(lat, lon);
		assertEquals("y pro " + lat + "," + lon, y, b.y, tolerance);
		assertEquals("x pro " + lat + "," + lon, x, b.x, tolerance);
	}

	/** Orientační hodnoty známých míst (Praha, Brno) s tolerancí 2 km; hlídá znaménka, osy a řád. */
	@Test
	public void znamaMistaSouhlasiRadove() {
		shoda(50.0875, 14.4213, -742_800, -1_043_000, 2000);
		shoda(49.1951, 16.6068, -598_300, -1_160_700, 2000);
	}

	/** Pevné hodnoty vlastního výpočtu, hlídají neúmyslnou změnu parametrů převodu. */
	@Test
	public void pevneHodnotyPrevodu() {
		shoda(50.0875, 14.4213, -742_845.98, -1_043_022.16, 0.02);
		shoda(49.1951, 16.6068, -598_280.46, -1_160_746.99, 0.02);
	}

	@Test
	public void bodOdVedouciho() {
		final double[] wgs = SJtsk.naWgs(-697_471.57, -1_037_986.22);
		assertEquals(50.186295, wgs[0], 1e-6);
		assertEquals(15.041156, wgs[1], 1e-6);
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
