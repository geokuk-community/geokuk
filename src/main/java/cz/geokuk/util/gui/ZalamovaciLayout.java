package cz.geokuk.util.gui;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Komponenty vedle sebe zleva v přirozené šířce; co se do řádku nevejde, přejde celé na další řádek.
 * Rezervovaná komponenta se při zalamování počítá i neviditelná, aby se počet řádků neměnil s její viditelností.
 * Plovoucí komponenta (třeba průběh) o řádcích nerozhoduje, dostane zbylé místo v posledním řádku.
 * Pravá komponenta stojí vždy u pravého okraje dole a ostatní se zalamují do šířky vedle ní, takže se při zalomení ani při změně
 * obsahu řádku nepohne.
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
			for (final List<Component> radek : radky(parent, dostupna > 0 ? dostupna - ins.left - ins.right - sirkaPraveho : Integer.MAX_VALUE)) {
				vyska += vyskaRadku(radek);
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
			for (final List<Component> radek : radky(parent, prava - ins.left)) {
				y += vyska;
				vyska = vyskaRadku(radek);
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

	private List<List<Component>> radky(final Container parent, final int sirka) {
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
		return radky;
	}

	private static int vyskaRadku(final List<Component> radek) {
		int vyska = 0;
		for (final Component c : radek) {
			vyska = Math.max(vyska, c.getPreferredSize().height);
		}
		return vyska;
	}
}
