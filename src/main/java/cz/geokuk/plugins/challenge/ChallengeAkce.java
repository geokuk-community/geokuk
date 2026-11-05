package cz.geokuk.plugins.challenge;

/** Společné vlastnosti akcí plánovače: stav se bere z {@link ChallengeModel}. */
final class ChallengeAkce {

	private ChallengeAkce() {}

	/** Akce „další/předchozí chybějící“ a filtr jsou dostupné, když je vidět pruh plánovače. */
	static boolean dostupne(final ChallengeModel model) {
		return model != null && model.jePruhViditelny();
	}
}
