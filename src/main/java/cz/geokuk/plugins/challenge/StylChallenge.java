package cz.geokuk.plugins.challenge;

import java.awt.Color;

/** Barva challenge: stejná v liště plánovače i u značek na mapě. */
public final class StylChallenge {

	private static final Color SEKTORY = new Color(0x7B3FC4);
	private static final Color MATICE = new Color(0x1E6FD9);
	private static final Color KALENDAR = new Color(0xA05500);

	private StylChallenge() {
	}

	public static Color barva(final Challenge c) {
		switch (c.typ()) {
		case DT_MATICE:
			return MATICE;
		case KALENDAR:
			return KALENDAR;
		default:
			return SEKTORY;
		}
	}
}
