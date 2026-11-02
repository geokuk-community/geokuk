package cz.geokuk.plugins.kesoidobsazenost;

import java.awt.Dimension;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.kesoid.LimityKresleni;

/** Obsazenost se nekreslí jen tehdy, když je keší ve výřezu víc než limit ikon. */
public class JObsazenostLimitTest {

	private static JObsazenost slide() {
		final JObsazenost s = new JObsazenost();
		s.setSoord(new Coord(12, new Wgs(50, 15).toMou(), new Dimension(800, 600), 0));
		return s;
	}

	@Test
	public void maloKesiSeKresli() {
		Assert.assertFalse(slide().prekrocenLimit(100));
	}

	@Test
	public void viceNezLimitIkonSeNekresli() {
		Assert.assertTrue(slide().prekrocenLimit(LimityKresleni.VYCHOZI_IKON + 1));
	}

	/** Hustě obsazené území je právě to, co má obsazenost ukázat. */
	@Test
	public void hustaSkupinaSeKresli() {
		Assert.assertFalse(slide().prekrocenLimit(10_000));
	}

	/** Limit ikon z nastavení se změnou v nastavení posune. */
	@Test
	public void limitZNastaveniPlatiSamostatne() throws Exception {
		final JObsazenost s = slide();
		final java.lang.reflect.Constructor<cz.geokuk.plugins.kesoid.mvc.LimityKresleniEvent> k = cz.geokuk.plugins.kesoid.mvc.LimityKresleniEvent.class
				.getDeclaredConstructor(LimityKresleni.class);
		k.setAccessible(true);
		final LimityKresleni limity = LimityKresleni.of(LimityKresleni.MIN_IKON, LimityKresleni.VYCHOZI_TECEK);
		s.onEvent(k.newInstance(limity));
		Assert.assertFalse(s.prekrocenLimit(limity.getIkon()));
		Assert.assertTrue(s.prekrocenLimit(limity.getIkon() + 1));
	}
}
