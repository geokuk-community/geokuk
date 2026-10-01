package cz.geokuk.plugins.mapy.stahovac;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import cz.geokuk.plugins.mapy.kachle.podklady.*;

/** Průběh jednoho hromadného stahování dlaždic: počty, zastavení a zastavení při omezení ze strany serveru. */
class DavkaStahovani {

	private final int celkem;
	private final AtomicInteger zarazeno = new AtomicInteger();
	private final AtomicInteger hotovo = new AtomicInteger();
	private final AtomicInteger chyb = new AtomicInteger();
	private final List<Kanceler> kancelery = new ArrayList<>();
	private final Runnable priZmene;
	private volatile boolean zarazovaniSkonceno;
	private volatile String duvodZastaveni;

	DavkaStahovani(final int celkem, final Runnable priZmene) {
		this.celkem = celkem;
		this.priZmene = priZmene;
	}

	ImageReceiver prijemce() {
		return stav -> {
			if (stav.getThr() == null) {
				hotovo.incrementAndGet();
			} else {
				chyb.incrementAndGet();
				final KachloDownloader.ChybaServeru chybaServeru = chybaServeru(stav.getThr());
				if (chybaServeru != null && chybaServeru.jeOmezeni()) {
					zastav("Server mapy omezil stahování (HTTP " + chybaServeru.getKod() + "). Zkuste to později.");
				}
			}
			priZmene.run();
		};
	}

	/** Vrací false, když je dávka zastavená a další dlaždice se už nemají zařazovat. */
	synchronized boolean zarazeno(final Kanceler kanceler) {
		if (duvodZastaveni != null) {
			kanceler.cancel();
			return false;
		}
		kancelery.add(kanceler);
		zarazeno.incrementAndGet();
		return true;
	}

	void zarazovaniSkonceno() {
		zarazovaniSkonceno = true;
		priZmene.run();
	}

	boolean jeZastavena() {
		return duvodZastaveni != null;
	}

	void zastav(final String duvod) {
		final List<Kanceler> kZruseni;
		synchronized (this) {
			if (duvodZastaveni != null) {
				return;
			}
			duvodZastaveni = duvod;
			kZruseni = new ArrayList<>(kancelery);
			kancelery.clear();
		}
		kZruseni.forEach(Kanceler::cancel);
		priZmene.run();
	}

	boolean jeHotova() {
		return duvodZastaveni != null || zarazovaniSkonceno && hotovo.get() + chyb.get() >= zarazeno.get();
	}

	String popis() {
		final int h = hotovo.get();
		final int ch = chyb.get();
		if (duvodZastaveni != null) {
			return String.format("Zastaveno: %s V cache je %d z %d dlaždic, chyb %d.", duvodZastaveni, h, celkem, ch);
		}
		if (jeHotova()) {
			return String.format("Hotovo: v cache je %d z %d dlaždic, chyb %d.", h, celkem, ch)
					+ (ch > 0 ? " Chybějící dlaždice stáhnete, když stahování spustíte znovu pro stejný výřez." : "");
		}
		return String.format("Zařazeno %d z %d dlaždic, v cache %d, chyb %d.", zarazeno.get(), celkem, h, ch);
	}

	private static KachloDownloader.ChybaServeru chybaServeru(final Throwable chyba) {
		for (Throwable t = chyba; t != null; t = t.getCause()) {
			if (t instanceof KachloDownloader.ChybaServeru) {
				return (KachloDownloader.ChybaServeru) t;
			}
		}
		return null;
	}
}
