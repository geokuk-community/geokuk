package cz.geokuk.plugins.kesoid;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;

import org.junit.Test;

public class GoldenSnimekTest {

	private static BufferedImage obrazek(final int barva) {
		final BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 100; y++) {
			for (int x = 0; x < 100; x++) {
				img.setRGB(x, y, barva);
			}
		}
		return img;
	}

	@Test
	public void malyRozdilBarvyProjde() {
		assertNull(GoldenSnimek.rozdil(obrazek(0xff808080), obrazek(0xff888888)));
	}

	@Test
	public void velkyRozdilBarvyNeprojde() {
		assertNotNull(GoldenSnimek.rozdil(obrazek(0xff808080), obrazek(0xff809080)));
	}

	@Test
	public void jedenJinyPixelProjdeDvaNe() {
		final BufferedImage jeden = obrazek(0);
		jeden.setRGB(5, 5, 0xffff0000);
		assertNull(GoldenSnimek.rozdil(obrazek(0), jeden));
		jeden.setRGB(6, 6, 0xffff0000);
		assertNotNull(GoldenSnimek.rozdil(obrazek(0), jeden));
	}

	@Test
	public void jinyRozmerNeprojde() {
		assertNotNull(GoldenSnimek.rozdil(obrazek(0), new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)));
	}
}
