package cz.geokuk.plugins.refbody;

import java.io.File;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/** Domácí souřadnice z vadného nebo nedopsaného geohome.ini: žádná špatná poloha, žádná chyba navíc. */
public class GeohomeVadneVstupyTest {

	private static List<RefbodyModel.RefBod> precti(final String vzorek) throws Exception {
		return RefbodyModel.precti(new File(GeohomeVadneVstupyTest.class.getResource("/vadne-vstupy/" + vzorek).toURI()));
	}

	@Test
	public void cp1250SeNacteSDiakritikou() throws Exception {
		final List<RefbodyModel.RefBod> body = precti("geohome-cp1250.ini");
		Assert.assertEquals(2, body.size());
		Assert.assertEquals("Domov Žďár", body.get(0).nazev);
		Assert.assertEquals("Chata Kůň", body.get(1).nazev);
	}

	/** Soubor useknutý uvnitř čísla (GeoGet zrovna zapisuje): nedopsaný bod se vynechá, nevznikne bod se zkrácenou souřadnicí. */
	@Test
	public void zkracenyRadekNedaSpatnouPolohu() throws Exception {
		final List<RefbodyModel.RefBod> body = precti("geohome-zkraceny.ini");
		Assert.assertEquals(1, body.size());
		Assert.assertEquals("Domov Žďár", body.get(0).nazev);
		Assert.assertEquals(49.5625, body.get(0).wgs.lat, 1e-9);
		Assert.assertEquals(15.9392, body.get(0).wgs.lon, 1e-9);
	}

	@Test
	public void prazdnySouborNemaBody() throws Exception {
		Assert.assertTrue(precti("geohome-prazdny.ini").isEmpty());
	}
}
