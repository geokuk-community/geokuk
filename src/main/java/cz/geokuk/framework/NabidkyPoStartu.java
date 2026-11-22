package cz.geokuk.framework;

import java.awt.Dialog;
import java.awt.Window;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import javax.swing.RootPaneContainer;
import javax.swing.Timer;

/**
 * Okna, která program otevře sám (ne na akci uživatele), například nabídka nové verze nebo doporučení optimalizace: ukazují se po jednom, až není
 * otevřené žádné modální ani blokující okno, a pak ještě po krátké pauze, aby nepřekryla rozdělanou práci. Volá se jen z EDT.
 */
public final class NabidkyPoStartu {

	/** Klientská vlastnost kořenového panelu nemodálního okna, které má ostatní nabídky zdržet (nabídka sama, průběh operace). */
	public static final String BLOKUJE = "geokuk.blokujeNabidky";

	static final long PAUZA_MS = 3000;
	private static final int KROK_MS = 500;

	private static NabidkyPoStartu instance;

	private final BooleanSupplier blokovano;
	private final LongSupplier hodiny;
	private final boolean sCasovacem;
	private final Deque<Runnable> fronta = new ArrayDeque<>();
	private Timer casovac;
	private long volnoOd = -1;
	private boolean ukazuje;

	NabidkyPoStartu(final BooleanSupplier blokovano, final LongSupplier hodiny, final boolean sCasovacem) {
		this.blokovano = blokovano;
		this.hodiny = hodiny;
		this.sCasovacem = sCasovacem;
	}

	/** Ukáže nabídku hned, nebo až se uvolní místo. */
	public static void ukaz(final Runnable nabidka) {
		if (instance == null) {
			instance = new NabidkyPoStartu(NabidkyPoStartu::jeOtevrenoBlokujiciOkno, System::currentTimeMillis, true);
		}
		instance.pridej(nabidka);
	}

	/** Označí nemodální okno, které má zdržet ostatní nabídky, dokud je vidět. */
	public static void blokuje(final RootPaneContainer okno) {
		okno.getRootPane().putClientProperty(BLOKUJE, Boolean.TRUE);
	}

	void pridej(final Runnable nabidka) {
		fronta.add(nabidka);
		if (fronta.size() == 1 && !ukazuje && !blokovano.getAsBoolean()) {
			ukazDalsi();
			return;
		}
		if (sCasovacem && casovac == null) {
			casovac = new Timer(KROK_MS, e -> tik());
			casovac.start();
		}
	}

	/** Krok časovače: další nabídka až po pauze, během které nic neblokovalo. */
	void tik() {
		if (ukazuje) {
			return;
		}
		if (fronta.isEmpty()) {
			zastavCasovac();
			return;
		}
		if (blokovano.getAsBoolean()) {
			volnoOd = -1;
			return;
		}
		final long nyni = hodiny.getAsLong();
		if (volnoOd < 0) {
			volnoOd = nyni;
		} else if (nyni - volnoOd >= PAUZA_MS) {
			ukazDalsi();
		}
	}

	int cekajicich() {
		return fronta.size();
	}

	private void ukazDalsi() {
		final Runnable nabidka = fronta.poll();
		volnoOd = -1;
		ukazuje = true;
		try {
			nabidka.run();
		} finally {
			ukazuje = false;
		}
		if (fronta.isEmpty()) {
			zastavCasovac();
		}
	}

	private void zastavCasovac() {
		if (casovac != null) {
			casovac.stop();
			casovac = null;
		}
	}

	static boolean jeOtevrenoBlokujiciOkno() {
		for (final Window w : Window.getWindows()) {
			if (!w.isShowing()) {
				continue;
			}
			if (w instanceof Dialog && ((Dialog) w).isModal()) {
				return true;
			}
			if (w instanceof RootPaneContainer && Boolean.TRUE.equals(((RootPaneContainer) w).getRootPane().getClientProperty(BLOKUJE))) {
				return true;
			}
		}
		return false;
	}
}
