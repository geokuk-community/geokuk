package cz.geokuk.plugins.kesoidpopisky;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;

import org.junit.Assert;
import org.junit.Test;

/** Šířka stejného textu se pro stejné písmo a měřítko měří jednou. */
public class SirkaTextuCacheTest {

	private static class Pocitadlo extends FontMetrics {
		private static final long serialVersionUID = 1L;
		int mereni;
		private final FontRenderContext kontext;

		Pocitadlo(final Font font, final double meritko) {
			super(font);
			kontext = new FontRenderContext(AffineTransform.getScaleInstance(meritko, meritko), true, true);
		}

		@Override
		public int stringWidth(final String str) {
			mereni++;
			return str.length() * 7;
		}

		@Override
		public FontRenderContext getFontRenderContext() {
			return kontext;
		}
	}

	private static final Font PISMO = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

	@Test
	public void stejnyTextSeMeriJednou() {
		final SirkaTextuCache cache = new SirkaTextuCache();
		final Pocitadlo fm = new Pocitadlo(PISMO, 1);
		for (int i = 0; i < 100; i++) {
			Assert.assertEquals(21, cache.sirka(fm, "Kuk"));
		}
		Assert.assertEquals(1, fm.mereni);
		Assert.assertEquals(35, cache.sirka(fm, "Kuk 2"));
		Assert.assertEquals(2, fm.mereni);
	}

	@Test
	public void zmenaPismaNeboMeritkaMeriZnovu() {
		final SirkaTextuCache cache = new SirkaTextuCache();
		final Pocitadlo fm = new Pocitadlo(PISMO, 1);
		cache.sirka(fm, "Kuk");
		final Pocitadlo vetsi = new Pocitadlo(PISMO.deriveFont(20f), 1);
		cache.sirka(vetsi, "Kuk");
		Assert.assertEquals(1, vetsi.mereni);
		final Pocitadlo dvojnasobne = new Pocitadlo(PISMO.deriveFont(20f), 2);
		cache.sirka(dvojnasobne, "Kuk");
		Assert.assertEquals(1, dvojnasobne.mereni);
	}

	@Test
	public void cachePrelnenaSeVyprazdni() {
		final SirkaTextuCache cache = new SirkaTextuCache();
		final Pocitadlo fm = new Pocitadlo(PISMO, 1);
		for (int i = 0; i < SirkaTextuCache.MAX_POLOZEK + 10; i++) {
			cache.sirka(fm, "t" + i);
		}
		final int pred = fm.mereni;
		cache.sirka(fm, "t" + (SirkaTextuCache.MAX_POLOZEK + 9));
		Assert.assertEquals("poslední text je po vyprázdnění v cachi", pred, fm.mereni);
	}
}
