package cz.geokuk.plugins.cesty;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.*;
import cz.geokuk.plugins.cesty.data.*;

/** Vybraná cesta je azurová s tmavým lemem, rovný úsek čárkovaný, vzdušný tečkovaný; purpurová už ne. */
public class MalovadloStylTest {

	private static final Wgs STRED = new Wgs(50.005, 14.01);

	private static Set<Integer> barvy(final boolean vzdusny) {
		final Updator updator = new Updator();
		final Doc doc = new Doc();
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		updator.pridejNaKonec(cesta, new Wgs(50.00, 14.00).toMou());
		updator.pridejNaKonec(cesta, new Wgs(50.01, 14.02).toMou());
		if (vzdusny) {
			updator.setVzdusny(cesta.getUseky().iterator().next(), true);
		}
		final MalovadloParams params = new MalovadloParams();
		params.doc = doc;
		params.curta = cesta;
		params.soord = new Coord(14, STRED.toMou(), new Dimension(400, 300), 0);
		final BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = img.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 400, 300);
		new Malovadlo(g, params).paint();
		g.dispose();
		final Set<Integer> barvy = new HashSet<>();
		for (final int p : img.getRGB(0, 0, 400, 300, null, 0, 400)) {
			barvy.add(p & 0xFFFFFF);
		}
		return barvy;
	}

	@Test
	public void rovnyUsekAzurovySLemem() {
		final Set<Integer> b = barvy(false);
		Assert.assertTrue("azurová", b.contains(FBarvy.CURTA_NORMALNE.getRGB() & 0xFFFFFF));
		Assert.assertTrue("tmavý lem", b.contains(FBarvy.CURTA_LEM.getRGB() & 0xFFFFFF));
		Assert.assertFalse("purpurová", b.contains(0xFF00FF));
	}

	@Test
	public void vzdusnyUsekSLemem() {
		final Set<Integer> b = barvy(true);
		Assert.assertTrue(b.contains(FBarvy.CURTA_LEM.getRGB() & 0xFFFFFF));
		Assert.assertTrue(b.contains(FBarvy.CURTA_NORMALNE.getRGB() & 0xFFFFFF));
	}
}
