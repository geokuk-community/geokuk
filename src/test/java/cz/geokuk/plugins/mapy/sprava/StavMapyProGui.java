package cz.geokuk.plugins.mapy.sprava;

import java.time.Instant;

/** Stavy mapy pro testy GUI (konstruktory stavu jsou jen pro balíček správy map). */
public final class StavMapyProGui {

	private StavMapyProGui() {}

	public static StavMapy stav(final KatalogovaMapa m, final StavMapy.Mistni mistni, final StavMapy.Server server, final StavMapy.Faze faze, final long stazeno,
			final long celkem, final String chyba) {
		final boolean mame = mistni != StavMapy.Mistni.NEMAME;
		return StavMapy.novy(m).sMistni(mistni, mame ? Instant.parse("2026-08-21T00:00:00Z") : null, mame ? 600_000_000L : -1, stazeno > 0 && !mame)
				.sServerem(server).sFazi(faze, chyba).sPrubehem(stazeno, celkem, stazeno > 0 && !mame);
	}
}
