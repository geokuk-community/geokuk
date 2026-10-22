package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;

import org.junit.Assert;
import org.junit.Test;

/** Krytí zapečené do obrázku vypadá stejně jako kreslení s průhledností při každém překreslení. */
public class JKachleZapecKrytiTest {

	private static BufferedImage zdroj() {
		final BufferedImage bi = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				bi.setRGB(x, y, new Color(x * 30, y * 30, 200, x == 7 ? 128 : 255).getRGB());
			}
		}
		return bi;
	}

	private static BufferedImage naBilem(final Image obrazek, final float kryti) {
		final BufferedImage out = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = out.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 8, 8);
		if (kryti < 1) {
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, kryti));
		}
		g.drawImage(obrazek, 0, 0, null);
		g.dispose();
		return out;
	}

	@Test
	public void zapecenePrekrytiSeShodujeSKreslenimSKrytim() {
		final BufferedImage puvodni = zdroj();
		final BufferedImage stareKresleni = naBilem(puvodni, 0.4f);
		final BufferedImage zapecene = naBilem(JKachle.zapecKryti(puvodni, 0.4f), 1);
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				final Color a = new Color(stareKresleni.getRGB(x, y));
				final Color b = new Color(zapecene.getRGB(x, y));
				Assert.assertTrue(x + "," + y, Math.abs(a.getRed() - b.getRed()) <= 2 && Math.abs(a.getGreen() - b.getGreen()) <= 2 && Math.abs(a.getBlue() - b.getBlue()) <= 2);
			}
		}
	}

	@Test
	public void plneKrytiObrazekNemeni() {
		final BufferedImage puvodni = zdroj();
		Assert.assertSame(puvodni, JKachle.zapecKryti(puvodni, 1f));
		Assert.assertNull(JKachle.zapecKryti(null, 0.4f));
	}

	/** Reliéf ČÚZK se na mapě kreslí s krytím 0,4: nesmí být ani plně neprůhledný, ani zprůhledněný dvakrát. */
	@Test
	public void dlazdiceReliefuSeKresliSeKrytim() {
		final cz.geokuk.plugins.mapy.kachle.data.Ka ka = new cz.geokuk.plugins.mapy.kachle.data.Ka(
				cz.geokuk.plugins.mapy.kachle.data.KaLoc.ofJZ(new cz.geokuk.core.coordinates.Mou(0x23800000, 0x28100000), 13), cz.geokuk.plugins.mapy.kachle.data.EKaType.RELIEF_CUZK);
		final JKachle kachle = new JKachle(null, ka);
		final BufferedImage cervena = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D gc = cervena.createGraphics();
		gc.setColor(Color.RED);
		gc.fillRect(0, 0, 256, 256);
		gc.dispose();
		kachle.prijmi(new cz.geokuk.plugins.mapy.kachle.podklady.KachloStav(cervena), cz.geokuk.plugins.mapy.kachle.podklady.Priority.KACHLE);
		kachle.setSize(256, 256);
		final BufferedImage out = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = out.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 256, 256);
		kachle.paint(g);
		g.dispose();
		final Color px = new Color(out.getRGB(100, 100));
		Assert.assertEquals(255, px.getRed());
		// červená s krytím 0,4 přes bílou: zelený a modrý kanál 255 * 0,6 = 153
		Assert.assertEquals("zelený kanál " + px.getGreen(), 153, px.getGreen(), 3);
		Assert.assertEquals("modrý kanál " + px.getBlue(), 153, px.getBlue(), 3);
	}
}
