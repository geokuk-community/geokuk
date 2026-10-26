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
 * Příznak „první načítání mapy po spuštění“. Dokud trvá, ukazuje se logo načítání a vrstvy nad mapou (body, popisky, cesty) se nekreslí. Vrstvy se začnou kreslit, když je
 * ve výřezu aspoň {@link #PRAH_PROCENT} dlaždic s hotovým obrázkem; logo zmizí až při úplném načtení. Obojí skončí i první chybnou dlaždicí nebo po {@link #LIMIT_MS} od
 * spuštění programu. V tomto běhu se příznak už nevrací, ani když při posunu podíl načtených dlaždic klesne.
 */
public final class PrvniNacitaniMapy {

	/** Nejdéle takto dlouho od spuštění programu trvá první načítání, i když dlaždice stále nedorazily (bez sítě, při chybě serveru). */
	public static final long LIMIT_MS = 30_000;

	/** Kolik procent dlaždic výřezu musí mít hotový obrázek, aby se začaly kreslit vrstvy nad mapou. */
	public static final int PRAH_PROCENT = 40;

	/** Bez displeje není co ukazovat ani na co čekat. */
	private static boolean ukonceno = GraphicsEnvironment.isHeadless();
	private static boolean vrstvyZobrazeny = GraphicsEnvironment.isHeadless();
	/** Doba od spuštění programu v ms; v testech se dá nahradit. */
	private static LongSupplier hodiny = Diagnostika::dobaBehuMs;
	private static Timer casovac;
	private static final List<WeakReference<JComponent>> prekreslit = new ArrayList<>();
	private static final List<WeakReference<JComponent>> prekreslitVrstvy = new ArrayList<>();

	private PrvniNacitaniMapy() {
	}

	/** Jestli první načítání ještě trvá a má se ukazovat logo; po limitu ho ukončí. */
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

	/** Jestli se vrstvy nad mapou ještě nemají kreslit; po limitu první načítání ukončí. */
	public static boolean vrstvyCekaji() {
		synchronized (PrvniNacitaniMapy.class) {
			if (vrstvyZobrazeny || ukonceno) {
				return false;
			}
		}
		return probiha();
	}

	/** Jestli je z dlaždic výřezu načtená dost na to, aby se začaly kreslit vrstvy nad mapou. */
	public static boolean jeDostNactenych(final int hotovych, final int vsech) {
		return vsech > 0 && hotovych * 100 >= PRAH_PROCENT * vsech;
	}

	/** Od teď se vrstvy nad mapou kreslí vždy; překreslí se. */
	public static void zobrazVrstvy() {
		final List<JComponent> komponenty;
		synchronized (PrvniNacitaniMapy.class) {
			if (vrstvyZobrazeny) {
				return;
			}
			vrstvyZobrazeny = true;
			komponenty = vezmi(prekreslitVrstvy);
		}
		komponenty.forEach(JComponent::repaint);
	}

	/** Ukončí první načítání (logo i čekání vrstev) a překreslí součásti, které na jeho konec čekají. */
	public static void ukonci() {
		final List<JComponent> komponenty = new ArrayList<>();
		synchronized (PrvniNacitaniMapy.class) {
			if (ukonceno) {
				return;
			}
			ukonceno = true;
			vrstvyZobrazeny = true;
			if (casovac != null) {
				casovac.stop();
				casovac = null;
			}
			komponenty.addAll(vezmi(prekreslit));
			komponenty.addAll(vezmi(prekreslitVrstvy));
		}
		komponenty.forEach(JComponent::repaint);
	}

	private static List<JComponent> vezmi(final List<WeakReference<JComponent>> seznam) {
		final List<JComponent> komponenty = new ArrayList<>();
		for (final WeakReference<JComponent> ref : seznam) {
			final JComponent c = ref.get();
			if (c != null) {
				komponenty.add(c);
			}
		}
		seznam.clear();
		return komponenty;
	}

	/** Součást (logo) se po skončení prvního načítání překreslí; bez sítě by se jinak nic nepřekreslilo samo. */
	public static synchronized void prekresliPoUkonceni(final JComponent c) {
		if (!ukonceno) {
			pridej(prekreslit, c);
		}
	}

	/** Vrstva se překreslí, až se začnou kreslit vrstvy nad mapou. */
	public static synchronized void prekresliPoZobrazeniVrstev(final JComponent c) {
		if (!vrstvyZobrazeny) {
			pridej(prekreslitVrstvy, c);
		}
	}

	private static void pridej(final List<WeakReference<JComponent>> seznam, final JComponent c) {
		seznam.removeIf(ref -> ref.get() == null);
		for (final WeakReference<JComponent> ref : seznam) {
			if (ref.get() == c) {
				return;
			}
		}
		seznam.add(new WeakReference<>(c));
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
		vrstvyZobrazeny = false;
		hodiny = () -> 0;
		if (casovac != null) {
			casovac.stop();
			casovac = null;
		}
		prekreslit.clear();
		prekreslitVrstvy.clear();
	}
}
