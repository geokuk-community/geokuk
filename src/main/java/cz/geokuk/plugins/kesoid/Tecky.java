package cz.geokuk.plugins.kesoid;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import cz.geokuk.plugins.kesoid.data.EKesoidKind;

/**
 * Keše jako barevné tečky při oddálené mapě. Barva podle typu keše s tenkým tmavým obrysem, nalezené jsou menší, vlastní mají silnější
 * obrys, neaktivní a archivované jsou světlejší.
 */
public final class Tecky {

	static final int MIN_PRUMER = 4;
	static final int MAX_PRUMER = 14;

	private static final Color OBRYS = new Color(0x444444);
	private static final Color OBRYS_VLASTNI = new Color(0x111111);

	static final Color TRADICNI = new Color(0x80AF64);
	static final Color MULTI = new Color(0xFCDE19);
	static final Color MYSTERY = new Color(0x002FC1);
	static final Color VIRTUALNI = new Color(0xF2F2F2);
	static final Color EVENT = new Color(0x960000);
	static final Color LAB = new Color(0xA9FFF0);
	static final Color OSTATNI = new Color(0x0000FF);
	static final Color NEKES = new Color(0x888888);

	enum Styl {
		BEZNA, NALEZENA, VLASTNI
	}

	private final Map<Long, BufferedImage> obrazky = new HashMap<>();

	/** Průměr tečky, aby tečky pokryly okno zhruba dvakrát. */
	static int prumer(final int pocet, final Dimension okno) {
		if (pocet <= 0) {
			return MAX_PRUMER;
		}
		final int d = (int) Math.round(Math.sqrt(2.0 * okno.width * okno.height / pocet));
		return Math.max(MIN_PRUMER, Math.min(MAX_PRUMER, d));
	}

	static Color barva(final Wpt wpt) {
		final Kesoid kesoid = wpt.getKesoid();
		if (kesoid.getKesoidKind() != EKesoidKind.KES || !wpt.isMainWpt()) {
			return NEKES;
		}
		return barvaTypu(wpt.getSym());
	}

	/** Barva podle textu typu keše z GPX nebo databáze. */
	static Color barvaTypu(final String typ) {
		if (typ == null) {
			return OSTATNI;
		}
		final String t = typ.toLowerCase(Locale.ROOT);
		if (t.contains("event") || t.contains("celebration") || t.contains("block party") || t.contains("gps adventures")) {
			return EVENT;
		}
		if (t.contains("traditional")) {
			return TRADICNI;
		}
		if (t.contains("multi")) {
			return MULTI;
		}
		if (t.contains("unknown") || t.contains("mystery") || t.contains("letterbox") || t.contains("wherigo")) {
			return MYSTERY;
		}
		if (t.contains("virtual") || t.contains("webcam") || t.contains("earth")) {
			return VIRTUALNI;
		}
		if (t.contains("lab")) {
			return LAB;
		}
		return OSTATNI;
	}

	static Styl styl(final Kesoid kesoid) {
		switch (kesoid.getVztah()) {
		case FOUND:
			return Styl.NALEZENA;
		case OWN:
			return Styl.VLASTNI;
		default:
			return Styl.BEZNA;
		}
	}

	static boolean neaktivni(final Kesoid kesoid) {
		return kesoid.getStatus() != EKesStatus.ACTIVE;
	}

	/** Nakreslí tečku se středem v bodě. */
	public void kresli(final Graphics g, final Wpt wpt, final Point p, final int prumer) {
		final Kesoid kesoid = wpt.getKesoid();
		final BufferedImage img = obrazek(barva(wpt), styl(kesoid), neaktivni(kesoid), prumer);
		g.drawImage(img, p.x - img.getWidth() / 2, p.y - img.getHeight() / 2, null);
	}

	BufferedImage obrazek(final Color barva, final Styl styl, final boolean neaktivni, final int prumer) {
		final long klic = (long) barva.getRGB() << 32 | styl.ordinal() << 16 | (neaktivni ? 1 << 8 : 0) | prumer;
		return obrazky.computeIfAbsent(klic, k -> nakresli(barva, styl, neaktivni, prumer));
	}

	private static Color zesvetli(final Color c) {
		return new Color((c.getRed() + 255) / 2, (c.getGreen() + 255) / 2, (c.getBlue() + 255) / 2);
	}

	private static BufferedImage nakresli(final Color barvaTypu, final Styl styl, final boolean neaktivni, final int prumer) {
		final int d = styl == Styl.NALEZENA ? Math.max(3, prumer * 2 / 3) : prumer;
		// Bez vyhlazení a bez průhlednosti, pixely jsou jen plné nebo prázdné, takže se tečka kreslí bez míchání barev.
		final BufferedImage img = new BufferedImage(d + 1, d + 1, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		final Color barva = neaktivni ? zesvetli(barvaTypu) : barvaTypu;
		switch (styl) {
		case VLASTNI:
			g.setColor(barva);
			g.fillOval(0, 0, d, d);
			g.setColor(OBRYS_VLASTNI);
			g.setStroke(new BasicStroke(d >= 8 ? 2f : 1f));
			g.drawOval(1, 1, d - 2, d - 2);
			break;
		default:
			g.setColor(barva);
			g.fillOval(0, 0, d, d);
			if (d >= 5) {
				g.setColor(OBRYS);
				g.drawOval(0, 0, d - 1, d - 1);
			}
		}
		g.dispose();
		return img;
	}
}
