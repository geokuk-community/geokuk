package cz.geokuk.framework;

import cz.geokuk.core.napoveda.Diagnostika;

/** Čekání v testech, až se chyba ohlášená na pozadí objeví v diagnostice. */
public final class ChybyVDiagnostice {

	public static int pocet(final String okolnost) {
		final String text = Diagnostika.text();
		int pocet = 0;
		for (int i = text.indexOf(okolnost); i >= 0; i = text.indexOf(okolnost, i + 1)) {
			pocet++;
		}
		return pocet;
	}

	/** Vrátí true, když počet výskytů okolnosti do 10 s vzroste nad zadaný. */
	public static boolean pribude(final String okolnost, final int puvodne) throws InterruptedException {
		final long konec = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < konec) {
			if (pocet(okolnost) > puvodne) {
				return true;
			}
			Thread.sleep(50);
		}
		return false;
	}

	private ChybyVDiagnostice() {}
}
