package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/** Atribuce v mapě: jeden zkrácený řádek vpravo dole s ikonou ⓘ, plné znění se ukáže po kliku na ikonu. Bez závislosti na modelech. */
final class AtribuceMapy {

	static final String OSM = "© OpenStreetMap";
	static final String ELIPSA = "…";
	/** Šířka ikony ⓘ včetně mezery před ní. */
	static final int IKONA = 16;

	private AtribuceMapy() {}

	/** Řádek rozložený do plochy: text a obdélníky celého pole a ikony, v souřadnicích plochy. */
	static final class Rozlozeni {
		final String text;
		final Rectangle pole;
		final Rectangle ikona;

		Rozlozeni(final String text, final Rectangle pole, final Rectangle ikona) {
			this.text = text;
			this.pole = pole;
			this.ikona = ikona;
		}
	}

	/**
	 * Zkrácená podoba atribuce zdroje: ze znění „© A, © B, licence“ jednotlivá jména bez licenčních dovětků; OpenStreetMap vždy jako „© OpenStreetMap“; text bez „©“ zůstává celý.
	 */
	static List<String> zkrat(final String atribuce) {
		final List<String> v = new ArrayList<>();
		if (atribuce == null || atribuce.trim().isEmpty()) {
			return v;
		}
		if (!atribuce.contains("©")) {
			v.add(atribuce.contains("OpenStreetMap") ? OSM : atribuce.trim());
			return v;
		}
		for (final String kus : atribuce.split(",\\s*")) {
			final String k = kus.trim();
			if (k.isEmpty() || k.matches("(?i)(CC BY|a\\.s\\.|dl-de).*")) {
				continue;
			}
			final String jmeno = k.contains("OpenStreetMap") ? OSM : k.replaceAll("\\s*\\(.*$", "").trim();
			if (!v.contains(jmeno)) {
				v.add(jmeno);
			}
		}
		if (v.isEmpty()) {
			v.add(atribuce.trim());
		}
		return v;
	}

	/** Spojí zkrácené zdroje bez duplicit; „© OpenStreetMap“ je vždy první. */
	static List<String> slouc(final List<String> zdroje) {
		final List<String> v = new ArrayList<>();
		for (final String z : zdroje) {
			if (!v.contains(z)) {
				v.add(z);
			}
		}
		if (v.remove(OSM)) {
			v.add(0, OSM);
		}
		return v;
	}

	/**
	 * Rozloží řádek do spodního pravého rohu plochy šířky sirka a výšky vyska; pole není širší než maxSirka. Co se nevejde, se zkrátí od konce
	 * s „…“; první zdroj (OpenStreetMap) zůstane, nejvýš se zkrátí jeho text.
	 */
	static Rozlozeni rozloz(final List<String> zdroje, final FontMetrics fm, final int sirka, final int vyska, final int maxSirka) {
		final int vysokaPole = fm.getHeight() + 2;
		final int textMax = Math.max(0, maxSirka - IKONA - 8);
		final List<String> zbyva = new ArrayList<>(zdroje);
		String text = String.join(" · ", zbyva);
		boolean zkraceno = false;
		while (fm.stringWidth(text) > textMax && zbyva.size() > 1) {
			zbyva.remove(zbyva.size() - 1);
			zkraceno = true;
			text = String.join(" · ", zbyva) + " " + ELIPSA;
		}
		if (fm.stringWidth(text) > textMax) {
			String t = zbyva.isEmpty() ? "" : zbyva.get(0);
			while (!t.isEmpty() && fm.stringWidth(t + ELIPSA) > textMax) {
				t = t.substring(0, t.length() - 1);
			}
			text = t + ELIPSA;
		}
		final int sirkaPole = fm.stringWidth(text) + 8 + IKONA;
		final int x = sirka - sirkaPole;
		final int y = vyska - vysokaPole;
		final Rectangle pole = new Rectangle(x, y, sirkaPole, vysokaPole);
		final Rectangle ikona = new Rectangle(x + sirkaPole - IKONA - 2, y, IKONA + 2, vysokaPole);
		return new Rozlozeni(zkraceno && text.isEmpty() ? ELIPSA : text, pole, ikona);
	}

	static void kresli(final Graphics2D g, final Rozlozeni r, final FontMetrics fm) {
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(255, 255, 255, 170));
		g.fillRect(r.pole.x, r.pole.y, r.pole.width, r.pole.height);
		g.setColor(Color.DARK_GRAY);
		g.drawString(r.text, r.pole.x + 4, r.pole.y + 1 + fm.getAscent());
		final int d = Math.min(r.pole.height - 4, 11);
		final int ix = r.ikona.x + 1;
		final int iy = r.pole.y + (r.pole.height - d) / 2;
		g.drawOval(ix, iy, d, d);
		g.drawString("i", ix + d / 2 - fm.charWidth('i') / 2, iy + d - 2);
	}
}
