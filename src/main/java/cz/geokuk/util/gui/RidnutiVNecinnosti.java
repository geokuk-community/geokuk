package cz.geokuk.util.gui;

import java.awt.KeyboardFocusManager;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Pravidelná kontrola disku, která při neaktivním programu běží jen jednou za delší interval; po návratu do programu hned zase při každém tiku. */
public final class RidnutiVNecinnosti {

	private final long intervalNs;
	private final BooleanSupplier aktivni;
	private final LongSupplier hodiny;
	private boolean probehla;
	private long posledni;

	public RidnutiVNecinnosti(final long interval, final TimeUnit jednotka) {
		this(interval, jednotka, RidnutiVNecinnosti::jeProgramAktivni, System::nanoTime);
	}

	RidnutiVNecinnosti(final long interval, final TimeUnit jednotka, final BooleanSupplier aktivni, final LongSupplier hodiny) {
		intervalNs = jednotka.toNanos(interval);
		this.aktivni = aktivni;
		this.hodiny = hodiny;
	}

	/** Má kontrola proběhnout teď. */
	public synchronized boolean ted() {
		final long t = hodiny.getAsLong();
		if (probehla && !aktivni.getAsBoolean() && t - posledni < intervalNs) {
			return false;
		}
		probehla = true;
		posledni = t;
		return true;
	}

	/** Některé okno programu má fokus. */
	public static boolean jeProgramAktivni() {
		return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null;
	}
}
