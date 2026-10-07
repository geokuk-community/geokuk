package cz.geokuk.plugins.kesoid.mvc;

import cz.geokuk.framework.Event0;
import cz.geokuk.plugins.kesoid.importek.StavZdroju;

/** Změnil se stav některé položky zdrojů (zapnutí, načítání, postup, zámek, chyba, počty). */
public class StavZdrojuEvent extends Event0<KesoidModel> {
	private final StavZdroju stav;

	StavZdrojuEvent(final StavZdroju stav) {
		this.stav = stav;
	}

	public StavZdroju getStav() {
		return stav;
	}
}
