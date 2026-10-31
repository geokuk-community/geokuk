package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import org.mapsforge.core.util.MercatorProjection;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import lombok.extern.slf4j.Slf4j;

/**
 * Předvykreslení přehledových zoomů offline map do cache v nečinnosti: při oddálení se pak dlaždice jen čtou z disku. Po přehledu kreslí ještě z10–12 v okolí
 * zobrazeného místa. Kreslí po jedné dlaždici a před každou další se ptá, jestli nečinnost trvá; jakákoli akce uživatele ho tak zastaví nejpozději po
 * dokončení rozdělané dlaždice.
 */
@Slf4j
final class PredvykresleniPrehledu {

	static final int Z_MIN = 3;
	static final int Z_MAX = 9;
	static final int Z_OKOLI_MIN = 10;
	static final int Z_OKOLI_MAX = 12;
	static final double OKRUH_KM = 15;
	/** Nejvíc nově vykreslených dlaždic okolí na jeden klíč map, aby cache rychle nerostla. */
	static final int STROP_OKOLI = 300;
	private static final double KM_NA_STUPEN = 111.2;

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

		/** Přehledové zoomy pod tímto klíčem byly předvykresleny už dřív (třeba před restartem). */
		default boolean jeHotovo(final String klic) {
			return false;
		}

		/** Zapamatuje si, že přehledové zoomy pod tímto klíčem jsou předvykresleny. */
		default void zapisHotovo(final String klic) {}

		/** Vykreslí dlaždici, která v cache chybí; false, když už v cache je, nebo se mezitím změnil klíč či dlaždice není v mapách. */
		default boolean vykresliChybejici(final KaLoc loc, final String klic) throws IOException {
			return !jeVCache(loc, klic) && vykresli(loc, klic);
		}

		/** Vykreslí dlaždici a zařadí ji k uložení; nic nedělá a vrátí false, když se mezitím změnil klíč nebo dlaždice není v mapách. */
		boolean vykresli(KaLoc loc, String klic) throws IOException;
	}

	private final Mapy mapy;
	/** Nic jiného se nekreslí ani nestahuje. */
	private final BooleanSupplier volno;
	private final LongSupplier hodiny;
	/** Až po prvním pohledu na mapu; do té doby se nečinnost počítá od okamžiku, kdy to platí. */
	private final BooleanSupplier poPrvnimPohledu;

	private volatile long posledniAktivitaNs;

	private String klicFronty;
	private final Deque<KaLoc> fronta = new ArrayDeque<>();
	private String hotovyKlic;
	/** Klíč, u kterého se už zjišťovalo, jestli je hotový z dřívějška. */
	private String zjistenyKlic;

	/** Střed zobrazeného výřezu; null, dokud není známý. */
	private volatile Wgs pozice;
	private Wgs poziceFronty;
	private final Deque<KaLoc> frontaOkoli = new ArrayDeque<>();
	private String klicOkoli;
	private int vykreslenoOkoli;
	private boolean zOkoli;

	PredvykresleniPrehledu(final Mapy mapy, final BooleanSupplier volno, final LongSupplier hodiny) {
		this(mapy, volno, hodiny, () -> true);
	}

	PredvykresleniPrehledu(final Mapy mapy, final BooleanSupplier volno, final LongSupplier hodiny, final BooleanSupplier poPrvnimPohledu) {
		this.mapy = mapy;
		this.volno = volno;
		this.hodiny = hodiny;
		this.poPrvnimPohledu = poPrvnimPohledu;
		posledniAktivitaNs = hodiny.getAsLong();
	}

	/** Uživatel něco udělal: předvykreslení se zastaví a pokračuje až po další nečinnosti. */
	void aktivita() {
		posledniAktivitaNs = hodiny.getAsLong();
	}

	/** Místo, kolem kterého se po přehledu předvykreslí z10–12. */
	void setPozice(final Wgs pozice) {
		this.pozice = pozice;
	}

	boolean jeNecinnost() {
		if (!poPrvnimPohledu.getAsBoolean()) {
			posledniAktivitaNs = hodiny.getAsLong();
			return false;
		}
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
				if (klic == null) {
					return vykresleno;
				}
				final KaLoc loc = dalsi(klic);
				if (loc == null) {
					return vykresleno;
				}
				if (mapy.vykresliChybejici(loc, klic)) {
					vykresleno++;
					if (zOkoli) {
						vykreslenoOkoli++;
					}
				}
			} catch (final IOException | RuntimeException e) {
				log.debug("Předvykreslení přehledu offline map přerušeno: {}", e.toString());
				return vykresleno;
			}
		}
		return vykresleno;
	}

	private KaLoc dalsi(final String klic) throws IOException {
		zOkoli = false;
		if (!klic.equals(hotovyKlic) && !klic.equals(zjistenyKlic) && mapy.jeHotovo(klic)) {
			hotovyKlic = klic;
			log.debug("Přehledové zoomy offline map jsou předvykresleny z dřívějška ({})", klic);
		}
		zjistenyKlic = klic;
		if (!klic.equals(hotovyKlic)) {
			if (!klic.equals(klicFronty)) {
				fronta.clear();
				fronta.addAll(mapy.dlazdice(Z_MIN, Z_MAX));
				klicFronty = klic;
			}
			final KaLoc loc = fronta.poll();
			if (loc != null) {
				return loc;
			}
			hotovyKlic = klic;
			mapy.zapisHotovo(klic);
			log.info("Přehledové zoomy offline map předvykresleny ({})", klic);
		}
		if (!klic.equals(klicOkoli)) {
			klicOkoli = klic;
			vykreslenoOkoli = 0;
			poziceFronty = null;
		}
		final Wgs p = pozice;
		if (p == null || vykreslenoOkoli >= STROP_OKOLI) {
			return null;
		}
		if (!p.equals(poziceFronty)) {
			frontaOkoli.clear();
			frontaOkoli.addAll(okoli(p, OKRUH_KM));
			poziceFronty = p;
		}
		zOkoli = true;
		return frontaOkoli.poll();
	}

	/** Dlaždice z12 až z10 ve čtverci kolem místa, v každém zoomu od středu. */
	static List<KaLoc> okoli(final Wgs stred, final double km) {
		final double dLat = km / KM_NA_STUPEN;
		final double dLon = dLat / Math.max(0.1, Math.cos(Math.toRadians(stred.lat)));
		final double sever = Math.min(MercatorProjection.LATITUDE_MAX, stred.lat + dLat);
		final double jih = Math.max(MercatorProjection.LATITUDE_MIN, stred.lat - dLat);
		final List<KaLoc> vysledek = new ArrayList<>();
		for (int z = Z_OKOLI_MAX; z >= Z_OKOLI_MIN; z--) {
			final byte zoom = (byte) z;
			final int cx = MercatorProjection.longitudeToTileX(stred.lon, zoom);
			final int cy = MercatorProjection.latitudeToTileY(stred.lat, zoom);
			final List<int[]> zoomu = new ArrayList<>();
			for (int y = MercatorProjection.latitudeToTileY(sever, zoom); y <= MercatorProjection.latitudeToTileY(jih, zoom); y++) {
				for (int x = MercatorProjection.longitudeToTileX(Math.max(-180, stred.lon - dLon), zoom); x <= MercatorProjection.longitudeToTileX(Math.min(180, stred.lon + dLon), zoom); x++) {
					zoomu.add(new int[] { x, y, (x - cx) * (x - cx) + (y - cy) * (y - cy) });
				}
			}
			zoomu.sort(Comparator.comparingInt(t -> t[2]));
			for (final int[] t : zoomu) {
				vysledek.add(KaLoc.ofXyz(t[0], t[1], z));
			}
		}
		return vysledek;
	}

	/** Kolik dlaždic zbývá ve frontě. */
	int zbyva() {
		return fronta.size();
	}
}
