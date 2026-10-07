package cz.geokuk.plugins.kesoid.importek;

import java.util.concurrent.*;

/**
 * Po načtení, které ubralo hodně waypointů (vypnutý velký zdroj), se jednou spustí plný sběr odpadu, aby Java uvolněnou paměť vrátila systému. Mimo EDT a s odkladem, aby
 * starý bag už nikdo nedržel; pauza je úměrná zbylým datům.
 */
final class UvolneniPameti {

	private static final long ODKLAD_S = 3;
	private static final ScheduledExecutorService VLAKNO = Executors.newSingleThreadScheduledExecutor(r -> {
		final Thread t = new Thread(r, "Uvolnění paměti");
		t.setDaemon(true);
		return t;
	});
	private static ScheduledFuture<?> naplanovano;

	private UvolneniPameti() {}

	/** {@code predtim} je počet waypointů minulého bagu, nebo -1 před prvním načtením. */
	static boolean vyplatiSe(final int predtim, final int ted) {
		return predtim - ted >= 1000 && ted <= predtim * 4L / 5;
	}

	static synchronized void naplanuj() {
		if (naplanovano != null) {
			naplanovano.cancel(false);
		}
		naplanovano = VLAKNO.schedule(System::gc, ODKLAD_S, TimeUnit.SECONDS);
	}
}
