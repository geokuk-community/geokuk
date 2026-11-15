package cz.geokuk.util.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JTextField;
import javax.swing.UIManager;

/** Jednořádkové textové pole, které v prázdném stavu zobrazí šedou nápovědu; po zadání textu zmizí. */
public class JPoleSNapovedou extends JTextField {

	private static final long serialVersionUID = 1L;

	private final String napoveda;

	public JPoleSNapovedou(final String napoveda, final int sloupcu) {
		super(sloupcu);
		this.napoveda = napoveda;
	}

	/** Nápověda se kreslí jen do prázdného pole. */
	public boolean ukazujeNapovedu() {
		return getDocument().getLength() == 0;
	}

	public String getNapoveda() {
		return napoveda;
	}

	@Override
	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		if (!ukazujeNapovedu()) {
			return;
		}
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			final Color seda = UIManager.getColor("TextField.inactiveForeground");
			g2.setColor(seda != null ? seda : Color.GRAY);
			g2.setFont(getFont());
			final Insets in = getInsets();
			final int y = in.top + (getHeight() - in.top - in.bottom - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();
			g2.drawString(napoveda, in.left, y);
		} finally {
			g2.dispose();
		}
	}
}
