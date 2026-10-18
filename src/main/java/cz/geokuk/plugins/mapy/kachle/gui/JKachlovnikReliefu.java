package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Component;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.plugins.mapy.kachle.ReliefCuzkEvent;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;

/** Stínovaný reliéf ČÚZK přes mapový podklad. */
public class JKachlovnikReliefu extends JKachlovnik {

	private static final long serialVersionUID = 1L;

	private boolean viditelny;

	public JKachlovnikReliefu() {
		super("Kachlovník reliéfu", Priority.KACHLE);
		setVisible(false);
	}

	public void onEvent(final ReliefCuzkEvent event) {
		if (viditelny == event.isZapnuto()) {
			return;
		}
		viditelny = event.isZapnuto();
		setVisible(viditelny);
		init(false);
	}

	/** Dlaždice jsou vždy reliéf, na podkladu nezáleží. */
	@Override
	public void setKachloType(final EKaType katype) {
		super.setKachloType(EKaType.RELIEF_CUZK);
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

	/** Reliéf do tisku a exportu výřezu. */
	static final class Rendrovaci extends JKachlovnikRendrovaci {
		private static final long serialVersionUID = 1L;

		@Override
		public void setKachloType(final EKaType katype) {
			super.setKachloType(EKaType.RELIEF_CUZK);
		}
	}
}
