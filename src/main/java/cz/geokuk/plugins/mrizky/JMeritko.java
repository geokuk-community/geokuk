/**
 *
 */
package cz.geokuk.plugins.mrizky;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.Hashtable;
import java.util.Map;

import javax.swing.JPanel;

/**
 * @author Martin Veverka
 *
 */
public class JMeritko extends JPanel {

	/** Dílek 1, 2 nebo 5 × 10^n metrů, nejmenší s aspoň touto šířkou; vychází tak 50 až 125 px. */
	static final int MINIMALNI_SIRKA_DILKU = 50;
	/** Dosavadní dílek zůstává, dokud nevyjede o víc než 10 % z rozsahu, ať na hranici neskáče tam a zpět. */
	private static final double HYSTEREZE = 0.1;
	/** Lišta má zhruba třetinu nejvyšší šířky, ale ne méně a ne více; počet dílků se k ní přizpůsobí. */
	static final int NEJMENSI_CILOVA_SIRKA = 250;
	static final int NEJVETSI_CILOVA_SIRKA = 400;
	private static final int ODSTUP_POPISKU_OD_CARKY = 3;

	private final static int tloustka = 6;
	private final static int vyskaCarky = 6;

	private static final long serialVersionUID = -4801191981059574701L;
	private double pixluNaMetr = 1;
	private double metruNaDilek;
	private int pixluNaDilek;
	private int pocetDilku;
	private int sirka;
	private int vyska;
	private double maximalniSirkaMeritka = 400;
	private Font font;
	private FontMetrics fontMetrics;

	public JMeritko() {
		// setPreferredSize(new Dimension(1600, 40));
		spocitejMetriky();
		// setBorder(BorderFactory.createLoweredBevelBorder());
	}

	public double getMaximalniSirkaMeritka() {
		return maximalniSirkaMeritka;
	}

	public double getPixluNaMetr() {
		return pixluNaMetr;
	}

	@Override
	public Dimension getPreferredSize() {
		final Insets insets = getInsets();
		return new Dimension(sirka + insets.left + insets.right, vyska + insets.top + insets.bottom);
	}

	public void setMaximalniSirkaMeritka(final double maximalniSirkaMeritka) {
		if (this.maximalniSirkaMeritka == maximalniSirkaMeritka) {
			return;
		}
		this.maximalniSirkaMeritka = maximalniSirkaMeritka;
		spocitejMetriky();
		revalidate();
		repaint();
	}

	public void setPixluNaMetr(final double pixluNaMetr) {
		if (pixluNaMetr == this.pixluNaMetr) {
			return;
		}
		this.pixluNaMetr = pixluNaMetr;
		spocitejMetriky();
		revalidate();
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		g = g.create();
		final Insets insets = getInsets();
		g.translate(insets.left, insets.top);
		if (pixluNaMetr <= 0 || Double.isNaN(pixluNaMetr)) {
			return;
		}
		g.setFont(font);

		final int offset = (getWidth() - sirka) / 2;
		final int pocatekY = fontMetrics.getHeight() + vyskaCarky + ODSTUP_POPISKU_OD_CARKY;
		for (int i = 0; i < pocetDilku; i++) {
			final double metruOdZacatku = i * metruNaDilek;
			final int pixluOdZacatku = offset + (int) (metruOdZacatku * pixluNaMetr);
			g.setColor(i % 2 == 0 ? Color.BLACK : Color.WHITE);
			g.fillRect(pixluOdZacatku, pocatekY, pixluNaDilek, tloustka);
			g.setColor(Color.BLACK);
			g.drawRect(pixluOdZacatku, pocatekY, pixluNaDilek, tloustka);
			g.setColor(Color.WHITE);
			g.drawLine(pixluOdZacatku, pocatekY - 1, pixluOdZacatku + pixluNaDilek, pocatekY - 1);
			g.setColor(Color.BLACK);
			g.fillRect(pixluOdZacatku, pocatekY - vyskaCarky, 2, vyskaCarky + tloustka);
			g.setColor(Color.WHITE);
			g.drawLine(pixluOdZacatku + 2, pocatekY - 1, pixluOdZacatku + 2, pocatekY - vyskaCarky);

			g.setColor(i % 2 == 0 ? Color.WHITE : Color.BLACK);
			// teď písmenka
			g.setColor(Color.BLACK);

			g.drawString(popisek(metruOdZacatku), pixluOdZacatku, pocatekY - vyskaCarky - ODSTUP_POPISKU_OD_CARKY);
		}
		final double metruOdZacatku = pocetDilku * metruNaDilek;
		final int pixluOdZacatku = offset + (int) (metruOdZacatku * getPixluNaMetr());
		g.drawString(jednotka(), pixluOdZacatku, pocatekY - vyskaCarky - ODSTUP_POPISKU_OD_CARKY);
	}

	private String jednotka() {
		return metruNaDilek >= 1000 ? "km" : "m";
	}

	/** Jednotku určuje dílek, aby všechny popisky lišty byly ve stejné. */
	String popisek(final double metru) {
		return String.valueOf(Math.round(metruNaDilek >= 1000 ? metru / 1000 : metru));
	}

	double getMetruNaDilek() {
		return metruNaDilek;
	}

	int getPocetDilku() {
		return pocetDilku;
	}

	int getPixluNaDilek() {
		return pixluNaDilek;
	}

	/** Nejmenší dílek 1, 2 nebo 5 × 10^n metrů, který má na mapě aspoň danou šířku. */
	static double dilek(final double pixluNaMetr, final double minimalniSirka) {
		double rad = 1;
		while (true) {
			for (final int nasobek : new int[] { 1, 2, 5 }) {
				if (nasobek * rad * pixluNaMetr >= minimalniSirka) {
					return nasobek * rad;
				}
			}
			rad *= 10;
		}
	}

	private void spocitejMetriky() {
		if (!(pixluNaMetr > 0) || Double.isInfinite(pixluNaMetr)) {
			return;
		}
		final double sirkaDosavadniho = metruNaDilek * pixluNaMetr;
		if (metruNaDilek <= 0 || sirkaDosavadniho < MINIMALNI_SIRKA_DILKU * (1 - HYSTEREZE) || sirkaDosavadniho >= MINIMALNI_SIRKA_DILKU * 2.5 * (1 + HYSTEREZE)) {
			metruNaDilek = dilek(pixluNaMetr, MINIMALNI_SIRKA_DILKU);
		}
		pixluNaDilek = Math.max(1, (int) (pixluNaMetr * metruNaDilek));
		final double cil = Math.max(NEJMENSI_CILOVA_SIRKA, Math.min(NEJVETSI_CILOVA_SIRKA, getMaximalniSirkaMeritka() / 3));
		pocetDilku = (int) Math.max(1, Math.min(Math.round(cil / pixluNaDilek), getMaximalniSirkaMeritka() / pixluNaDilek));

		final Map<TextAttribute, Object> map = new Hashtable<>();
		// map.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
		map.put(TextAttribute.BACKGROUND, Color.WHITE);
		map.put(TextAttribute.SWAP_COLORS, TextAttribute.SWAP_COLORS_ON);
		map.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
		font = Font.decode("ARIAL-BOLD-12").deriveFont(map);
		fontMetrics = getFontMetrics(font);
		final int naJednotkuNaKonci = fontMetrics.stringWidth(jednotka());

		sirka = pixluNaDilek * pocetDilku + naJednotkuNaKonci;
		vyska = vyskaCarky + tloustka + ODSTUP_POPISKU_OD_CARKY + fontMetrics.getHeight();
	}

}
