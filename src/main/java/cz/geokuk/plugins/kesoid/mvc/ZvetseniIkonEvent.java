package cz.geokuk.plugins.kesoid.mvc;

import cz.geokuk.framework.Event0;

/** Zvětšení ikon keší na mapě. */
public class ZvetseniIkonEvent extends Event0<KesoidModel> {

	public static final int VYCHOZI = 100;
	public static final int MIN = 50;
	public static final int MAX = 500;

	private final int procent;

	public ZvetseniIkonEvent(final int procent) {
		this.procent = procent;
	}

	/** Násobek velikosti ikon. */
	public double getNasobek() {
		return procent / 100.0;
	}

	public int getProcent() {
		return procent;
	}

	/** Hodnota z nastavení v povoleném rozsahu; neplatná je výchozí. */
	public static int oriznout(final int procent) {
		return procent <= 0 ? VYCHOZI : Math.max(MIN, Math.min(MAX, procent));
	}
}
