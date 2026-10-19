package cz.geokuk.plugins.mapy.sprava;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/** Jedna mapa z katalogu offline map. */
public final class KatalogovaMapa {

	private final String id;
	private final String nazev;
	private final String skupina;
	private final String celek;
	private final Instant datumNaServeru;
	private final List<URI> urlyMapy;
	private final List<URI> urlyPolygonu;
	private final long pribliznaVelikost;
	private final PolygonPokryti pokryti;
	private final String zdroj;
	private final String licence;
	private final String atribuce;

	KatalogovaMapa(final String id, final String nazev, final String skupina, final String celek, final Instant datumNaServeru, final List<URI> urlyMapy, final List<URI> urlyPolygonu, final long pribliznaVelikost,
			final PolygonPokryti pokryti, final String zdroj, final String licence, final String atribuce) {
		this.id = id;
		this.nazev = nazev;
		this.skupina = skupina;
		this.celek = celek;
		this.datumNaServeru = datumNaServeru;
		this.urlyMapy = urlyMapy;
		this.urlyPolygonu = urlyPolygonu;
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

	/** Datum mapy na serveru podle katalogu (Last-Modified při jeho sestavení), null když ho katalog neuvádí. */
	public Instant getDatumNaServeru() {
		return datumNaServeru;
	}

	/**
	 * Id celé země, když je mapa jejím regionem (například „europe/germany“ u „europe/germany/bayern“), jinak null. Katalog ho může určit sám, když celá země
	 * leží na serveru jinde (Velká Británie je „europe/great-britain“, regiony „europe/united-kingdom/…“).
	 */
	public String getIdCelku() {
		if (celek != null) {
			return celek;
		}
		final int i = id.lastIndexOf('/');
		final int j = id.indexOf('/');
		return i > j ? id.substring(0, i) : null;
	}

	/** Jméno souboru ve složce offline map. */
	public String getNazevSouboru() {
		return id.substring(id.lastIndexOf('/') + 1) + ".map";
	}

	/** Hlavní adresa mapy (zrcadlo). */
	public URI getUrlMapy() {
		return urlyMapy.get(0);
	}

	/** Adresy mapy v pořadí, v jakém se zkoušejí: hlavní, pak záložní. */
	public List<URI> getUrlyMapy() {
		return urlyMapy;
	}

	public URI getUrlPolygonu() {
		return urlyPolygonu.get(0);
	}

	public List<URI> getUrlyPolygonu() {
		return urlyPolygonu;
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
