package cz.geokuk.plugins.kesoid;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

import cz.geokuk.framework.JSlide0;
import cz.geokuk.framework.RozmisteniPrekryvu;
import cz.geokuk.util.gui.BublinkaHodnoty;
import cz.geokuk.util.gui.PopisekNaMape;

/** Krátká zpráva uprostřed dole nad měřítkem; plynule se objeví, drží a zmizí. Myš ani klávesy nezachytává. */
public class JToast extends JSlide0 {

	private static final long serialVersionUID = 1L;

	/** Jak dlouho se zpráva drží beze změny. */
	public static final int DRZENI_MS = 2500;
	private transient Supplier<? extends Collection<Rectangle>> prekazky = Collections::emptyList;
	private final transient BublinkaHodnoty bublinka = new BublinkaHodnoty(this::repaint, DRZENI_MS);

	public JToast() {
		setOpaque(false);
	}

	public void onEvent(final ToastEvent event) {
		bublinka.dotkni(event.getText());
	}

	String text() {
		return bublinka.getText();
	}

	boolean bezi() {
		return bublinka.casovacBezi();
	}

	/** Překážky, které zpráva přeskočí (panely v rozích, legenda); volá se při kreslení. */
	public void setPrekazky(final Supplier<? extends Collection<Rectangle>> prekazky) {
		this.prekazky = prekazky;
	}

	/** Obdélník zprávy podle {@link RozmisteniPrekryvu#zprava}. */
	static Rectangle umisteni(final int sirkaMapy, final int vyskaMapy, final int sirkaZpravy, final int vyskaZpravy) {
		return RozmisteniPrekryvu.zprava(new Dimension(sirkaMapy, vyskaMapy), new Dimension(sirkaZpravy, vyskaZpravy), Collections.<Rectangle> emptyList());
	}

	/** Text zkrácený o konec s třemi tečkami, aby se vešel do {@code sirka}; text, který se vejde, beze změny. */
	static String zkrat(final String text, final FontMetrics fm, final int sirka) {
		if (fm.stringWidth(text) <= sirka) {
			return text;
		}
		int n = text.length();
		while (n > 1 && fm.stringWidth(text.substring(0, n) + "…") > sirka) {
			n--;
		}
		return text.substring(0, n) + "…";
	}

	@Override
	public boolean contains(final int x, final int y) {
		return false;
	}

	@Override
	public void removeNotify() {
		bublinka.zrus();
		super.removeNotify();
	}

	@Override
	public void setVisible(final boolean visible) {
		if (!visible) {
			bublinka.zrus();
		}
		super.setVisible(visible);
	}

	@Override
	protected void paintComponent(final Graphics g) {
		final float v = bublinka.viditelnost();
		if (v <= 0f) {
			return;
		}
		final Graphics2D g2 = (Graphics2D) g;
		final Font font = getFont().deriveFont(Font.BOLD);
		final FontMetrics fm = g2.getFontMetrics(font);
		final Rectangle velikost = PopisekNaMape.hranice(fm, bublinka.getText(), 0, 0);
		final Rectangle r = RozmisteniPrekryvu.zprava(getSize(), new Dimension(velikost.width, velikost.height), prekazky.get());
		PopisekNaMape.nakresli(g2, font, zkrat(bublinka.getText(), fm, r.width - (velikost.width - fm.stringWidth(bublinka.getText()))), r.x + r.width, r.y + r.height / 2, v);
	}
}
