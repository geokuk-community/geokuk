package cz.geokuk.core.coord;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Moud;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.util.index2d.BoundingRect;

/** Převody mezi body obrazovky a souřadnicemi a přibližování ke kurzoru. */
public class CoordTest {

	private static final Dimension OKNO = new Dimension(800, 600);
	private static final Mou PRAHA = new Wgs(50.0755, 14.4378).toMou();

	@Test
	public void prevodDoPrepouzitehoBoduDaStejnyVysledek() {
		final Coord c = new Coord(12, PRAHA, OKNO, 0);
		final Point bod = new Point(-1, -1);
		for (final Mou mou : new Mou[] { PRAHA, new Wgs(50.1, 14.5).toMou(), new Wgs(49.9, 14.3).toMou() }) {
			Assert.assertEquals(c.transform(mou), c.transform(mou, bod));
			Assert.assertEquals(c.transform(mou), bod);
		}
	}

	@Test
	public void rozmerVyrezuPriOdzoomuNepretece() {
		// při měřítku 0 je okno širší než celý svět; v intu vyšel rozměr menší, nebo dokonce prázdný
		final Dimension okno = new Dimension(2560, 1440);
		final Coord c = new Coord(0, PRAHA, okno, 0);

		final Moud rozmer = c.getMouSize();
		Assert.assertEquals((long) okno.width << Coord.MAX_MOUMER, rozmer.dxx);
		Assert.assertEquals((long) okno.height << Coord.MAX_MOUMER, rozmer.dyy);
		Assert.assertFalse(rozmer.isAnyRozmerEmpty());
	}

	@Test
	public void posunPresCelouObrazovkuPriOdzoomuNepretece() {
		// tažení mapy o celou šířku okna je při měřítku 0 posun o víc než celý svět
		final Coord c = new Coord(0, PRAHA, new Dimension(2560, 1440), 0);

		Assert.assertEquals(2560L << Coord.MAX_MOUMER, c.transformPoindDiff(2560));
		Assert.assertEquals(-(1440L << Coord.MAX_MOUMER), c.transformShift(0, 1440).dyy);
	}

	@Test
	public void priOdzoomuNaCelySvetJeVyrezCelySvet() {
		// při malém měřítku se svět v okně opakuje; výřez se nesmí přetočit na skoro prázdný,
		// jinak počítaný výřez nic neobsahuje, kreslený je celý svět a vykreslí se všechny keše
		final Dimension okno = new Dimension(2560, 1080);
		final Coord c = new Coord(0, PRAHA, okno, 0);

		final BoundingRect pocitany = c.getBoundingRect();
		Assert.assertEquals(BoundingRect.ALL.xx1, pocitany.xx1);
		Assert.assertEquals(BoundingRect.ALL.xx2, pocitany.xx2);
		Assert.assertEquals(BoundingRect.ALL.yy1, pocitany.yy1);
		Assert.assertEquals(BoundingRect.ALL.yy2, pocitany.yy2);

		// tak, jak se ptá vykreslování: okno zvětšené o okraje ikon
		final BoundingRect kresleny = c.transforToBounding(new Rectangle(-20, -20, okno.width + 40, okno.height + 40));
		Assert.assertEquals(pocitany.xx1, kresleny.xx1);
		Assert.assertEquals(pocitany.xx2, kresleny.xx2);
		Assert.assertEquals(pocitany.yy1, kresleny.yy1);
		Assert.assertEquals(pocitany.yy2, kresleny.yy2);
	}

	@Test
	public void bodTamAZpet() {
		final Coord c = new Coord(12, new Wgs(50, 15).toMou(), OKNO, 0);
		final Point p1 = new Point(200, 225);
		final Point p2 = c.transform(c.transform(p1));
		Assert.assertEquals(p1, p2);
	}

	@Test
	public void stredJeUprostredOkna() {
		final Coord c = new Coord(14, PRAHA, OKNO, 0);
		final Point p = c.transform(PRAHA);
		Assert.assertEquals(400, p.x, 1);
		Assert.assertEquals(300, p.y, 1);
	}

	@Test
	public void severJeNahore() {
		final Coord c = new Coord(14, PRAHA, OKNO, 0);
		final Point stred = c.transform(PRAHA);
		final Point sever = c.transform(new Wgs(50.08, 14.4378).toMou());
		final Point vychod = c.transform(new Wgs(50.0755, 14.45).toMou());
		Assert.assertTrue(sever.y < stred.y);
		Assert.assertTrue(vychod.x > stred.x);
	}

	@Test
	public void priblizeniKeKurzoruNechaBodNaMiste() {
		for (final int z : new int[] { 5, 8, 12, 16 }) {
			final Coord c = new Coord(z, PRAHA, OKNO, 0);
			for (final Point kurzor : new Point[] { new Point(0, 0), new Point(123, 456), new Point(799, 599), new Point(400, 300) }) {
				final Mou podKurzorem = c.transform(new Point(kurzor));
				for (final int novy : new int[] { z - 1, z + 1, z + 3 }) {
					final Coord c2 = c.derive(novy, c.computeZoom(novy, podKurzorem));
					final Point p = c2.transform(podKurzorem);
					Assert.assertEquals("z" + z + "→" + novy + " " + kurzor, kurzor.x, p.x, 1);
					Assert.assertEquals("z" + z + "→" + novy + " " + kurzor, kurzor.y, p.y, 1);
				}
			}
		}
	}

	@Test
	public void priblizeniBezKurzoruDrziStred() {
		final Coord c = new Coord(12, PRAHA, OKNO, 0);
		final Mou stred = c.computeZoom(14, null);
		Assert.assertEquals(PRAHA.xx, stred.xx, 1 << (Coord.MAX_MOUMER - 12));
		Assert.assertEquals(PRAHA.yy, stred.yy, 1 << (Coord.MAX_MOUMER - 12));
	}

	@Test
	public void natocenaMapaTamAZpet() {
		final Coord c = new Coord(14, PRAHA, OKNO, 0.3);
		for (final Point p : new Point[] { new Point(0, 0), new Point(123, 456), new Point(799, 599) }) {
			final Point zpet = c.transform(c.transform(new Point(p)));
			Assert.assertEquals(p.x, zpet.x, 1);
			Assert.assertEquals(p.y, zpet.y, 1);
		}
	}

	@Test
	public void prevodNemeniPredanyBod() {
		final Coord c = new Coord(14, PRAHA, OKNO, 0.3);
		final Point p = new Point(123, 456);
		c.transform(p);
		Assert.assertEquals(new Point(123, 456), p);
	}

	@Test
	public void priblizeniNatoceneMapyKeKurzoru() {
		final Coord c = new Coord(12, PRAHA, OKNO, 0.3);
		final Point kurzor = new Point(123, 456);
		final Mou podKurzorem = c.transform(new Point(kurzor));
		final Coord c2 = c.derive(13, c.computeZoom(13, podKurzorem));
		final Point p = c2.transform(podKurzorem);
		Assert.assertEquals(kurzor.x, p.x, 2);
		Assert.assertEquals(kurzor.y, p.y, 2);
	}
}
