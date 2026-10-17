package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Component;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.plugins.mapy.kachle.PrekryvTrasEvent;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;

/** Průhledné dlaždice s turistickými a cyklotrasami přes mapový podklad. */
public class JKachlovnikTras extends JKachlovnik {

	private static final long serialVersionUID = 1L;

	static final String ATRIBUCE = "Trasy " + EKaType.TRASY.getAtribuce();

	private boolean viditelny;
	/** Podklad pod trasami, podle něj se rozhoduje o atribuci. */
	private EKaType podklad;

	public JKachlovnikTras() {
		super("Kachlovník tras", Priority.KACHLE);
		setVisible(false);
	}

	public void onEvent(final PrekryvTrasEvent event) {
		if (viditelny == event.isViditelny()) {
			return;
		}
		viditelny = event.isViditelny();
		setVisible(viditelny);
		init(false);
	}

	/** Dlaždice jsou vždy trasy, podklad se jen zapamatuje. */
	@Override
	public void setKachloType(final EKaType katype) {
		podklad = katype;
		super.setKachloType(EKaType.TRASY);
		repaint();
	}

	@Override
	protected void init(final boolean smimZnovuPouzitKachle) {
		if (!viditelny) {
			for (final Component c : getComponents()) {
				((JKachle) c).uzTeNepotrebuju();
			}
			removeAll();
			return;
		}
		super.init(smimZnovuPouzitKachle);
	}

	@Override
	public JSingleSlide0 createRenderableSlide() {
		return new Rendrovaci();
	}

	/** Trasy do tisku a exportu výřezu. */
	static final class Rendrovaci extends JKachlovnikRendrovaci {
		private static final long serialVersionUID = 1L;

		@Override
		public void setKachloType(final EKaType katype) {
			super.setKachloType(EKaType.TRASY);
		}
	}

	/** Podklad, který data OpenStreetMap nepřiznává sám, dostane do atribuce OpenStreetMap kvůli trasám. */
	static boolean potrebujeAtribuci(final EKaType podklad) {
		return podklad != null && !podklad.isOffline() && !podklad.getAtribuce().contains("OpenStreetMap");
	}
}
