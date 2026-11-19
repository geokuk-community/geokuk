package cz.geokuk.plugins.challenge;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/** Poloha štítku kandidáta vůči ikoně, spodnímu okraji okna a překážkám (měřítko). */
public class PolohaStitkuKandidataTest {

	private static final Dimension STITEK = new Dimension(30, 16);

	@Test
	public void uprostredMapyStojiVlevoOdIkony() {
		final Point p = JPlanovacNadKeskami.polohaStitkuKandidata(500, 300, 8, 0, -8, 2, STITEK, 1000, 600, Collections.emptyList());
		Assert.assertEquals(new Point(500 + 8 - 2 - 30, 300 - 8), p);
	}

	@Test
	public void uSpodnihoOkrajeStojiNadIkonou() {
		final Point p = JPlanovacNadKeskami.polohaStitkuKandidata(500, 595, 8, 0, -8, 2, STITEK, 1000, 600, Collections.emptyList());
		Assert.assertEquals(new Point(500 + 8 - 2 - 30, 595 - 8 - 2 - 16), p);
		Assert.assertTrue(p.y + STITEK.height <= 600);
	}

	@Test
	public void uMeritkaStojiNadIkonouMimoPrekazku() {
		final List<Rectangle> prekazky = Collections.singletonList(new Rectangle(0, 580, 300, 20));
		final Point p = JPlanovacNadKeskami.polohaStitkuKandidata(100, 575, 8, 0, -8, 2, STITEK, 1000, 600, prekazky);
		Assert.assertFalse(prekazky.get(0).intersects(new Rectangle(p.x, p.y, STITEK.width, STITEK.height)));
		Assert.assertEquals("zůstává vlevo od ikony", 100 + 8 - 2 - 30, p.x);
	}
}
