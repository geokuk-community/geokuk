package cz.geokuk.plugins.kesoidobsazenost;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.kesoid.LimityKresleni;

/** Obsazenost se nekreslí, když je keší příliš mnoho nebo by se kruhy jen slévaly. */
public class JObsazenostLimitTest {

	private static JObsazenost slide() {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		final JObsazenost s = new JObsazenost();
		s.setSoord(new Coord(12, new Wgs(50, 15).toMou(), new Dimension(800, 600), 0));
		return s;
	}

	@Test
	public void maloKesiSeKresli() {
		Assert.assertFalse(slide().prekrocenLimit(100, 10));
	}

	@Test
	public void viceNezLimitIkonSeNekresli() {
		Assert.assertTrue(slide().prekrocenLimit(LimityKresleni.VYCHOZI_IKON + 1, 3));
	}

	@Test
	public void prilisHustaSkupinaSeNekresli() {
		// 10 000 kruhů o poloměru 40 px pokryje 800×600 mnohonásobně
		Assert.assertTrue(slide().prekrocenLimit(10_000, 40));
	}
}
