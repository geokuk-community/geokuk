package cz.geokuk.plugins.mapy.sprava;

import java.net.URI;
import java.time.Instant;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Supplier;

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
	/** Obrys pokrytí; u vestavěného katalogu se čte až při prvním použití. */
	private volatile PolygonPokryti pokryti;
	private final PolygonZdroj zdrojPokryti;
	/** Ohraničení obrysu {jih, západ, sever, východ} z katalogu; null = zjistí se z obrysu. */
	private final double[] ohraniceni;
	private final String zdroj;
	private final String licence;
	private final String atribuce;

	/** Obrys, který se načte až při prvním použití. */
	interface PolygonZdroj {
		PolygonPokryti nacti() throws IOException;
	}

	KatalogovaMapa(final String id, final String nazev, final String skupina, final String celek, final Instant datumNaServeru, final List<URI> urlyMapy, final List<URI> urlyPolygonu, final long pribliznaVelikost,
			final PolygonPokryti pokryti, final String zdroj, final String licence, final String atribuce) {
		this(id, nazev, skupina, celek, datumNaServeru, urlyMapy, urlyPolygonu, pribliznaVelikost, pokryti, null, null, zdroj, licence, atribuce);
	}

	KatalogovaMapa(final String id, final String nazev, final String skupina, final String celek, final Instant datumNaServeru, final List<URI> urlyMapy, final List<URI> urlyPolygonu, final long pribliznaVelikost,
			final PolygonPokryti pokryti, final PolygonZdroj zdrojPokryti, final double[] ohraniceni, final String zdroj, final String licence, final String atribuce) {
		this.id = id;
		this.nazev = nazev;
		this.skupina = skupina;
		this.celek = celek;
		this.datumNaServeru = datumNaServeru;
		this.urlyMapy = urlyMapy;
		this.urlyPolygonu = urlyPolygonu;
		this.pribliznaVelikost = pribliznaVelikost;
		this.pokryti = pokryti;
		this.zdrojPokryti = zdrojPokryti;
		this.ohraniceni = ohraniceni;
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
		PolygonPokryti p = pokryti;
		if (p == null) {
			synchronized (this) {
				p = pokryti;
				if (p == null) {
					try {
						p = zdrojPokryti.nacti();
					} catch (final IOException e) {
						throw new UncheckedIOException("Obrys mapy " + id + " nejde načíst", e);
					}
					pokryti = p;
				}
			}
		}
		return p;
	}

	/** Ohraničení obrysu {jih, západ, sever, východ}; z katalogu, a když ho nemá, z obrysu. */
	public double[] getOhraniceni() {
		if (ohraniceni != null) {
			return ohraniceni;
		}
		final PolygonPokryti p = getPokryti();
		return new double[] { p.getJih(), p.getZapad(), p.getSever(), p.getVychod() };
	}

	/** Bod leží v obrysu; obrys se čte, až když bod leží v ohraničení. */
	public boolean pokryva(final double lat, final double lon) {
		final double[] o = getOhraniceni();
		return lat >= o[0] && lat <= o[2] && lon >= o[1] && lon <= o[3] && getPokryti().obsahuje(lat, lon);
	}

	/** Ohraničení obrysu zasahuje do obdélníku; obrys se nečte. */
	public boolean zasahujeDo(final double jih, final double zapad, final double sever, final double vychod) {
		final double[] o = getOhraniceni();
		return !(o[0] > sever || o[2] < jih || o[1] > vychod || o[3] < zapad);
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
