package cz.geokuk.util.file;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.mvc.KesoidUmisteniSouboru;

public class FilexTest {

	@Test
	public void lisiSeAktivitou() {
		final Filex neaktivni = new Filex(new File("C:\\geoget\\data"), false, false);
		final Filex aktivni = new Filex(new File("C:\\geoget\\data"), false, true);
		Assert.assertNotEquals(neaktivni, aktivni);
		Assert.assertEquals(aktivni, new Filex(new File("C:\\geoget\\data"), false, true));
		Assert.assertEquals(aktivni.hashCode(), new Filex(new File("C:\\geoget\\data"), false, true).hashCode());
	}

	@Test
	public void zmenaAktivityJeZmenaUmisteni() {
		final KesoidUmisteniSouboru puvodni = new KesoidUmisteniSouboru();
		puvodni.setGeogetDataDir(new Filex(new File("C:\\geoget\\data"), false, false));
		final KesoidUmisteniSouboru nove = new KesoidUmisteniSouboru();
		nove.setGeogetDataDir(new Filex(new File("C:\\geoget\\data"), false, true));
		Assert.assertNotEquals(puvodni, nove);
		Assert.assertFalse(nove.equalsDataLocations(puvodni));
	}
}
