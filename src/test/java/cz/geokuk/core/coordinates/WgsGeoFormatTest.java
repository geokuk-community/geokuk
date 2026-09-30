package cz.geokuk.core.coordinates;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WgsGeoFormatTest {

	@Test
	public void beznaHodnota() {
		assertEquals("50°04.974", Wgs.toGeoFormat(50 + 4.974 / 60));
	}

	@Test
	public void minutySeZaokrouhliNaCelyStupen() {
		// 59,9999994 minuty se zaokrouhlí na 60,000 – musí se přenést do stupňů.
		assertEquals("50°00.000", Wgs.toGeoFormat(49.99999999));
	}

	@Test
	public void celyStupen() {
		assertEquals("N50°00.000 E14°00.000", new Wgs(50, 14).toString());
	}
}
