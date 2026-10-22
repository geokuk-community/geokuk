package cz.geokuk.api.mapicon;

import java.awt.image.BufferedImage;

import org.junit.Assert;
import org.junit.Test;

/** Zvětšená podoba ikony se spočítá jednou pro dané měřítko. */
public class ImagantZvetsenyTest {

	private static Imagant ikona() {
		final BufferedImage bi = new BufferedImage(10, 8, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 10; x++) {
			for (int y = 0; y < 8; y++) {
				bi.setRGB(x, y, 0xFFFF0000);
			}
		}
		return new Imagant(bi, 2, -1);
	}

	@Test
	public void stejneMeritkoDaStejnyObrazek() {
		final Imagant i = ikona();
		Assert.assertSame(i.zvetseny(1.5), i.zvetseny(1.5));
		Assert.assertNotSame(i.zvetseny(1.5), i.zvetseny(2));
		Assert.assertSame(i, i.zvetseny(1));
	}

	@Test
	public void velikostAPoziceSeZvetsi() {
		final Imagant i = ikona();
		final Imagant z = i.zvetseny(1.5);
		Assert.assertEquals(15, z.getImage().getWidth());
		Assert.assertEquals(12, z.getImage().getHeight());
		Assert.assertEquals(BufferedImage.TYPE_INT_ARGB_PRE, z.getImage().getType());
		Assert.assertEquals(Math.round(i.getXpos() * 1.5), z.getXpos());
		Assert.assertEquals(Math.round(i.getYpos() * 1.5), z.getYpos());
		Assert.assertEquals(0xFFFF0000, z.getImage().getRGB(7, 6));
	}
}
