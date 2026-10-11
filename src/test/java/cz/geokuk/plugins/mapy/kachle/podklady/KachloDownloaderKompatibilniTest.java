package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/** Dlaždice z PNG s paletou i z JPEG jsou po načtení ve formátu obrazovky a se stejnými barvami. */
public class KachloDownloaderKompatibilniTest {

	private GraphicsConfiguration gc;

	@Before
	public void obrazovka() {
		Assume.assumeFalse("bez displeje se nepřevádí", GraphicsEnvironment.isHeadless());
		gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
	}

	private static byte[] zakoduj(final BufferedImage img, final String format) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		Assert.assertTrue(ImageIO.write(img, format, out));
		return out.toByteArray();
	}

	private static BufferedImage vzor(final int typ) {
		final BufferedImage img = new BufferedImage(256, 256, typ);
		final Graphics2D g = img.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 256, 256);
		g.setColor(Color.RED);
		g.fillRect(10, 20, 100, 50);
		g.dispose();
		return img;
	}

	private void overKompatibilni(final BufferedImage img) {
		Assert.assertEquals(gc.getColorModel(img.getTransparency()), img.getColorModel());
		Assert.assertEquals(256, img.getWidth());
		Assert.assertEquals(256, img.getHeight());
	}

	@Test
	public void pngSPaletou() throws Exception {
		final BufferedImage nactena = (BufferedImage) KachloDownloader.precti(new ByteArrayInputStream(zakoduj(vzor(BufferedImage.TYPE_BYTE_INDEXED), "png")));
		overKompatibilni(nactena);
		Assert.assertEquals(Color.RED.getRGB(), nactena.getRGB(50, 40));
		Assert.assertEquals(Color.WHITE.getRGB(), nactena.getRGB(200, 200));
	}

	@Test
	public void jpeg() throws Exception {
		final BufferedImage nactena = (BufferedImage) KachloDownloader.precti(new ByteArrayInputStream(zakoduj(vzor(BufferedImage.TYPE_3BYTE_BGR), "jpg")));
		overKompatibilni(nactena);
		final Color c = new Color(nactena.getRGB(50, 40));
		Assert.assertTrue(c.toString(), c.getRed() > 200 && c.getGreen() < 60 && c.getBlue() < 60);
	}

	@Test
	public void kompatibilniSeNekopiruje() {
		final BufferedImage img = gc.createCompatibleImage(256, 256, Transparency.TRANSLUCENT);
		Assert.assertSame(img, KachloDownloader.kompatibilni(img));
	}
}
