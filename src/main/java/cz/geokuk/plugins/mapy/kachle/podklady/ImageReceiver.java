/**
 *
 */
package cz.geokuk.plugins.mapy.kachle.podklady;

/**
 * @author Martin Veverka
 *
 */
public interface ImageReceiver {

	/** Velikost jedné třídy naléhavosti; v rámci třídy rozhoduje vzdálenost od středu výřezu. */
	long TRIDA = 1L << 40;

	/** Příjemce dlaždici už nechce, její získání se přeskočí. */
	/** Posun pořadí dlaždic mimo viditelnou oblast; větší než jakákoli vzdálenost od středu. */
	long MIMO_VYREZ = 1L << 34;
	long ZASTARALA = Long.MAX_VALUE;

	/** Dlaždice mimo výřez, třeba předvykreslení okolí; jde až za všechno, co je vidět. */
	long NA_POZADI = 3 * TRIDA;

	/**
	 * @param kachloStav
	 *            TODO
	 */
	public void send(KachloStav kachloStav);

	/**
	 * Jak naléhavě příjemce dlaždici potřebuje právě teď: menší číslo se získává dřív, {@link #ZASTARALA}, když ji už nechce. Volá se z vlákna, které vybírá další
	 * úlohu, a nesmí brát žádný zámek.
	 */
	default long dulezitost() {
		return NA_POZADI;
	}

}
