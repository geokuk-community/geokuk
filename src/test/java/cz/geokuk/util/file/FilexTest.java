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

	/** Relativní cesta (třeba data\gpx) se počítá od složky GeoKuk, ve které jsou složky program a data. */
	@Test
	public void relativniCestaOdSlozkyGeokuk() {
		final File koren = new File("/tmp/GeoKuk").getAbsoluteFile();
		Assert.assertEquals(Filex.canonize(new File(koren, "data/gpx")), Filex.efektivni(new File("data/gpx"), koren));
		Assert.assertEquals(Filex.canonize(new File(koren, "data/gpx")), Filex.efektivni(new File("program/../data/gpx"), koren));
	}

	/** Beta 9 až 13: „..\data\gpx“ relativně ke složce program míří dál do data\gpx, uloží se pak přenosně. */
	@Test
	public void relativniKProgramuZeStarsiVerze() {
		final File koren = new File("/tmp/GeoKuk").getAbsoluteFile();
		final Filex f = Filex.zNastaveni(new File("../data/gpx"), true, true, new File(koren, "program"));
		Assert.assertEquals(Filex.canonize(new File(koren, "data/gpx")), f.getFile());
		Assert.assertFalse(f.isRelativeToProgram());
		Assert.assertEquals(new File("data/gpx"), Filex.zNastaveni(new File("data/gpx"), false, true, new File(koren, "program")).getFile());
	}
}
