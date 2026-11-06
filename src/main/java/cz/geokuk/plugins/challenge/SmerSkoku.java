package cz.geokuk.plugins.challenge;

/** Směr skoku na kandidáta challenge (Shift+šipka); kandidát se hledá v kuželu ±45° kolem směru. */
public enum SmerSkoku {
	SEVER("na sever"), VYCHOD("na východ"), JIH("na jih"), ZAPAD("na západ");

	private final String text;

	SmerSkoku(final String text) {
		this.text = text;
	}

	/** „na sever“ a podobně, do zprávy po zkratce. */
	public String text() {
		return text;
	}
}
