package cz.geokuk.plugins.cesty.data;

/** Jak úsek cesty vede mezi svými body. */
public enum TypUseku {
	/** Rovnou čarou. */
	ROVNE,
	/** Po cestách a pěšinách z offline mapy. */
	PO_CESTE,
	/** Vzdušně: přeskok, který se nepočítá do délky cesty. */
	VZDUSNY
}
