package cz.geokuk.core.coord;

import java.awt.Dimension;

import org.junit.*;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.MouRect;

/** Zoom na obdélník vybere největší měřítko, ve kterém se obdélník vejde do okna. */
public class VyrezModelTest {

	@Test
	public void obdelnikNaPolovinuOknaSePriblizi() {
		// 400×250 px v měřítku 15 se do okna 1050×595 vejde až v měřítku 16
		final int mouNaPixel = 1 << Coord.MAX_MOUMER - 15;
		final MouRect r = new MouRect(new Mou(1000, 1000), new Mou(1000 + 400 * mouNaPixel, 1000 + 250 * mouNaPixel));
		Assert.assertEquals(16, VyrezModel.meritkoProObdelnik(r, new Dimension(1050, 595)));
	}

	@Test
	public void obdelnikNaCeleOknoZustaneVMeritku() {
		final int mouNaPixel = 1 << Coord.MAX_MOUMER - 15;
		final MouRect r = new MouRect(new Mou(1000, 1000), new Mou(1000 + 900 * mouNaPixel, 1000 + 500 * mouNaPixel));
		Assert.assertEquals(15, VyrezModel.meritkoProObdelnik(r, new Dimension(1050, 595)));
	}
}
