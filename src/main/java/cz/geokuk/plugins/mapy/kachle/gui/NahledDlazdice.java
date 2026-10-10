package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/**
 * Výřez už hotové dlaždice z jiného měřítka, který se ukáže, dokud se nekreslí tato dlaždice.
 */
final class NahledDlazdice {

	static final int MAX_UROVNI_NAHORU = 3;

	final Image obrazek;
	final int sx1, sy1, sx2, sy2;
	final int dx1, dy1, dx2, dy2;

	private NahledDlazdice(final Image obrazek, final int sx1, final int sy1, final int sx2, final int sy2, final int dx1, final int dy1, final int dx2, final int dy2) {
		this.obrazek = obrazek;
		this.sx1 = sx1;
		this.sy1 = sy1;
		this.sx2 = sx2;
		this.sy2 = sy2;
		this.dx1 = dx1;
		this.dy1 = dy1;
		this.dx2 = dx2;
		this.dy2 = dy2;
	}

	/**
	 * Najde náhled: nejbližší hotovou dlaždici z nižšího měřítka, jinak čtyři hotové dlaždice z vyššího.
	 *
	 * @param hledej
	 *            hotový obrázek dlaždice, nebo null, když ho nemáme
	 * @param velikost
	 *            velikost dlaždice v logických bodech
	 */
	static List<NahledDlazdice> najdi(final Ka ka, final Function<Ka, Image> hledej, final int velikost) {
		final KaLoc loc = ka.getLoc();
		KaLoc rodic = loc;
		for (int uroven = 1; uroven <= MAX_UROVNI_NAHORU; uroven++) {
			rodic = rodic.rodic();
			if (rodic == null) {
				break;
			}
			final Image img = hledej.apply(new Ka(rodic, ka.getType()));
			final int w = img == null ? 0 : img.getWidth(null);
			final int h = img == null ? 0 : img.getHeight(null);
			if (w >= 1 << uroven && h >= 1 << uroven) {
				final int n = 1 << uroven;
				final int ix = loc.getSignedX() - (rodic.getSignedX() << uroven);
				final int iy = n - 1 - (loc.getSignedY() - (rodic.getSignedY() << uroven));
				return Collections.singletonList(new NahledDlazdice(img, ix * w / n, iy * h / n, (ix + 1) * w / n, (iy + 1) * h / n, 0, 0, velikost, velikost));
			}
		}
		if (loc.getMoumer() < 1) {
			return Collections.emptyList();
		}
		final List<NahledDlazdice> deti = new ArrayList<>(4);
		final int pul = velikost / 2;
		for (int vpravo = 0; vpravo <= 1; vpravo++) {
			for (int nahoru = 0; nahoru <= 1; nahoru++) {
				final Image img = hledej.apply(new Ka(loc.dite(vpravo, nahoru), ka.getType()));
				final int w = img == null ? 0 : img.getWidth(null);
				final int h = img == null ? 0 : img.getHeight(null);
				if (w > 0 && h > 0) {
					final int x = vpravo * pul;
					final int y = (1 - nahoru) * pul;
					deti.add(new NahledDlazdice(img, 0, 0, w, h, x, y, x + pul, y + pul));
				}
			}
		}
		return deti;
	}

	void kresli(final Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(obrazek, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
	}
}
