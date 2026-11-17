package cz.geokuk.plugins.challenge;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.function.Supplier;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.UIManager;

import cz.geokuk.util.gui.ZkraceniTextu;

/**
 * Položka seznamu challenge: na začátku ● u zapnuté a ○ u ostatních, potom název a šedé přípony „(šablona)“ a „(nedostupná)“. Dlouhý název se zkrátí „…“ podle šířky seznamu, aby
 * seznam nedostal vodorovný posuvník a přípona zůstala vidět; plný název a souhrn jsou v tooltipu.
 */
final class PolozkaChallengeRenderer extends DefaultListCellRenderer {

	private static final long serialVersionUID = 1L;
	private final transient Supplier<ChallengeModel> model;

	PolozkaChallengeRenderer(final Supplier<ChallengeModel> model) {
		this.model = model;
	}

	/** Šedá přípona za textem popisku (značka a název). */
	private String pripona = "";
	private boolean vybrana;

	/** Značka zapnuté challenge. */
	static final String ZAPNUTA = "●";
	/** Značka ostatních challenge. */
	static final String VYPNUTA = "○";

	/** Celý text položky včetně přípon. */
	static String plnyText(final JLabel polozka) {
		return polozka.getText() + (polozka instanceof PolozkaChallengeRenderer ? ((PolozkaChallengeRenderer) polozka).pripona : "");
	}

	@Override
	public Component getListCellRendererComponent(final JList<?> l, final Object v, final int i, final boolean sel, final boolean fokus) {
		final Challenge c = (Challenge) v;
		final ChallengeModel m = model.get();
		final JLabel s = (JLabel) super.getListCellRendererComponent(l, c.nazev(), i, sel, fokus);
		vybrana = sel;
		final String znacka = (m != null && m.jeAktivni(c) ? ZAPNUTA : VYPNUTA) + "  ";
		pripona = (m != null && m.jeSablona(c) ? "  (šablona)" : "") + (m != null && !m.jeVUi(c) ? "  (nedostupná)" : "");
		final FontMetrics fm = s.getFontMetrics(s.getFont());
		final int volno = l.getWidth() - s.getInsets().left - s.getInsets().right - fm.stringWidth(znacka + pripona);
		s.setText(znacka + (l.getWidth() > 0 ? ZkraceniTextu.zkrat(c.nazev(), fm, volno) : c.nazev()));
		// Šířku seznamu určuje okno, ne délka názvu.
		s.setPreferredSize(new Dimension(1, s.getPreferredSize().height));
		s.setToolTipText("<html>" + escape(c.nazev()) + "<br>" + escape(FormularDefinice.souhrn(c)) + "</html>");
		return s;
	}

	@Override
	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		if (pripona.isEmpty()) {
			return;
		}
		final FontMetrics fm = g.getFontMetrics(getFont());
		final Color tlumena = UIManager.getColor("Label.disabledForeground");
		g.setColor(vybrana || tlumena == null ? getForeground() : tlumena);
		g.setFont(getFont());
		g.drawString(pripona, getInsets().left + fm.stringWidth(getText()), (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
	}

	private static String escape(final String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
