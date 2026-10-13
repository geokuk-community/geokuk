package cz.geokuk.plugins.kesoid.kind.kes;

import java.util.function.Function;

/** Údaje keše, které se z databáze jiného programu čtou až při zobrazení detailu. Jeden dotahovač je společný všem keším databáze. */
public interface DotahovacZDatabaze extends Function<String, String> {

	/** Nadmořská výška keše z databáze, 0 když tam není. Chyba čtení vyhodí {@link java.io.UncheckedIOException}. */
	default int nadmorskaVyska(final String kod) {
		return 0;
	}

	/** Dotahovač čte i nadmořskou výšku, takže se při načítání databáze nečte. */
	default boolean ctouVyskuAzPriZobrazeni() {
		return false;
	}
}
