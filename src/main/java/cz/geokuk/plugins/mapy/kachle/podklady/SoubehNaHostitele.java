package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.ToIntFunction;

import cz.geokuk.plugins.mapy.kachle.data.EKaType;

/** Počet souběžných stahování z jednoho serveru, sdílený všemi vlákny dávky. */
class SoubehNaHostitele {

	private final int vychozi;
	private final ToIntFunction<String> omezeni;
	private final ConcurrentHashMap<String, Semaphore> semafory = new ConcurrentHashMap<>();

	SoubehNaHostitele(final int vychozi) {
		this(vychozi, EKaType::omezeniSoubehuDavky);
	}

	/** @param omezeni nejvýše souběžných stahování z hostitele, 0 bez omezení */
	SoubehNaHostitele(final int vychozi, final ToIntFunction<String> omezeni) {
		this.vychozi = vychozi;
		this.omezeni = omezeni;
	}

	Semaphore pro(final String hostitel) {
		return semafory.computeIfAbsent(hostitel.toLowerCase(java.util.Locale.ROOT), h -> {
			final int limit = omezeni.applyAsInt(h);
			return new Semaphore(limit > 0 ? limit : vychozi);
		});
	}
}
