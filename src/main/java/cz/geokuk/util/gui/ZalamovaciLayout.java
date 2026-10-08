package cz.geokuk.util.gui;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Komponenty vedle sebe zleva v přirozené šířce; co se do řádku nevejde, přejde celé na další řádek.
 * Rezervovaná komponenta se při zalamování počítá i neviditelná, aby se počet řádků neměnil s její viditelností.
 * Plovoucí komponenta (třeba průběh) o řádcích nerozhoduje, dostane zbylé místo v posledním řádku.
 * Pravá komponenta stojí vždy u pravého okraje dole; spodní řádek se vejde vedle ní, horní řádky mají plnou šířku. Při zalomení ani
 * při změně obsahu řádku se pravá komponenta nepohne.
 */
public class ZalamovaciLayout implements LayoutManager {

	private final Set<Component> rezervovane = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Component> plovouci = Collections.newSetFromMap(new IdentityHashMap<>());
	private Component pravy;

	public void rezervuj(final Component c) {
		rezervovane.add(c);
	}

	public void plovouci(final Component c) {
		plovouci.add(c);
	}

	public void vpravo(final Component c) {
		pravy = c;
	}

	@Override
	public void addLayoutComponent(final String name, final Component comp) {}

	@Override
	public void removeLayoutComponent(final Component comp) {
		rezervovane.remove(comp);
		plovouci.remove(comp);
		if (pravy == comp) {
			pravy = null;
		}
	}

	@Override
	public Dimension preferredLayoutSize(final Container parent) {
		synchronized (parent.getTreeLock()) {
			int sirka = 0;
			for (final Component c : parent.getComponents()) {
				if (zabiraMisto(c) && !plovouci.contains(c) && c != pravy) {
					sirka += c.getPreferredSize().width;
				}
			}
			final int sirkaPraveho = sirkaPraveho(parent);
			sirka += sirkaPraveho;
			final Insets ins = parent.getInsets();
			final int dostupna = dostupnaSirka(parent);
			int vyska = 0;
			final int sirkaRadku = dostupna > 0 ? dostupna - ins.left - ins.right : Integer.MAX_VALUE;
			final List<List<Component>> radky = radky(parent, sirkaRadku, sirkaRadku - sirkaPraveho);
			for (int i = 0; i < radky.size(); i++) {
				vyska += vyskaRadku(radky.get(i), i == radky.size() - 1 ? sirkaPraveho : 0);
			}
			if (sirkaPraveho > 0) {
				vyska = Math.max(vyska, pravy.getPreferredSize().height);
			}
			return new Dimension((dostupna > 0 ? Math.min(sirka, dostupna) : sirka) + ins.left + ins.right, vyska + ins.top + ins.bottom);
		}
	}

	@Override
	public Dimension minimumLayoutSize(final Container parent) {
		return preferredLayoutSize(parent);
	}

	@Override
	public void layoutContainer(final Container parent) {
		synchronized (parent.getTreeLock()) {
			final Insets ins = parent.getInsets();
			final int sirkaPraveho = sirkaPraveho(parent);
			// V kontejneru užším než pravá komponenta zůstane vidět její levá část.
			final int prava = Math.max(ins.left, parent.getWidth() - ins.right - sirkaPraveho);
			if (sirkaPraveho > 0) {
				final int vyskaPraveho = Math.min(pravy.getPreferredSize().height, parent.getHeight() - ins.top - ins.bottom);
				pravy.setBounds(prava, parent.getHeight() - ins.bottom - vyskaPraveho, sirkaPraveho, vyskaPraveho);
			}
			int y = ins.top;
			int x = ins.left;
			int vyska = 0;
			final List<List<Component>> radky = radky(parent, parent.getWidth() - ins.right - ins.left, prava - ins.left);
			for (int i = 0; i < radky.size(); i++) {
				final List<Component> radek = radky.get(i);
				y += vyska;
				vyska = vyskaRadku(radek, i == radky.size() - 1 ? sirkaPraveho : 0);
				x = ins.left;
				for (final Component c : radek) {
					if (c.isVisible()) {
						final int sirka = c.getPreferredSize().width;
						c.setBounds(x, y, sirka, vyska);
						x += sirka;
					}
				}
			}
			for (final Component c : parent.getComponents()) {
				if (plovouci.contains(c) && c.isVisible()) {
					final int sirka = Math.max(0, Math.min(c.getPreferredSize().width, prava - x));
					c.setBounds(x, y, sirka, vyska);
					x += sirka;
				}
			}
		}
	}

	private int sirkaPraveho(final Container parent) {
		return pravy != null && pravy.getParent() == parent && pravy.isVisible() ? pravy.getPreferredSize().width : 0;
	}

	private boolean zabiraMisto(final Component c) {
		return c.isVisible() || rezervovane.contains(c);
	}

	/**
	 * Šířka, do které se bude kontejner skládat: šířka rodiče (kontejner ji vyplňuje celou), nebo vlastní.
	 * Rodič má při změně velikosti okna novou šířku dřív než kontejner.
	 */
	private static int dostupnaSirka(final Container parent) {
		for (Container c = parent.getParent() != null ? parent.getParent() : parent; c != null; c = c.getParent()) {
			if (c.getWidth() > 0) {
				return c.getWidth();
			}
		}
		return 0;
	}

	/** Řádky do plné šířky; poslední (spodní) řádek se musí vejít vedle pravé komponenty. */
	private List<List<Component>> radky(final Container parent, final int sirka, final int sirkaPosledniho) {
		final List<List<Component>> radky = new ArrayList<>();
		List<Component> radek = new ArrayList<>();
		int obsazeno = 0;
		for (final Component c : parent.getComponents()) {
			if (!zabiraMisto(c) || plovouci.contains(c) || c == pravy) {
				continue;
			}
			final int w = c.getPreferredSize().width;
			if (!radek.isEmpty() && obsazeno + w > sirka) {
				radky.add(radek);
				radek = new ArrayList<>();
				obsazeno = 0;
			}
			radek.add(c);
			obsazeno += w;
		}
		if (!radek.isEmpty()) {
			radky.add(radek);
		}
		if (!radky.isEmpty() && sirkaRadku(radky.get(radky.size() - 1)) > sirkaPosledniho) {
			final List<Component> posledni = radky.get(radky.size() - 1);
			final List<Component> spodni = new ArrayList<>();
			int obsazenoSpodni = 0;
			while (posledni.size() > 1 && obsazenoSpodni + posledni.get(posledni.size() - 1).getPreferredSize().width <= sirkaPosledniho) {
				final Component c = posledni.remove(posledni.size() - 1);
				spodni.add(0, c);
				obsazenoSpodni += c.getPreferredSize().width;
			}
			if (spodni.isEmpty() && posledni.size() > 1) {
				spodni.add(posledni.remove(posledni.size() - 1));
			}
			if (!spodni.isEmpty()) {
				radky.add(spodni);
			}
		}
		return radky;
	}

	private static int sirkaRadku(final List<Component> radek) {
		int sirka = 0;
		for (final Component c : radek) {
			sirka += c.getPreferredSize().width;
		}
		return sirka;
	}

	/** Spodní řádek je aspoň tak vysoký jako pravá komponenta, aby do horních řádků nezasahovala. */
	private int vyskaRadku(final List<Component> radek, final int sirkaPraveho) {
		int vyska = sirkaPraveho > 0 ? pravy.getPreferredSize().height : 0;
		for (final Component c : radek) {
			vyska = Math.max(vyska, c.getPreferredSize().height);
		}
		return vyska;
	}
}
