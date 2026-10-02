package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Component;
import java.awt.Graphics;

import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JKachlovnikRendrovaci extends JKachlovnik {

	public interface Progressor {
		void setProgress(int value, int maxlue);
	}


	private static final long serialVersionUID = -3170605712662727739L;
	private Progressor progressor;
	private int citacZpracovanychKachli;
	private int celkovyPocetKachliKtereRendruejeme;

	public JKachlovnikRendrovaci() {
		super("Renderovací kachlovník", Priority.STAHOVANI);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.core.coord.JSingleSlide0#render(java.awt.Graphics)
	 */
	@Override
	public void render(final Graphics g) throws InterruptedException {
		try {
			celkovyPocetKachliKtereRendruejeme = 0;
			citacZpracovanychKachli = 0;
			paintComponent(g);
			// if (true) return;
			vykreslovatokamzite = true;
			init(false);
			paint(g);
			final Component[] components = getComponents();
			celkovyPocetKachliKtereRendruejeme = components.length;
			log.debug("waitNaDotazeni: start");
			for (final Component component : components) {
				if (component instanceof JKachle) {
					final JKachle kachle = (JKachle) component;
					log.debug("waitNaDotazeni: {}", kachle.getKaLoc());
					kachle.waitNaDotazeniDlazdice();
				}
			}
			log.debug("waitNaDotazeni: stop");
		} finally {
			// KDyž končíme, třeba i výjimkou, rychle kachlím řekneme, že je nepotřebujeme
			// a ona se v mžiku vyprázdní front
			for (final Component component : getComponents()) {
				if (component instanceof JKachle) {
					final JKachle kachle = (JKachle) component;
					kachle.uzTeNepotrebuju();
				}
			}
			log.trace("Opoustim cekani");
		}
		// paint(g);
	}

	public void setProgressor(final Progressor progressor) {
		this.progressor = progressor;
	}

	@Override
	protected JKachle createJKachle(final Ka ka) {
		return new JKachleRendrovaci(this, ka);
	}

	@Override
	void kachleZpracovana(final JKachle jKachle) {
		++citacZpracovanychKachli;
		log.debug("Zpracováno dlaždic: {}/{}", citacZpracovanychKachli, celkovyPocetKachliKtereRendruejeme);
		if (progressor != null) {
			progressor.setProgress(++citacZpracovanychKachli, celkovyPocetKachliKtereRendruejeme);
		}
	}

}
