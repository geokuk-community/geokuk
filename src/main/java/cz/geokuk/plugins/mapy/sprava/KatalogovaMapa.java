package cz.geokuk.plugins.mapy.sprava;

import java.net.URI;

/** Jedna mapa z katalogu offline map. */
public final class KatalogovaMapa {

	private final String id;
	private final String nazev;
	private final String skupina;
	private final URI urlMapy;
	private final URI urlPolygonu;
	private final long pribliznaVelikost;
	private final PolygonPokryti pokryti;
	private final String zdroj;
	private final String licence;
	private final String atribuce;

	KatalogovaMapa(final String id, final String nazev, final String skupina, final URI urlMapy, final URI urlPolygonu, final long pribliznaVelikost,
			final PolygonPokryti pokryti, final String zdroj, final String licence, final String atribuce) {
		this.id = id;
		this.nazev = nazev;
		this.skupina = skupina;
		this.urlMapy = urlMapy;
		this.urlPolygonu = urlPolygonu;
		this.pribliznaVelikost = pribliznaVelikost;
		this.pokryti = pokryti;
		this.zdroj = zdroj;
		this.licence = licence;
		this.atribuce = atribuce;
	}

	/** Cesta na serveru bez přípony, například „europe/germany/bayern“. */
	public String getId() {
		return id;
	}

	public String getNazev() {
		return nazev;
	}

	/** Země u regionu (například „Německo“), jinak null. */
	public String getSkupina() {
		return skupina;
	}

	/** Jméno souboru ve složce offline map. */
	public String getNazevSouboru() {
		return id.substring(id.lastIndexOf('/') + 1) + ".map";
	}

	public URI getUrlMapy() {
		return urlMapy;
	}

	public URI getUrlPolygonu() {
		return urlPolygonu;
	}

	/** Velikost v bajtech podle katalogu; skutečnou řekne až server. */
	public long getPribliznaVelikost() {
		return pribliznaVelikost;
	}

	public PolygonPokryti getPokryti() {
		return pokryti;
	}

	public String getZdroj() {
		return zdroj;
	}

	public String getLicence() {
		return licence;
	}

	public String getAtribuce() {
		return atribuce;
	}

	@Override
	public String toString() {
		return id;
	}
}
