package cz.geokuk.plugins.mapy.sprava;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Vrstva ke stažení k offline mapě (trasy, vrstevnice, výškový model pro stínování). Neměnná. */
public final class KatalogovaVrstva {

	public enum Typ {
		/** Turistické, cyklo a další trasy (.map). */
		TRASY("trasy"),
		/** Vrstevnice (.map). */
		VRSTEVNICE("vrstevnice"),
		/** Výškový model pro stínování (zip s dlaždicemi .hgt). */
		DEM("dem");

		private final String kod;

		Typ(final String kod) {
			this.kod = kod;
		}

		static Typ podleKodu(final String kod) {
			for (final Typ t : values()) {
				if (t.kod.equals(kod)) {
					return t;
				}
			}
			return null;
		}
	}

	public enum Format {
		/** Mapsforge .map. */
		MAP("map"),
		/** Zip s dlaždicemi SRTM .hgt. */
		HGT_ZIP("hgt-zip");

		private final String kod;

		Format(final String kod) {
			this.kod = kod;
		}

		static Format podleKodu(final String kod) {
			for (final Format f : values()) {
				if (f.kod.equals(kod)) {
					return f;
				}
			}
			return null;
		}
	}

	/** Jeden soubor vrstvy. */
	public static final class Soubor {
		private final String nazev;
		private final List<URI> urly;
		private final long velikost;
		private final String sha256;
		private final Instant datumSouboru;

		Soubor(final String nazev, final List<URI> urly, final long velikost, final String sha256, final Instant datumSouboru) {
			this.nazev = nazev;
			this.urly = urly;
			this.velikost = velikost;
			this.sha256 = sha256;
			this.datumSouboru = datumSouboru;
		}

		/** Jméno souboru ve složce vrstev. */
		public String getNazev() {
			return nazev;
		}

		/** Adresy v pořadí, v jakém se zkoušejí. */
		public List<URI> getUrly() {
			return urly;
		}

		public long getVelikost() {
			return velikost;
		}

		/** SHA-256 obsahu, malými písmeny. */
		public String getSha256() {
			return sha256;
		}

		/** Null = neuvedeno. */
		public Instant getDatumSouboru() {
			return datumSouboru;
		}

		@Override
		public String toString() {
			return nazev;
		}
	}

	private final String id;
	private final Typ typ;
	private final String region;
	private final String nazev;
	private final Format format;
	private final List<Soubor> soubory;
	private final long velikost;
	private final LocalDate datumDat;
	private final Instant vyrobeno;
	private final String zdrojDat;
	private final int zoomMin;
	private final int zoomMax;
	private final int krokVrstevnic;
	private final PolygonPokryti pokryti;
	private final String licence;
	private final String atribuce;
	private final String atribuceDlouha;
	private final String tema;
	private final String poznamka;

	KatalogovaVrstva(final String id, final Typ typ, final String region, final String nazev, final Format format, final List<Soubor> soubory, final long velikost,
			final LocalDate datumDat, final Instant vyrobeno, final String zdrojDat, final int zoomMin, final int zoomMax, final int krokVrstevnic,
			final PolygonPokryti pokryti, final String licence, final String atribuce, final String atribuceDlouha, final String tema, final String poznamka) {
		this.id = id;
		this.typ = typ;
		this.region = region;
		this.nazev = nazev;
		this.format = format;
		this.soubory = soubory;
		this.velikost = velikost;
		this.datumDat = datumDat;
		this.vyrobeno = vyrobeno;
		this.zdrojDat = zdrojDat;
		this.zoomMin = zoomMin;
		this.zoomMax = zoomMax;
		this.krokVrstevnic = krokVrstevnic;
		this.pokryti = pokryti;
		this.licence = licence;
		this.atribuce = atribuce;
		this.atribuceDlouha = atribuceDlouha;
		this.tema = tema;
		this.poznamka = poznamka;
	}

	/** Například „trasy/europe/czech-republic“. */
	public String getId() {
		return id;
	}

	public Typ getTyp() {
		return typ;
	}

	/** Id regionu jako u katalogové mapy, například „europe/czech-republic“. */
	public String getRegion() {
		return region;
	}

	public String getNazev() {
		return nazev;
	}

	public Format getFormat() {
		return format;
	}

	/** Soubory vrstvy; vrstva je stažená, až když jsou všechny. */
	public List<Soubor> getSoubory() {
		return soubory;
	}

	/** Součet velikostí souborů v bajtech. */
	public long getVelikost() {
		return velikost;
	}

	/** Stav vstupních dat; null = neuvedeno. */
	public LocalDate getDatumDat() {
		return datumDat;
	}

	/** Kdy byla vrstva vyrobena; null = neuvedeno. */
	public Instant getVyrobeno() {
		return vyrobeno;
	}

	/** Null = neuvedeno. */
	public String getZdrojDat() {
		return zdrojDat;
	}

	/** Nejmenší použitelný zoom, -1 = neuvedeno. */
	public int getZoomMin() {
		return zoomMin;
	}

	/** Největší použitelný zoom, -1 = neuvedeno. */
	public int getZoomMax() {
		return zoomMax;
	}

	/** Krok vrstevnic v metrech, 0 u jiných vrstev. */
	public int getKrokVrstevnic() {
		return krokVrstevnic;
	}

	/** Obrys pokrytí: u regionu z katalogu map jeho obrys, jinak obdélník; nikdy null. */
	public PolygonPokryti getPokryti() {
		return pokryti;
	}

	public String getLicence() {
		return licence;
	}

	/** Krátká atribuce, zobrazuje se vždy u zapnuté vrstvy. */
	public String getAtribuce() {
		return atribuce;
	}

	/** Plné znění atribuce pro „O mapě“; null = stačí krátká. */
	public String getAtribuceDlouha() {
		return atribuceDlouha;
	}

	/** Doporučené překryvné téma; null = neuvedeno. */
	public String getTema() {
		return tema;
	}

	/** Poznámka pro uživatele; null = žádná. */
	public String getPoznamka() {
		return poznamka;
	}

	@Override
	public String toString() {
		return id;
	}
}
