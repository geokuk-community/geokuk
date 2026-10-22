package cz.geokuk.api.mapicon;

import java.util.Deque;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.mapicon.IkonDrawingProperties;

/** Nesmyslná hodnota v nastavení uživatelské sady ikon se nahradí výchozí, kreslení keší nespadne. */
public class Drawer0NeplatneTest {

	private static final class Kreslic extends Drawer0 {
		@Override
		public void draw(final Deque<Imagant> imaganti) {}
	}

	@Test
	public void neplatnaCislaDajiVychozi() {
		final IkonDrawingProperties idp = new IkonDrawingProperties();
		idp.properties = new Properties();
		idp.properties.setProperty("sirka", "abc");
		idp.properties.setProperty("tloustka", "1,5");
		idp.properties.setProperty("barva", "10,x,30");
		idp.properties.setProperty("posuny", "1.5,y");
		idp.properties.setProperty("dobre", " 7 ");
		final Kreslic k = new Kreslic();
		k.setIdp(idp);
		Assert.assertEquals(3, k.getInt("sirka", 3));
		Assert.assertEquals(2f, k.getFloat("tloustka", 2f), 0f);
		Assert.assertArrayEquals(new int[] { 1, 2, 3 }, k.getInts("barva", new int[] { 1, 2, 3 }));
		Assert.assertArrayEquals(new float[] { 4f }, k.getFloats("posuny", new float[] { 4f }), 0f);
		Assert.assertEquals(7, k.getInt("dobre", 0));
	}
}
