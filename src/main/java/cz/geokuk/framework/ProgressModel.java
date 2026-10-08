/**
 *
 */
package cz.geokuk.framework;

import java.util.function.IntConsumer;

import javax.swing.SwingUtilities;

/**
 * @author Martin Veverka
 *
 */
public class ProgressModel extends Model0 {

	private static final ThreadLocal<IntConsumer> SLEDOVAC_POSTUPU = new ThreadLocal<>();

	/** Průběhy založené ve volajícím vláknu hlásí procenta dokončení do sledovače; null sledování ukončí. */
	public static void setSledovacPostupu(final IntConsumer sledovac) {
		if (sledovac == null) {
			SLEDOVAC_POSTUPU.remove();
		} else {
			SLEDOVAC_POSTUPU.set(sledovac);
		}
	}

	private class SimpleNotParalelProgressor implements Progressor {

		private static final long millisToDecideToPopup = 500;
		private static final long millisToPopup = 2000;
		/**
		 *
		 */
		private static final int MINIMALNI_DOBA_MEZI_DVEVA_POSUNY_PROGRESORU = 101;
		private int progress;
		private int max;
		private String text = "";

		private String tooltip = "";
		private boolean visible;

		private final IntConsumer sledovac = SLEDOVAC_POSTUPU.get();
		private final long startTime = System.currentTimeMillis();
		private long lastFireTime;

		/*
		 * (non-Javadoc)
		 *
		 * @see nove.Progressor#addProgress(int)
		 */
		@Override
		public void addProgress(final int aProgress) {
			setProgress(progress + aProgress);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see nove.Progressor#finish()
		 */
		@Override
		public void finish() {
			progress = 0;
			visible = false;
			lastFireTime = 0; // při ukončení musíme vždy poslat
			firex(true);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see cz.geokuk.framework.Progressor#getProgress()
		 */
		@Override
		public int getProgress() {
			return progress;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see nove.Progressor#incProgress()
		 */
		@Override
		public void incProgress() {
			addProgress(1);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see nove.Progressor#setMax(int)
		 */
		@Override
		public void setMax(final int aMax) {
			if (max == aMax) {
				return;
			}
			max = aMax;
			firex(false);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see nove.Progressor#setProgress(int)
		 */
		@Override
		public void setProgress(final int nv) {
			progress = nv;
			if (sledovac != null && max > 0) {
				sledovac.accept((int) Math.min(100L, 100L * nv / max));
			}
			if (nv >= max) {
				finish();
			} else {
				if (visible) {
					firex(false);
				} else {
					final long T = System.currentTimeMillis();
					final long dT = T - startTime;
					if (dT >= millisToDecideToPopup) {
						long predictedCompletionTime;
						if (nv > 0) {
							predictedCompletionTime = (int) (dT * max / nv);
						} else {
							predictedCompletionTime = millisToPopup;
						}
						if (predictedCompletionTime >= millisToPopup) {
							visible = true;
							firex(true);
						}
					}
				}
			}
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see nove.Progressor#setText(java.lang.String)
		 */
		@Override
		public void setText(final String aText) {
			if (text.equals(aText)) {
				return;
			}
			text = aText;
			firex(true);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see nove.Progressor#setTooltip(java.lang.String)
		 */
		@Override
		public void setTooltip(final String aTooltip) {
			if (tooltip.equals(aTooltip)) {
				return;
			}
			tooltip = aTooltip;
			firex(true);
		}

		/**
		 *
		 */
		void firex(final boolean allways) {
			final long T = System.currentTimeMillis();
			if (allways || T - lastFireTime > MINIMALNI_DOBA_MEZI_DVEVA_POSUNY_PROGRESORU) {
				final ProgressEvent event = new ProgressEvent(this, visible, progress, max, text, tooltip);
				lastFireTime = T;
				SwingUtilities.invokeLater(() -> fire(event));
			}
		}

	}

	public Progressor start(final int max, final String text) {
		final SimpleNotParalelProgressor progressor = new SimpleNotParalelProgressor();
		progressor.max = max;
		progressor.text = text;
		return progressor;
	}

	public Progressor start(final int max, final String text, final String tooltip) {
		final Progressor progressor = start(max, text);
		progressor.setTooltip(tooltip);
		return progressor;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.framework.Model0#initAndFire()
	 */
	@Override
	protected void initAndFire() {}

}
