package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import cz.geokuk.plugins.mapy.kachle.data.EKaType;

/** Počet souběžných stahování z jednoho serveru, sdílený všemi vlákny dávky. */
class SoubehNaHostitele {

	private final int vychozi;
	private final ConcurrentHashMap<String, Semaphore> semafory = new ConcurrentHashMap<>();

	SoubehNaHostitele(final int vychozi) {
		this.vychozi = vychozi;
	}

	Semaphore pro(final String hostitel) {
		return semafory.computeIfAbsent(hostitel.toLowerCase(java.util.Locale.ROOT), h -> {
			final int omezeni = EKaType.omezeniSoubehuDavky(h);
			return new Semaphore(omezeni > 0 ? omezeni : vychozi);
		});
	}
}
