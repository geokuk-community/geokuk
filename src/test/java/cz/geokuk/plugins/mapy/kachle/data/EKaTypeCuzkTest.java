package cz.geokuk.plugins.mapy.kachle.data;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;

/** Podklady ČÚZK: dlaždice v pořadí z/y/x, území jen ČR, atribuce a stálá jména. */
public class EKaTypeCuzkTest {

	/** Staroměstské náměstí v Praze, dlaždice z16 x 35393, y 22201. */
	private static final Wgs PRAHA = new Wgs(50.0875, 14.4208);

	@Test
	public void adresaDlazdiceVPoradiZYX() throws Exception {
		Assert.assertEquals("https://ags.cuzk.gov.cz/arcgis1/rest/services/ORTOFOTO_WM/MapServer/tile/16/22201/35393",
				new Ka(KaLoc.ofJZ(PRAHA.toMou(), 16), EKaType.CUZK_ORTO).getUrl().toExternalForm());
		Assert.assertEquals("https://ags.cuzk.gov.cz/arcgis1/rest/services/ZTM_WM/MapServer/tile/16/22201/35393",
				new Ka(KaLoc.ofJZ(PRAHA.toMou(), 16), EKaType.CUZK_ZTM).getUrl().toExternalForm());
	}

	@Test
	public void meritkaUzemiAtribuce() {
		Assert.assertEquals(6, EKaType.CUZK_ORTO.getMinMoumer());
		Assert.assertEquals(20, EKaType.CUZK_ORTO.getMaxMoumer());
		Assert.assertEquals(6, EKaType.CUZK_ZTM.getMinMoumer());
		Assert.assertEquals(19, EKaType.CUZK_ZTM.getMaxMoumer());
		Assert.assertTrue(EKaType.CUZK_ORTO.isOmezeneUzemi());
		Assert.assertTrue(EKaType.CUZK_ZTM.isOmezeneUzemi());
		Assert.assertFalse(EKaType.OPEN_STREET.isOmezeneUzemi());
		Assert.assertEquals("© ČÚZK, CC BY 4.0", EKaType.CUZK_ORTO.getAtribuce());
		Assert.assertEquals("© ČÚZK, CC BY 4.0", EKaType.CUZK_ZTM.getAtribuce());
	}

	@Test
	public void staleJmenoAMenuBezZkratky() {
		Assert.assertSame(EKaType.CUZK_ORTO, EKaType.podleJmena("CUZK_ORTO"));
		Assert.assertSame(EKaType.CUZK_ZTM, EKaType.podleJmena("CUZK_ZTM"));
		Assert.assertNull(EKaType.CUZK_ORTO.getKeyStroke());
		Assert.assertNull(EKaType.CUZK_ZTM.getKeyStroke());
	}
}
