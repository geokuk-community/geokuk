package cz.geokuk.plugins.kesoid;

import cz.geokuk.framework.Model0;

/** Drží, které zapnuté vrstvy kruhů se nekreslí; vrstvy sem hlásí svůj stav při změně výřezu, nastavení nebo dat, událost se rozešle jen při změně. */
public class StavVrstevModel extends Model0 {

	private StavVrstev stav = StavVrstev.prazdny();

	/** Stav vrstvy: důvod, proč se nekreslí; {@code KRESLI} nebo null, když se kreslí nebo je vypnutá. */
	public void nahlas(final VrstvaNadMapou vrstva, final DuvodNekresleni duvod, final int limit) {
		final StavVrstev novy = stav.s(vrstva, duvod, limit);
		if (novy.equals(stav)) {
			return;
		}
		stav = novy;
		fire(new StavVrstevEvent(novy));
	}

	public StavVrstev getStav() {
		return stav;
	}

	@Override
	protected void initAndFire() {
		fire(new StavVrstevEvent(stav));
	}
}
