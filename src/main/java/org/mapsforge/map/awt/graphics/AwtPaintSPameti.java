package org.mapsforge.map.awt.graphics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mapsforge.core.graphics.*;

/**
 * Barva a písmo mapsforge, které si pamatuje šířky textů. Popisky se měří opakovaně (každá dlaždice i její okolí) a měření textu s diakritikou je pomalé. Je
 * v balíčku knihovny, protože konstruktory {@link AwtPaint} jinde dostupné nejsou.
 */
public final class AwtPaintSPameti extends AwtPaint {

	private static final int MAX_TEXTU = 2000;

	private volatile Map<String, Integer> sirky = new ConcurrentHashMap<>();

	public AwtPaintSPameti() {
	}

	public AwtPaintSPameti(final Paint paint) {
		super(paint);
	}

	@Override
	public int getTextWidth(final String text) {
		final Map<String, Integer> m = sirky;
		final Integer sirka = m.get(text);
		if (sirka != null) {
			return sirka;
		}
		final int nova = super.getTextWidth(text);
		if (m.size() >= MAX_TEXTU) {
			m.clear();
		}
		m.put(text, nova);
		return nova;
	}

	@Override
	public void setTextSize(final float textSize) {
		super.setTextSize(textSize);
		sirky = new ConcurrentHashMap<>();
	}

	@Override
	public void setTypeface(final FontFamily fontFamily, final FontStyle fontStyle) {
		super.setTypeface(fontFamily, fontStyle);
		sirky = new ConcurrentHashMap<>();
	}
}
