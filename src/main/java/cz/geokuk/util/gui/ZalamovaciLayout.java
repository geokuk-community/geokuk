package cz.geokuk.util.gui;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Komponenty vedle sebe zleva v přirozené šířce; co se do řádku nevejde, přejde celé na další řádek.
 * Rezervovaná komponenta se při zalamování počítá i neviditelná, aby se počet řádků neměnil s její viditelností.
 * Plovoucí komponenta (třeba průběh) o řádcích nerozhoduje, dostane zbylé místo v posledním řádku.
 */
public class ZalamovaciLayout implements LayoutManager {

	private final Set<Component> rezervovane = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Component> plovouci = Collections.newSetFromMap(new IdentityHashMap<>());

	public void rezervuj(final Component c) {
		rezervovane.add(c);
	}

	public void plovouci(final Component c) {
		plovouci.add(c);
	}

	@Override
	public void addLayoutComponent(final String name, final Component comp) {}

	@Override
	public void removeLayoutComponent(final Component comp) {
		rezervovane.remove(comp);
		plovouci.remove(comp);
	}

	@Override
	public Dimension preferredLayoutSize(final Container parent) {
		synchronized (parent.getTreeLock()) {
			int sirka = 0;
			for (final Component c : parent.getComponents()) {
				if (zabiraMisto(c) && !plovouci.contains(c)) {
					sirka += c.getPreferredSize().width;
				}
			}
			final Insets ins = parent.getInsets();
			final int dostupna = dostupnaSirka(parent);
			int vyska = 0;
			for (final List<Component> radek : radky(parent, dostupna > 0 ? dostupna - ins.left - ins.right : Integer.MAX_VALUE)) {
				vyska += vyskaRadku(radek);
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
			final int prava = parent.getWidth() - ins.right;
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
			if (!zabiraMisto(c) || plovouci.contains(c)) {
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
