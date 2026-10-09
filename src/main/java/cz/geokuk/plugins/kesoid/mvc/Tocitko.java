package cz.geokuk.plugins.kesoid.mvc;

import java.awt.event.HierarchyEvent;

import javax.swing.JComponent;
import javax.swing.Timer;

/** Překresluje ikony „načítá se“, aby se točily; běží jen, dokud se něco načítá a komponenta je vidět. */
final class Tocitko {

	static final int KROK_MS = 80;

	private final JComponent komponenta;
	private final Timer timer;
	private boolean nacitaSe;

	Tocitko(final JComponent komponenta, final Runnable prekresli) {
		this.komponenta = komponenta;
		timer = new Timer(KROK_MS, e -> {
			if (komponenta.isShowing()) {
				prekresli.run();
			} else {
				zastav();
			}
		});
		komponenta.addHierarchyListener(e -> {
			if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
				prepni();
			}
		});
	}

	void nastav(final boolean nacitaSe) {
		this.nacitaSe = nacitaSe;
		prepni();
	}

	private void prepni() {
		if (nacitaSe && komponenta.isShowing()) {
			if (!timer.isRunning()) {
				timer.start();
			}
		} else {
			zastav();
		}
	}

	private void zastav() {
		timer.stop();
	}

	boolean bezi() {
		return timer.isRunning();
	}
}
