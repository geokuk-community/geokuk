package cz.geokuk.plugins.challenge;

import java.util.Arrays;

/**
 * Umisťování štítků kandidátů vedle ikon keší: štítek se položí těsně vedle své ikony tam, kde nepřekrývá žádnou jinou ikonu, překážku ani dříve umístěný štítek. Nevejde-li se
 * těsně, zkusí se o kus dál se spojnicí k ikoně; nevejde-li se ani tam, štítek se vynechá. Mřížka drží obdélníky (ikony, překážky, umístěné štítky), takže kontrola je úměrná okolí,
 * ne počtu všech ikon.
 */
final class RozmisteniStitku {

	/** Kde štítek skončil: levý horní roh, a když je od ikony vzdálený, i bod spojnice na hraně ikony a na hraně štítku. */
	static final class Umisteni {
		int x;
		int y;
		boolean spojnice;
		int ikonaX;
		int ikonaY;
		int stitekX;
		int stitekY;
	}

	private static final int BUNKA = 48;
	/** Mezera štítku těsně u ikony. */
	static final int MEZERA = 2;
	/** Mezera štítku se spojnicí. */
	static final int MEZERA_SE_SPOJNICI = 14;

	private final int sloupcu;
	private final int radku;
	private final int[] hlava;
	private int[] dalsi = new int[256];
	private int[] rx = new int[256];
	private int[] ry = new int[256];
	private int[] rw = new int[256];
	private int[] rh = new int[256];
	private int pocet;

	RozmisteniStitku(final int sirka, final int vyska) {
		sloupcu = sirka / BUNKA + 3;
		radku = vyska / BUNKA + 3;
		hlava = new int[sloupcu * radku];
		Arrays.fill(hlava, -1);
	}

	/** Obdélník, na který se štítky nesmí (ikona jiné keše, panel, měřítko). */
	void pridej(final int x, final int y, final int w, final int h) {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (pocet == rx.length) {
			final int n = pocet * 2;
			dalsi = Arrays.copyOf(dalsi, n);
			rx = Arrays.copyOf(rx, n);
			ry = Arrays.copyOf(ry, n);
			rw = Arrays.copyOf(rw, n);
			rh = Arrays.copyOf(rh, n);
		}
		rx[pocet] = x;
		ry[pocet] = y;
		rw[pocet] = w;
		rh[pocet] = h;
		final int c0 = bunkaX(x);
		final int c1 = bunkaX(x + w - 1);
		final int r0 = bunkaY(y);
		final int r1 = bunkaY(y + h - 1);
		for (int r = r0; r <= r1; r++) {
			for (int c = c0; c <= c1; c++) {
				// jeden obdélník může být v několika buňkách, proto se každá buňka řetězí samostatným záznamem
				vlozDoBunky(r * sloupcu + c, pocet);
			}
		}
		pocet++;
	}

	private int[] retez = new int[256];
	private int[] retezObd = new int[256];
	private int retezPocet;

	private void vlozDoBunky(final int bunka, final int obdelnik) {
		if (retezPocet == retez.length) {
			retez = Arrays.copyOf(retez, retezPocet * 2);
			retezObd = Arrays.copyOf(retezObd, retezPocet * 2);
		}
		retez[retezPocet] = hlava[bunka];
		retezObd[retezPocet] = obdelnik;
		hlava[bunka] = retezPocet++;
	}

	private int bunkaX(final int x) {
		return Math.max(0, Math.min(sloupcu - 1, x / BUNKA + 1));
	}

	private int bunkaY(final int y) {
		return Math.max(0, Math.min(radku - 1, y / BUNKA + 1));
	}

	/** Kryje obdélník některý z uložených (kromě obdélníku {@code vlastni} o stejné poloze a rozměru jako ikona kandidáta)? */
	private boolean kryje(final int x, final int y, final int w, final int h, final int vx, final int vy, final int vw, final int vh) {
		final int c0 = bunkaX(x);
		final int c1 = bunkaX(x + w - 1);
		final int r0 = bunkaY(y);
		final int r1 = bunkaY(y + h - 1);
		for (int r = r0; r <= r1; r++) {
			for (int c = c0; c <= c1; c++) {
				for (int i = hlava[r * sloupcu + c]; i >= 0; i = retez[i]) {
					final int o = retezObd[i];
					if (x < rx[o] + rw[o] && x + w > rx[o] && y < ry[o] + rh[o] && y + h > ry[o] && !(rx[o] == vx && ry[o] == vy && rw[o] == vw && rh[o] == vh)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Umístí štítek {@code w × h} k ikoně {@code ix, iy, iw, ih}; ikona už má být přidána jako obdélník. Vrací null, když se štítek nikam nevejde; jinak štítek zapíše mezi obdélníky.
	 */
	Umisteni umisti(final int ix, final int iy, final int iw, final int ih, final int w, final int h) {
		for (final int mezera : new int[] { MEZERA, MEZERA_SE_SPOJNICI }) {
			final int[][] polohy = { { ix + iw + mezera, iy + (ih - h) / 2 }, { ix - w - mezera, iy + (ih - h) / 2 }, { ix + (iw - w) / 2, iy - h - mezera }, { ix + (iw - w) / 2, iy + ih + mezera },
					{ ix + iw + mezera, iy - h - mezera / 2 }, { ix + iw + mezera, iy + ih + mezera / 2 }, { ix - w - mezera, iy - h - mezera / 2 }, { ix - w - mezera, iy + ih + mezera / 2 } };
			for (final int[] p : polohy) {
				if (!kryje(p[0], p[1], w, h, ix, iy, iw, ih)) {
					final Umisteni u = new Umisteni();
					u.x = p[0];
					u.y = p[1];
					u.spojnice = mezera == MEZERA_SE_SPOJNICI;
					u.ikonaX = Math.max(ix, Math.min(ix + iw, p[0] + w / 2));
					u.ikonaY = Math.max(iy, Math.min(iy + ih, p[1] + h / 2));
					u.stitekX = Math.max(p[0], Math.min(p[0] + w, ix + iw / 2));
					u.stitekY = Math.max(p[1], Math.min(p[1] + h, iy + ih / 2));
					pridej(p[0], p[1], w, h);
					return u;
				}
			}
		}
		return null;
	}

	/** Počet uložených obdélníků. */
	int pocet() {
		return pocet;
	}
}
