package cz.geokuk.plugins.kesoid.mvc;

/**
 * Viditelnost vrstvy, která navazuje na kešoidy (popisky, kruhy, obsazenost): vrstva je vidět, jen když jsou zapnuté kešoidy (Kešoidy > Zobrazovat kešoidy, F5) a zároveň její vlastní
 * vypínač. Obě podmínky se drží zvlášť, takže zapnutí kešoidů vrátí vrstvu přesně do stavu, v jakém ji nechal její vlastní vypínač, a vlastní vypínač se zobrazením kešoidů nepřepisuje.
 * Dokud nepřijde událost, jsou obě podmínky splněné.
 */
public final class ViditelnostSKesoidy {

	private boolean kesoidy = true;
	private boolean vlastni = true;

	/** Zobrazování kešoidů se zapnulo nebo vypnulo; vrací, zda má být vrstva vidět. */
	public boolean setKesoidy(final boolean zapnuto) {
		kesoidy = zapnuto;
		return isViditelne();
	}

	/** Vlastní vypínač vrstvy se přepnul; vrací, zda má být vrstva vidět. */
	public boolean setVlastni(final boolean zapnuto) {
		vlastni = zapnuto;
		return isViditelne();
	}

	public boolean isViditelne() {
		return kesoidy && vlastni;
	}
}
