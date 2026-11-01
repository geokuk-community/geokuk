package cz.geokuk.util.gui;

import java.awt.Frame;

/**
 * Přenesení okna do popředí na výslovnou žádost, třeba z dálkového ovládání. Systémy nedovolují programu na pozadí brát jiným focus: na Windows okno zůstane
 * pod ostatními a bliká na hlavním panelu. Proto se okno nejdřív obnoví z ikony a na chvíli se mu nastaví „vždy nahoře“, což ho zviditelní i bez práva na popředí;
 * focus se jen vyžádá, jeho získání systém nezaručuje.
 */
public final class PredniOkno {

	/** Co se s oknem dělá; odděleno kvůli testům. */
	public interface Povrch {
		boolean jeIkonizovane();

		void obnov();

		void nastavVzdyNahore(boolean nahore);

		void doPopredi();

		void pozadejFocus();
	}

	private PredniOkno() {}

	/** Obnoví okno z ikony, přenese ho do popředí a vyžádá mu focus; „vždy nahoře“ se po skončení vždy vrátí. */
	public static void predAktivuj(final Povrch okno) {
		if (okno.jeIkonizovane()) {
			okno.obnov();
		}
		try {
			okno.nastavVzdyNahore(true);
			okno.doPopredi();
			okno.pozadejFocus();
		} finally {
			okno.nastavVzdyNahore(false);
		}
	}

	public static void predAktivuj(final Frame okno) {
		predAktivuj(new Povrch() {
			@Override
			public boolean jeIkonizovane() {
				return (okno.getExtendedState() & Frame.ICONIFIED) != 0;
			}

			@Override
			public void obnov() {
				okno.setExtendedState(okno.getExtendedState() & ~Frame.ICONIFIED);
			}

			@Override
			public void nastavVzdyNahore(final boolean nahore) {
				if (okno.isAlwaysOnTopSupported()) {
					try {
						okno.setAlwaysOnTop(nahore);
					} catch (final SecurityException e) {
						// bez práva se okno jen přenese do popředí
					}
				}
			}

			@Override
			public void doPopredi() {
				okno.toFront();
			}

			@Override
			public void pozadejFocus() {
				okno.requestFocus();
			}
		});
	}
}
