package cz.geokuk.plugins.kesoid.mvc;

import cz.geokuk.framework.Event0;

/** Zvětšení ikon keší na mapě. */
public class ZvetseniIkonEvent extends Event0<KesoidModel> {

	/** Podle systému: Windows už zvětšuje celé okno, ikony se dál nezvětšují. */
	public static final int AUTO = 0;
	public static final int MIN = 50;
	public static final int MAX = 500;

	private final int procent;

	public ZvetseniIkonEvent(final int procent) {
		this.procent = procent;
	}

	/** Násobek velikosti ikon, Auto = 1. */
	public double getNasobek() {
		return procent == AUTO ? 1 : procent / 100.0;
	}

	public int getProcent() {
		return procent;
	}

	/** Hodnota z nastavení v povoleném rozsahu; neplatná je Auto. */
	public static int oriznout(final int procent) {
		return procent <= AUTO ? AUTO : Math.max(MIN, Math.min(MAX, procent));
	}
}
