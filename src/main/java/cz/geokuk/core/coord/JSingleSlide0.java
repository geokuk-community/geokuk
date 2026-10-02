/**
 *
 */
package cz.geokuk.core.coord;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;

import cz.geokuk.framework.Factory;
import cz.geokuk.framework.JSlide0;
import cz.geokuk.util.index2d.BoundingRect;

/**
 * Předek všech jednoduchých, tedy nesložených slidů
 */
public abstract class JSingleSlide0 extends JSlide0 {

	private static final long serialVersionUID = 8758817189971703053L;

	/** Kolikrát smějí značky jedné vrstvy pokrýt okno. */
	private static final double MAX_POKRYTI = 2;

	private Coord soord;

	protected Factory factory;

	/**
	 * Potomek musí vytvořit novou instanci slidu, který bude rendrovatelný. Obvykle postačí, když vytvoří prázdnou instanci své vlastní třídy. Pokud vrátí null, nic se rendrovat nebude
	 *
	 * @return
	 */
	public JSingleSlide0 createRenderableSlide() {
		return null;
	}

	/**
	 * @return the coord
	 */
	public Coord getSoord() {
		assert soord != null;
		return soord;
	}

	/**
	 * Oblast mapy, kterou je potřeba překreslit, rozšířená o okraj v pixelech. Po dorazení jedné dlaždice se tak nekreslí znovu celý výřez.
	 */
	protected BoundingRect oblastKresleni(final Graphics g, final int okraj) {
		final Rectangle clip = g.getClipBounds();
		if (clip == null) {
			return getSoord().getBoundingRect();
		}
		return getSoord().transforToBounding(new Rectangle(clip.x - okraj, clip.y - okraj, clip.width + 2 * okraj, clip.height + 2 * okraj));
	}

	/**
	 * Značky by okno pokryly víckrát, než má smysl: splynuly by v jednu plochu a kreslily se zbytečně dlouho.
	 *
	 * @param pocet
	 *            počet značek ve výřezu
	 * @param plochaZnacky
	 *            plocha jedné značky v pixelech
	 */
	protected boolean prilisHuste(final int pocet, final double plochaZnacky) {
		final Dimension dim = getSoord().getDim();
		return pocet * plochaZnacky > MAX_POKRYTI * dim.width * dim.height;
	}

	public void inject(final Factory factory) {
		this.factory = factory;
	}

	public EJakOtacetPriRendrovani jakOtacetProRendrovani() {
		return EJakOtacetPriRendrovani.GRAPH2D;
	}

	public void render(final Graphics g) throws InterruptedException {
		paintComponent(g);
	}

	/**
	 * @param coord
	 *            the coord to set
	 */
	public void setSoord(final Coord soord) {
		assert soord != null;
		this.soord = soord;
	}

	protected boolean isSoordInitialized() {
		return soord != null;
	}

	protected void onVyrezChanged() {}

}
