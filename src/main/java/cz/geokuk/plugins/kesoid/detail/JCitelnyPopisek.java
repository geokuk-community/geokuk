package cz.geokuk.plugins.kesoid.detail;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.UIManager;

/**
 * Popisek v daném odstínu, který se ztmaví nebo zesvětlí tak, aby byl na pozadí podle vzhledu dobře čitelný (kontrast aspoň 4,5:1).
 */
public class JCitelnyPopisek extends JLabel {

	private static final long serialVersionUID = 1L;

	static final double MIN_KONTRAST = 4.5;

	private final Color odstin;

	public JCitelnyPopisek(final Color odstin) {
		this.odstin = odstin;
	}

	@Override
	public Color getForeground() {
		// Volá se už z konstruktoru JLabel, kdy odstín ještě není nastaven.
		return odstin == null ? super.getForeground() : citelna(odstin, pozadi());
	}

	private Color pozadi() {
		for (Component c = getParent(); c != null; c = c.getParent()) {
			if (c.isOpaque() && c.getBackground() != null) {
				return c.getBackground();
			}
		}
		final Color panel = UIManager.getColor("Panel.background");
		return panel != null ? panel : Color.WHITE;
	}

	/** Odstín smíchaný s černou nebo bílou (podle toho, co s pozadím víc kontrastuje) jen tolik, aby měl s pozadím kontrast aspoň {@link #MIN_KONTRAST}. */
	static Color citelna(final Color odstin, final Color pozadi) {
		if (kontrast(odstin, pozadi) >= MIN_KONTRAST) {
			return odstin;
		}
		final Color cil = kontrast(Color.BLACK, pozadi) >= kontrast(Color.WHITE, pozadi) ? Color.BLACK : Color.WHITE;
		for (int i = 1; i <= 20; i++) {
			final Color c = smichej(odstin, cil, i / 20.0);
			if (kontrast(c, pozadi) >= MIN_KONTRAST) {
				return c;
			}
		}
		return cil;
	}

	static double kontrast(final Color a, final Color b) {
		final double la = jas(a);
		final double lb = jas(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	/** Relativní jas podle WCAG 2. */
	private static double jas(final Color c) {
		return 0.2126 * slozka(c.getRed()) + 0.7152 * slozka(c.getGreen()) + 0.0722 * slozka(c.getBlue());
	}

	private static double slozka(final int hodnota) {
		final double s = hodnota / 255.0;
		return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
	}

	private static Color smichej(final Color a, final Color b, final double podil) {
		return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * podil), (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * podil),
				(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * podil));
	}
}
