package cz.geokuk.plugins.cesty;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.*;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.plugins.cesty.routovani.Trasa;

/** Úsek po cestě se kreslí po trase, ne rovnou spojnicí bodů. */
public class MalovadloPoCesteTest {

	private static final Wgs A = new Wgs(50.00, 14.00);
	private static final Wgs B = new Wgs(50.01, 14.00);
	/** Bod trasy daleko stranou od spojnice A–B. */
	private static final Wgs X = new Wgs(50.005, 14.008);

	private static BufferedImage nakresli(final boolean sTrasou) {
		final Updator updator = new Updator();
		final Doc doc = new Doc();
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		updator.pridejNaKonec(cesta, A.toMou());
		updator.pridejNaKonec(cesta, B.toMou());
		final Usek usek = cesta.getUseky().iterator().next();
		updator.setTyp(usek, TypUseku.PO_CESTE);
		if (sTrasou) {
			updator.setTrasa(usek, usek.getBvzad().getMou(), usek.getBvpred().getMou(), Trasa.nalezena(Arrays.asList(A, X, B)));
		}
		final MalovadloParams params = new MalovadloParams();
		params.doc = doc;
		params.curta = cesta;
		params.soord = new Coord(14, new Wgs(50.005, 14.004).toMou(), new Dimension(400, 300), 0);
		final BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = img.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 400, 300);
		new Malovadlo(g, params).paint();
		g.dispose();
		return img;
	}

	private static boolean nakreslenoU(final BufferedImage img, final Wgs w) {
		final Point p = new Coord(14, new Wgs(50.005, 14.004).toMou(), new Dimension(400, 300), 0).transform(w.toMou());
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				if ((img.getRGB(p.x + dx, p.y + dy) & 0xFFFFFF) != 0xFFFFFF) {
					return true;
				}
			}
		}
		return false;
	}

	@Test
	public void kresliSePoTrase() {
		final BufferedImage img = nakresli(true);
		Assert.assertTrue("bod trasy", nakreslenoU(img, X));
		Assert.assertFalse("střed rovné spojnice", nakreslenoU(img, new Wgs(50.005, 14.0)));
	}

	@Test
	public void bezTrasyRovne() {
		final BufferedImage img = nakresli(false);
		Assert.assertFalse(nakreslenoU(img, X));
		Assert.assertTrue(nakreslenoU(img, new Wgs(50.0052, 14.0)));
	}
}
