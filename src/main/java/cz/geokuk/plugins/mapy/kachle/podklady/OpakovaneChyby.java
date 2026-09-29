package cz.geokuk.plugins.mapy.kachle.podklady;

import lombok.extern.slf4j.Slf4j;

/**
 * Hlásí do logu chybu, která se opakuje u každé dlaždice. Kdyby se vypisovala
 * pokaždé, zahltí log tisíci stejnými výjimkami a hlášení o chybě je nečitelné,
 * proto se vypíše první výskyt a pak už jen každý desetinásobek.
 */
@Slf4j
class OpakovaneChyby {

	private final String co;

	private long pocet;

	private long dalsiHlaseni = 1;

	OpakovaneChyby(final String co) {
		this.co = co;
	}

	synchronized void ohlas(final Throwable chyba) {
		pocet++;
		if (pocet < dalsiHlaseni) {
			return;
		}
		dalsiHlaseni = pocet * 10;
		log.error("{} ({}. výskyt)", co, pocet, chyba);
	}
}
