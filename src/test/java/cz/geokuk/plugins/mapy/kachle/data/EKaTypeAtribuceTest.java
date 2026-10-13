package cz.geokuk.plugins.mapy.kachle.data;

import org.junit.Assert;
import org.junit.Test;

public class EKaTypeAtribuceTest {

	@Test
	public void kazdaVrstvaMaAtribuci() {
		for (final EKaType ka : EKaType.values()) {
			// basemap.at předepisuje znění „Data source: basemap.at“.
			Assert.assertTrue(ka.name(), ka.getAtribuce().contains("©") || ka == EKaType.AT_BASEMAP && ka.getAtribuce().equals("Data source: basemap.at"));
		}
	}

	@Test
	public void vrstvyZOpenStreetMapUvadejiOsm() {
		for (final EKaType ka : new EKaType[] { EKaType.OPEN_STREET, EKaType.TUR_FREEMAP_SK_T }) {
			Assert.assertTrue(ka.name(), ka.getAtribuce().contains("OpenStreetMap"));
		}
		Assert.assertTrue(EKaType.TURIST_M.getAtribuce().contains("Seznam.cz"));
	}
}
