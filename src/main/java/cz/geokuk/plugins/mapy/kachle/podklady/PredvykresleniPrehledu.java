package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import lombok.extern.slf4j.Slf4j;

/**
 * Předvykreslení přehledových zoomů offline map do cache v nečinnosti: při oddálení se pak dlaždice jen čtou z disku. Kreslí po jedné dlaždici a před každou
 * další se ptá, jestli nečinnost trvá; jakákoli akce uživatele ho tak zastaví nejpozději po dokončení rozdělané dlaždice.
 */
@Slf4j
final class PredvykresleniPrehledu {

	static final int Z_MIN = 3;
	static final int Z_MAX = 9;
	static final long NECINNOST_NS = TimeUnit.SECONDS.toNanos(10);

	/** Na jednom jádře by rozdělaná dlaždice zdržela okno. */
	static final int MIN_JADER = 2;

	static boolean jeVhodne(final int jader) {
		return jader >= MIN_JADER;
	}

	/** Offline mapy, ze kterých se předvykresluje. */
	interface Mapy {
		/** Klíč dlaždic aktuálních map, tématu a vrstev v cache; null, když mapy nejsou. */
		String klic() throws IOException;

		/** Dlaždice zadaných zoomů, které zasahují do map. */
		List<KaLoc> dlazdice(int zMin, int zMax) throws IOException;

		/** Dlaždice už je v cache pod tímto klíčem. */
		boolean jeVCache(KaLoc loc, String klic);

		/** Vykreslí dlaždici a zařadí ji k uložení; nic nedělá, když se mezitím změnil klíč. */
		void vykresli(KaLoc loc, String klic) throws IOException;
	}

	private final Mapy mapy;
	/** Nic jiného se nekreslí ani nestahuje. */
	private final BooleanSupplier volno;
	private final LongSupplier hodiny;

	private volatile long posledniAktivitaNs;

	private String klicFronty;
	private final Deque<KaLoc> fronta = new ArrayDeque<>();
	private String hotovyKlic;

	PredvykresleniPrehledu(final Mapy mapy, final BooleanSupplier volno, final LongSupplier hodiny) {
		this.mapy = mapy;
		this.volno = volno;
		this.hodiny = hodiny;
		posledniAktivitaNs = hodiny.getAsLong();
	}

	/** Uživatel něco udělal: předvykreslení se zastaví a pokračuje až po další nečinnosti. */
	void aktivita() {
		posledniAktivitaNs = hodiny.getAsLong();
	}

	boolean jeNecinnost() {
		return hodiny.getAsLong() - posledniAktivitaNs >= NECINNOST_NS && volno.getAsBoolean();
	}

	/**
	 * Kreslí dlaždice, dokud trvá nečinnost a je co kreslit. Volat z jednoho vlákna mimo EDT.
	 *
	 * @return kolik dlaždic vykreslil
	 */
	int pracuj() {
		int vykresleno = 0;
		while (jeNecinnost()) {
			try {
				final String klic = mapy.klic();
				if (klic == null || klic.equals(hotovyKlic)) {
					return vykresleno;
				}
				if (!klic.equals(klicFronty)) {
					fronta.clear();
					fronta.addAll(mapy.dlazdice(Z_MIN, Z_MAX));
					klicFronty = klic;
				}
				final KaLoc loc = fronta.poll();
				if (loc == null) {
					hotovyKlic = klic;
					log.info("Přehledové zoomy offline map předvykresleny ({})", klic);
					return vykresleno;
				}
				if (!mapy.jeVCache(loc, klic)) {
					mapy.vykresli(loc, klic);
					vykresleno++;
				}
			} catch (final IOException | RuntimeException e) {
				log.debug("Předvykreslení přehledu offline map přerušeno: {}", e.toString());
				return vykresleno;
			}
		}
		return vykresleno;
	}

	/** Kolik dlaždic zbývá ve frontě. */
	int zbyva() {
		return fronta.size();
	}
}
