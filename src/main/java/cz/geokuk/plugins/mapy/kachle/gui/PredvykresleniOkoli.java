package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.BiFunction;

import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.podklady.ImageReceiver;
import cz.geokuk.plugins.mapy.kachle.podklady.Kanceler;

/**
 * Předvykreslení dlaždic těsně za okrajem výřezu. Dlaždice žádá po jedné, další až po dokončení předchozí, takže nikdy nezabere víc než jedno vlákno
 * vykreslování a viditelné dlaždice zdrží nejvýš o dokončení té jedné.
 */
final class PredvykresleniOkoli {

	private final BiFunction<Ka, ImageReceiver, Kanceler> zdroj;

	private final Deque<Ka> fronta = new ArrayDeque<>();
	private Kanceler aktualni = Kanceler.EMPTY;
	private int generace;
	private boolean spusteno;
	private int krok;

	PredvykresleniOkoli(final BiFunction<Ka, ImageReceiver, Kanceler> zdroj) {
		this.zdroj = zdroj;
	}

	/** Zruší rozdělané předvykreslení a začne s novou frontou. */
	void spust(final List<Ka> dlazdice) {
		final int moje;
		final Kanceler zrusit;
		synchronized (this) {
			zrusit = aktualni;
			zrusInterne();
			spusteno = true;
			fronta.addAll(dlazdice);
			moje = generace;
		}
		zrusit.cancel();
		dalsi(moje);
	}

	/** Zruší rozdělané předvykreslení. */
	void zrus() {
		final Kanceler zrusit;
		synchronized (this) {
			zrusit = aktualni;
			zrusInterne();
		}
		zrusit.cancel();
	}

	private void zrusInterne() {
		generace++;
		spusteno = false;
		fronta.clear();
		aktualni = Kanceler.EMPTY;
	}

	/** Zdroj se volá mimo zámek: příjemce může být zavolán rovnou a zdroj zamyká dlaždice. */
	private void dalsi(final int moje) {
		final Ka ka;
		final int mujKrok;
		synchronized (this) {
			if (moje != generace || fronta.isEmpty()) {
				return;
			}
			ka = fronta.poll();
			mujKrok = ++krok;
		}
		final Kanceler k = zdroj.apply(ka, stav -> dalsi(moje));
		synchronized (this) {
			if (moje == generace) {
				if (mujKrok == krok) { // příjemce mohl být zavolán rovnou a požádat o další
					aktualni = k;
				}
				return;
			}
		}
		k.cancel(); // zrušeno během žádosti
	}

	/** Od posledního zrušení už běželo; znovu se nespouští, dokud se výřez nezmění. */
	synchronized boolean jeSpusteno() {
		return spusteno;
	}

	/**
	 * Dlaždice jednoho prstence kolem výřezu, od středu okna.
	 */
	static List<Ka> okoli(final Kaputer kaputer, final EKaType typ, final int sirka, final int vyska) {
		final int px = kaputer.getPocetKachliX();
		final int py = kaputer.getPocetKachliY();
		final List<Ka> ka = new ArrayList<>();
		final List<long[]> razeni = new ArrayList<>();
		for (int yi = -1; yi <= py; yi++) {
			for (int xi = -1; xi <= px; xi++) {
				if (xi >= 0 && xi < px && yi >= 0 && yi < py) {
					continue;
				}
				final Point p = kaputer.getKachlePoint(xi, yi);
				final long dx = 2L * p.x + Kaputer.KACHLE_PIXELS - sirka;
				final long dy = 2L * p.y + Kaputer.KACHLE_PIXELS - vyska;
				razeni.add(new long[] { dx * dx + dy * dy, ka.size() });
				ka.add(new Ka(kaputer.getKaloc(xi, yi), typ));
			}
		}
		razeni.sort(Comparator.comparingLong(r -> r[0]));
		final List<Ka> vysledek = new ArrayList<>(ka.size());
		for (final long[] r : razeni) {
			vysledek.add(ka.get((int) r[1]));
		}
		return vysledek;
	}
}
