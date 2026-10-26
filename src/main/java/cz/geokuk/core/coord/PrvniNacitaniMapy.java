package cz.geokuk.core.coord;

import java.awt.GraphicsEnvironment;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import javax.swing.JComponent;
import javax.swing.Timer;

import cz.geokuk.core.napoveda.Diagnostika;

/**
 * Jediný příznak „první načítání mapy po spuštění“. Dokud trvá, ukazuje se logo načítání a vrstvy nad mapou (body, popisky, cesty) se nekreslí. Skončí prvním úplným načtením
 * výřezu, první chybnou dlaždicí nebo po {@link #LIMIT_MS} od spuštění programu; v tomto běhu se už nezapne.
 */
public final class PrvniNacitaniMapy {

	/** Nejdéle takto dlouho od spuštění programu trvá první načítání, i když dlaždice stále nedorazily (bez sítě, při chybě serveru). */
	public static final long LIMIT_MS = 30_000;

	/** Bez displeje není co ukazovat ani na co čekat. */
	private static boolean ukonceno = GraphicsEnvironment.isHeadless();
	/** Doba od spuštění programu v ms; v testech se dá nahradit. */
	private static LongSupplier hodiny = Diagnostika::dobaBehuMs;
	private static Timer casovac;
	private static final List<WeakReference<JComponent>> prekreslit = new ArrayList<>();

	private PrvniNacitaniMapy() {
	}

	/** Jestli první načítání ještě trvá; po limitu ho ukončí. */
	public static boolean probiha() {
		synchronized (PrvniNacitaniMapy.class) {
			if (ukonceno) {
				return false;
			}
			if (hodiny.getAsLong() <= LIMIT_MS) {
				spustCasovac();
				return true;
			}
		}
		ukonci();
		return false;
	}

	/** Ukončí první načítání a překreslí součásti, které na jeho konec čekají. */
	public static void ukonci() {
		final List<JComponent> komponenty = new ArrayList<>();
		synchronized (PrvniNacitaniMapy.class) {
			if (ukonceno) {
				return;
			}
			ukonceno = true;
			if (casovac != null) {
				casovac.stop();
				casovac = null;
			}
			for (final WeakReference<JComponent> ref : prekreslit) {
				final JComponent c = ref.get();
				if (c != null) {
					komponenty.add(c);
				}
			}
			prekreslit.clear();
		}
		for (final JComponent c : komponenty) {
			c.repaint();
		}
	}

	/** Součást se po skončení prvního načítání překreslí; bez sítě by se jinak nic nepřekreslilo samo. */
	public static synchronized void prekresliPoUkonceni(final JComponent c) {
		if (ukonceno) {
			return;
		}
		prekreslit.removeIf(ref -> ref.get() == null);
		for (final WeakReference<JComponent> ref : prekreslit) {
			if (ref.get() == c) {
				return;
			}
		}
		prekreslit.add(new WeakReference<>(c));
	}

	private static void spustCasovac() {
		if (casovac == null) {
			casovac = new Timer((int) Math.max(0, LIMIT_MS - hodiny.getAsLong()) + 100, e -> probiha());
			casovac.setRepeats(false);
			casovac.start();
		}
	}

	/** Jen pro testy: čas, podle kterého se počítá limit. */
	public static synchronized void nastavHodiny(final LongSupplier nove) {
		hodiny = nove;
	}

	/** Jen pro testy: první načítání zase začíná, čas stojí na nule. */
	public static synchronized void obnovProTesty() {
		ukonceno = false;
		hodiny = () -> 0;
		if (casovac != null) {
			casovac.stop();
			casovac = null;
		}
		prekreslit.clear();
	}
}
