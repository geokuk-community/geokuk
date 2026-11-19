package cz.geokuk.plugins.challenge;

/** Světová strana, kterou se skáče na nejbližšího kandidáta (Ctrl+Shift+šipka). */
public enum SmerSkoku {
	SEVER(0, "na sever", "↑"), VYCHOD(90, "na východ", "→"), JIH(180, "na jih", "↓"), ZAPAD(270, "na západ", "←");

	private final double azimut;
	private final String text;
	private final String sipka;

	SmerSkoku(final double azimut, final String text, final String sipka) {
		this.azimut = azimut;
		this.text = text;
		this.sipka = sipka;
	}

	/** Šipka na klávesnici pro směr: ↑ → ↓ ←. */
	public String sipka() {
		return sipka;
	}

	/** Azimut směru ve stupních od severu po směru hodin. */
	public double azimut() {
		return azimut;
	}

	/** Text pro uživatele, například „na sever“. */
	public String text() {
		return text;
	}
}
