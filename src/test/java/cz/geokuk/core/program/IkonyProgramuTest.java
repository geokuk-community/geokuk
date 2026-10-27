package cz.geokuk.core.program;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.Test;

/** Ikony okna a logo načítání jsou v programu ve všech velikostech, které okno nastavuje; chybějící obrázek by se tiše nahradil. */
public class IkonyProgramuTest {

	private static BufferedImage nacti(final String zdroj) throws IOException {
		try (InputStream in = IkonyProgramuTest.class.getResourceAsStream(zdroj)) {
			Assert.assertNotNull(zdroj, in);
			return ImageIO.read(in);
		}
	}

	@Test
	public void ikonyOknaMajiSvouVelikost() throws IOException {
		for (final int v : new int[] { 16, 32, 48, 64, 128, 256 }) {
			final BufferedImage img = nacti("/img/geokuk" + v + ".png");
			Assert.assertEquals(v, img.getWidth());
			Assert.assertEquals(v, img.getHeight());
		}
	}

	@Test
	public void logoNacitaniMaCtyricetNaCtyricet() throws IOException {
		final BufferedImage img = nacti("/img/geokuk-logo.png");
		Assert.assertEquals(400, img.getWidth());
		Assert.assertEquals(400, img.getHeight());
	}
}
