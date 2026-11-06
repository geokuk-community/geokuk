package cz.geokuk.plugins.challenge;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import cz.geokuk.core.coord.JSingleSlide0;

/** Společný předek vrstev plánovače na mapě: čte přehled aktivní challenge z modelu a drží podklad ke kreslení, dokud se výsledek nebo výběr nezmění. */
abstract class JVrstvaPlanovace extends JSingleSlide0 {

	private static final long serialVersionUID = 1L;

	protected transient ChallengeModel model;
	private transient TvarPlanovace tvar;
	private transient BooleanSupplier sektory = () -> true;
	private transient BooleanSupplier stitky = () -> true;
	private transient Supplier<? extends Collection<Rectangle>> prekazky = Collections::emptyList;

	JVrstvaPlanovace() {
		setOpaque(false);
		setCursor(null);
	}

	public void inject(final ChallengeModel model) {
		this.model = model;
	}

	/** Zdroj přepínače „Sektory“ (prstenec, klín, čára, štítek sektoru); výchozí zapnuto. */
	public void setSektory(final BooleanSupplier sektory) {
		this.sektory = sektory;
		repaint();
	}

	/** Zdroj přepínače „Štítky“ (značky a štítky kandidátů); výchozí zapnuto. */
	public void setStitky(final BooleanSupplier stitky) {
		this.stitky = stitky;
		repaint();
	}

	/** Překážky, kterým se značky vyhýbají: panely v rozích a legenda (viz {@link cz.geokuk.framework.RozmisteniPrekryvu#prekazky}); volá se při kreslení. */
	public void setPrekazky(final Supplier<? extends Collection<Rectangle>> prekazky) {
		this.prekazky = prekazky;
	}

	/** Překážky na mapě dané velikosti: panely a legenda od správce rozmístění, měřítko, pruh plánovače a ukazatel výkonu. */
	List<Rectangle> prekazky(final int sirka, final int vyska) {
		final List<Rectangle> l = new ArrayList<>(prekazky.get());
		l.add(cz.geokuk.framework.RozmisteniPrekryvu.meritko(new Dimension(sirka, vyska)));
		l.add(cz.geokuk.framework.RozmisteniPrekryvu.pruhPlanovace(cz.geokuk.framework.RozmisteniPrekryvu.volnaSirkaPruhu(sirka)));
		l.add(cz.geokuk.framework.RozmisteniPrekryvu.UKAZATEL_VYKONU);
		return l;
	}

	public void onEvent(final ChallengeZmenenyEvent event) {
		repaint();
	}

	@Override
	protected boolean jeNadMapouKNacitani() {
		return true;
	}

	@Override
	public abstract void paintComponent(java.awt.Graphics g);

	boolean kresliSektory() {
		return sektory.getAsBoolean();
	}

	boolean kresliStitky() {
		return stitky.getAsBoolean();
	}

	/** Podklad kreslení pro zobrazenou challenge, nebo null, když se nic nekreslí. */
	TvarPlanovace tvar() {
		final PrehledChallenge p = model == null || !model.jeZapnuto() ? null : model.prehled();
		if (p == null) {
			tvar = null;
			return null;
		}
		final VybranyKandidat v = model.vybranyKandidat(p.challenge());
		if (tvar == null || tvar.prehled != p || tvar.vybrany != v) {
			tvar = TvarPlanovace.z(p, v);
		}
		return tvar;
	}
}
