package cz.geokuk.plugins.kesoidpopisky;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.font.FontRenderContext;
import java.util.HashMap;
import java.util.Map;

/** Šířky textů popisků pro jedno písmo a měřítko; při změně písma nebo měřítka se zahodí, při přeplnění se vyprázdní. */
class SirkaTextuCache {

	static final int MAX_POLOZEK = 5000;

	private final Map<String, Integer> sirky = new HashMap<>();
	private Font font;
	private FontRenderContext kontext;

	int sirka(final FontMetrics fm, final String text) {
		if (!fm.getFont().equals(font) || !fm.getFontRenderContext().equals(kontext) || sirky.size() >= MAX_POLOZEK) {
			sirky.clear();
			font = fm.getFont();
			kontext = fm.getFontRenderContext();
		}
		return sirky.computeIfAbsent(text, fm::stringWidth);
	}
}
