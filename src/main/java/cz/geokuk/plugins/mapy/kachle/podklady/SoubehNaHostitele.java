package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.ToIntFunction;

import cz.geokuk.plugins.mapy.kachle.data.EKaType;

/** Počet souběžných stahování z jednoho serveru s omezením, sdílený všemi vlákny dávky; ostatní servery omezení nemají. */
class SoubehNaHostitele {

	private final ToIntFunction<String> omezeni;
	private final ConcurrentHashMap<String, Optional<Semaphore>> semafory = new ConcurrentHashMap<>();

	SoubehNaHostitele() {
		this(EKaType::omezeniSoubehuDavky);
	}

	/** @param omezeni nejvýše souběžných stahování z hostitele, 0 bez omezení */
	SoubehNaHostitele(final ToIntFunction<String> omezeni) {
		this.omezeni = omezeni;
	}

	/** Semafor hostitele, nebo null, když se souběh nemá omezovat. */
	Semaphore pro(final String hostitel) {
		return semafory.computeIfAbsent(hostitel.toLowerCase(Locale.ROOT), h -> {
			final int limit = omezeni.applyAsInt(h);
			return limit > 0 ? Optional.of(new Semaphore(limit)) : Optional.empty();
		}).orElse(null);
	}
}
