package org.mapsforge.map.awt.graphics;

import org.junit.Assert;
import org.junit.Test;
import org.mapsforge.core.graphics.*;

/** Zapamatovaná šířka textu je stejná jako změřená a po změně písma se měří znovu. */
public class AwtPaintSPametiTest {

	private static final String TEXT = "Náměstí Míru – Žižkov";

	private static Paint pismo(final Paint p, final float velikost) {
		p.setTypeface(FontFamily.SANS_SERIF, FontStyle.BOLD);
		p.setTextSize(velikost);
		return p;
	}

	@Test
	public void sirkaJakoBezPameti() {
		final Paint s = pismo(new AwtPaintSPameti(), 14);
		final Paint bez = pismo(new AwtPaint(), 14);
		Assert.assertEquals(bez.getTextWidth(TEXT), s.getTextWidth(TEXT));
		Assert.assertEquals(bez.getTextWidth(TEXT), s.getTextWidth(TEXT));
		Assert.assertEquals(bez.getTextWidth("Vaduz"), s.getTextWidth("Vaduz"));
	}

	@Test
	public void poZmeneVelikostiNovaSirka() {
		final Paint s = pismo(new AwtPaintSPameti(), 10);
		final int mala = s.getTextWidth(TEXT);
		s.setTextSize(30);
		final int velka = s.getTextWidth(TEXT);
		Assert.assertTrue(mala + " < " + velka, velka > 2 * mala);
		Assert.assertEquals(pismo(new AwtPaint(), 30).getTextWidth(TEXT), velka);
	}

	@Test
	public void kopieMereSvymPismem() {
		final Paint puvodni = pismo(new AwtPaintSPameti(), 10);
		puvodni.getTextWidth(TEXT);
		final Paint kopie = new AwtPaintSPameti(puvodni);
		kopie.setTextSize(30);
		Assert.assertEquals(pismo(new AwtPaint(), 30).getTextWidth(TEXT), kopie.getTextWidth(TEXT));
		Assert.assertEquals(pismo(new AwtPaint(), 10).getTextWidth(TEXT), puvodni.getTextWidth(TEXT));
	}
}
