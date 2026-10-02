package cz.geokuk.plugins.kesoid.kind.kes;

import static org.junit.Assert.*;

import org.junit.Test;

public class EKesWptTypeTest {

	@Test
	public void dekodujeJmenaZGpx() {
		assertEquals(EKesWptType.FINAL_LOCATION, EKesWptType.decode("Final Location"));
		assertEquals(EKesWptType.STAGES_OF_A_MULTICACHE, EKesWptType.decode("Stages of a Multicache"));
		assertEquals(EKesWptType.PARKING_AREA, EKesWptType.decode("parking-area"));
		assertEquals(EKesWptType.FINAL_LOCATION, EKesWptType.decode("Final Location"));
	}

	@Test
	public void neznamyTypJeNull() {
		assertNull(EKesWptType.decode("Geocache"));
		assertNull(EKesWptType.decode("Geocache"));
		assertNull(EKesWptType.decode(""));
		assertNull(EKesWptType.decode(null));
	}
}
