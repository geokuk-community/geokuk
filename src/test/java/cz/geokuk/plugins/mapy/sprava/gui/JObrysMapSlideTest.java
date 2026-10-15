package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Path2D;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.PolygonPokryti;

/** Obrys pokrytí se kreslí v pixelech výřezu: uvnitř je území mapy, mimo ne. */
public class JObrysMapSlideTest {

	@Test
	public void obrysVPixelechVyrezu() {
		final Coord soord = new Coord(8, new Wgs(50, 15).toMou(), new Dimension(800, 600), 0);
		final PolygonPokryti cesko = PolygonPokryti.obdelnik(48.55, 12.09, 51.06, 18.86);
		final Path2D cesta = JObrysMapSlide.cesta(soord, cesko);
		final Point praha = soord.transform(new Wgs(50.08, 14.42).toMou());
		final Point viden = soord.transform(new Wgs(48.21, 16.37).toMou());
		Assert.assertTrue(cesta.contains(praha));
		Assert.assertFalse(cesta.contains(viden));
	}
}
