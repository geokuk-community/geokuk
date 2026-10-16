package cz.geokuk.plugins.mapy.sprava;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.io.ByteStreams;

import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva.Format;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva.Soubor;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva.Typ;
import lombok.extern.slf4j.Slf4j;

/** Katalog vrstev k offline mapám (trasy, vrstevnice, stínování). Vrstva se k mapě páruje podle id regionu. */
@Slf4j
public final class KatalogVrstev {

	private static final String VESTAVENY = "layers.json";
	/** Aktuální katalog vrstev: vždy u posledního vydání vrstev. */
	public static final URI URL_KATALOGU = URI.create("https://github.com/geokuk-community/geokuk-maps/releases/latest/download/layers.json");
	private static final int FORMAT = 1;
	private static final String ID = "[a-z0-9-]+(/[a-z0-9-]+)*";
	private static final String JMENO_SOUBORU = "[A-Za-z0-9][A-Za-z0-9._-]*";
	private static final String RELATIVNI_URL = "[A-Za-z0-9._~%-]+(/[A-Za-z0-9._~%-]+)*";

	private static volatile KatalogVrstev vestaveny;

	private final List<KatalogovaVrstva> vrstvy;

	private KatalogVrstev(final List<KatalogovaVrstva> vrstvy) {
		this.vrstvy = Collections.unmodifiableList(vrstvy);
	}

	/** Katalog z jaru; obrysy pokrytí bere z vestavěného katalogu map. */
	public static KatalogVrstev vestaveny() {
		KatalogVrstev k = vestaveny;
		if (k == null) {
			synchronized (KatalogVrstev.class) {
				k = vestaveny;
				if (k == null) {
					try (InputStream in = KatalogVrstev.class.getResourceAsStream(VESTAVENY)) {
						if (in == null) {
							throw new IOException("V programu chybí " + VESTAVENY);
						}
						k = nacti(new String(ByteStreams.toByteArray(in), StandardCharsets.UTF_8), KatalogMap.vestaveny());
					} catch (final IOException e) {
						throw new UncheckedIOException(e);
					}
					vestaveny = k;
				}
			}
		}
		return k;
	}

	/** Načte katalog ve formátu layers.json. Vrstvy neznámého typu nebo formátu přeskočí, ostatní vady jsou chyba. */
	public static KatalogVrstev nacti(final String json, final KatalogMap mapy) throws IOException {
		final Map<?, ?> koren = objekt(JsonCteni.precti(json), "katalog");
		final Object format = koren.get("version");
		if (!(format instanceof Double) || ((Double) format).intValue() != (Double) format) {
			throw new IOException("Katalog vrstev: chybí version");
		}
		if (((Double) format).intValue() > FORMAT) {
			throw new IOException("Katalog vrstev je v novějším formátu, než program zná");
		}
		final List<URI> zdroje = zdroje(koren.get("sources"));
		final Object polozky = koren.get("layers");
		if (!(polozky instanceof List)) {
			throw new IOException("Katalog vrstev nemá vrstvy");
		}
		final List<KatalogovaVrstva> vysledek = new ArrayList<>();
		final Set<String> idcka = new HashSet<>();
		final Set<String> soubory = new HashSet<>();
		for (final Object o : (List<?>) polozky) {
			final Map<?, ?> p = objekt(o, "vrstva");
			final String id = text(p, "id");
			final Typ typ = Typ.podleKodu(text(p, "type"));
			final Format fmt = Format.podleKodu(text(p, "format"));
			if (typ == null || fmt == null) {
				log.info("Vrstva {} má typ nebo formát, který program nezná, přeskakuje se", id);
				continue;
			}
			final KatalogovaVrstva v = vrstva(p, id, typ, fmt, zdroje, mapy);
			if (!idcka.add(id)) {
				throw new IOException("Katalog vrstev: id " + id + " je dvakrát");
			}
			for (final Soubor s : v.getSoubory()) {
				if (!soubory.add(s.getCesta().toLowerCase(Locale.ROOT))) {
					throw new IOException("Katalog vrstev: soubor " + s.getCesta() + " je dvakrát");
				}
			}
			vysledek.add(v);
		}
		return new KatalogVrstev(vysledek);
	}

	private static KatalogovaVrstva vrstva(final Map<?, ?> p, final String id, final Typ typ, final Format fmt, final List<URI> zdroje, final KatalogMap mapy)
			throws IOException {
		if (!id.matches(ID)) {
			throw new IOException("Neplatné id vrstvy: " + id);
		}
		final String region = text(p, "region");
		if (!region.matches(ID)) {
			throw new IOException("Vrstva " + id + " má neplatný region " + region);
		}
		final Object seznam = p.get("files");
		if (!(seznam instanceof List) || ((List<?>) seznam).isEmpty()) {
			throw new IOException("Vrstva " + id + " nemá soubory");
		}
		final List<Soubor> soubory = new ArrayList<>();
		long soucet = 0;
		for (final Object o : (List<?>) seznam) {
			final Soubor s = soubor(objekt(o, "soubor vrstvy " + id), id, zdroje, region + "." + typ.getKod(), fmt.getPripona());
			soucet += s.getVelikost();
			soubory.add(s);
		}
		final Object zoom = p.get("zoom");
		int zoomMin = -1;
		int zoomMax = -1;
		if (zoom != null) {
			final Map<?, ?> z = objekt(zoom, "zoom vrstvy " + id);
			zoomMin = cele(z.get("min"), "zoom vrstvy " + id);
			zoomMax = cele(z.get("max"), "zoom vrstvy " + id);
		}
		final Object krok = p.get("contourInterval");
		return new KatalogovaVrstva(id, typ, region, text(p, "name"), fmt, Collections.unmodifiableList(soubory), soucet, datumDat(p.get("dataDate"), id),
				okamzik(p.get("produced"), id), volitelny(p, "dataSource"), zoomMin, zoomMax, krok == null ? 0 : cele(krok, "krok vrstevnic " + id),
				pokryti(p.get("coverage"), region, id, mapy), text(p, "license"), text(p, "attribution"), volitelny(p, "attributionFull"),
				volitelny(p, "licenseText"), volitelny(p, "theme"), volitelny(p, "note"));
	}

	private static Soubor soubor(final Map<?, ?> s, final String id, final List<URI> zdroje, final String zacatekCesty, final String pripona) throws IOException {
		final String nazev = text(s, "name");
		if (!platneJmenoSouboru(nazev)) {
			throw new IOException("Vrstva " + id + " má neplatné jméno souboru " + nazev);
		}
		final String cesta = text(s, "path");
		if (!platnaCesta(cesta) || !cesta.matches(Pattern.quote(zacatekCesty) + "(\\.[a-z0-9_-]+)?" + Pattern.quote(pripona))) {
			throw new IOException("Vrstva " + id + " má neplatnou cestu souboru " + cesta + " (čekám " + zacatekCesty + pripona + ")");
		}
		final String url = text(s, "url");
		final List<URI> urly = new ArrayList<>();
		if (url.matches("https?://.+")) {
			urly.add(URI.create(url));
		} else if (url.matches(RELATIVNI_URL) && !url.matches("(.*/)?\\.\\.?(/.*)?")) {
			for (final URI z : zdroje) {
				urly.add(z.resolve(url));
			}
		} else {
			throw new IOException("Vrstva " + id + " má neplatnou adresu souboru " + url);
		}
		final Object velikost = s.get("size");
		if (!(velikost instanceof Double) || (Double) velikost <= 0 || (Double) velikost != Math.floor((Double) velikost)) {
			throw new IOException("Soubor " + nazev + " vrstvy " + id + " nemá velikost");
		}
		final String sha = text(s, "sha256").toLowerCase(Locale.ROOT);
		if (!sha.matches("[0-9a-f]{64}")) {
			throw new IOException("Soubor " + nazev + " vrstvy " + id + " nemá platný sha256");
		}
		return new Soubor(nazev, cesta, Collections.unmodifiableList(urly), ((Double) velikost).longValue(), sha, okamzik(s.get("date"), id));
	}

	/** Relativní cesta s lomítky, každá část platné jméno souboru. */
	static boolean platnaCesta(final String cesta) {
		for (final String cast : cesta.split("/", -1)) {
			if (!platneJmenoSouboru(cast)) {
				return false;
			}
		}
		return true;
	}

	/** Jméno souboru bez cesty, které jde založit i na Windows a nekoliduje s pomocnými soubory stahování. */
	static boolean platneJmenoSouboru(final String nazev) {
		final String male = nazev.toLowerCase(Locale.ROOT);
		for (final String pripona : new String[] { StahovacMapy.PRIPONA, StahovacMapy.PRIPONA_INFO, StahovacVrstev.PRIPONA_SOUCTU, StahovacVrstev.PRIPONA_STARA }) {
			if (male.endsWith(pripona)) {
				return false;
			}
		}
		final String zaklad = male.indexOf('.') < 0 ? male : male.substring(0, male.indexOf('.'));
		return nazev.matches(JMENO_SOUBORU) && !nazev.endsWith(".") && !zaklad.matches("con|prn|aux|nul|com[0-9]|lpt[0-9]");
	}

	/** Obrys regionu z katalogu map, jinak obdélník z katalogu vrstev. */
	private static PolygonPokryti pokryti(final Object o, final String region, final String id, final KatalogMap mapy) throws IOException {
		final Optional<KatalogovaMapa> mapa = mapy.podleId(region);
		if (mapa.isPresent()) {
			return mapa.get().getPokryti();
		}
		final Object bbox = o instanceof Map ? ((Map<?, ?>) o).get("bbox") : null;
		if (!(bbox instanceof List) || ((List<?>) bbox).size() != 4) {
			throw new IOException("Vrstva " + id + " nemá obrys ani obdélník pokrytí");
		}
		final double[] d = new double[4];
		for (int i = 0; i < 4; i++) {
			final Object c = ((List<?>) bbox).get(i);
			if (!(c instanceof Double)) {
				throw new IOException("Vrstva " + id + " má neplatný obdélník pokrytí");
			}
			d[i] = (Double) c;
		}
		try {
			return PolygonPokryti.obdelnik(d[0], d[1], d[2], d[3]);
		} catch (final IllegalArgumentException e) {
			throw new IOException("Vrstva " + id + " má neplatný obdélník pokrytí", e);
		}
	}

	private static List<URI> zdroje(final Object o) throws IOException {
		if (!(o instanceof List) || ((List<?>) o).isEmpty()) {
			throw new IOException("Katalog vrstev: chybí sources");
		}
		final List<URI> vysledek = new ArrayList<>();
		for (final Object a : (List<?>) o) {
			if (!(a instanceof String) || !((String) a).matches("https?://.+/")) {
				throw new IOException("Katalog vrstev: neplatný zdroj " + a);
			}
			vysledek.add(URI.create((String) a));
		}
		return vysledek;
	}

	/** ISO datum, nebo datum podle RFC 1123. */
	private static LocalDate datumDat(final Object o, final String id) throws IOException {
		if (o == null) {
			return null;
		}
		try {
			if (o instanceof String) {
				final String s = (String) o;
				return s.matches("\\d{4}-\\d{2}-\\d{2}") ? LocalDate.parse(s) : DateTimeFormatter.RFC_1123_DATE_TIME.parse(s, Instant::from).atOffset(ZoneOffset.UTC).toLocalDate();
			}
		} catch (final DateTimeParseException e) {
			// spadne do chyby níže
		}
		throw new IOException("Vrstva " + id + " má neplatné datum dat " + o);
	}

	private static Instant okamzik(final Object o, final String id) throws IOException {
		if (o == null) {
			return null;
		}
		try {
			if (o instanceof String) {
				return Instant.parse((String) o);
			}
		} catch (final DateTimeParseException e) {
			// spadne do chyby níže
		}
		throw new IOException("Vrstva " + id + " má neplatný čas " + o);
	}

	private static int cele(final Object o, final String co) throws IOException {
		if (!(o instanceof Double) || ((Double) o).intValue() != (Double) o || (Double) o < 0) {
			throw new IOException("Katalog vrstev: neplatný " + co);
		}
		return ((Double) o).intValue();
	}

	private static Map<?, ?> objekt(final Object o, final String co) throws IOException {
		if (!(o instanceof Map)) {
			throw new IOException("Katalog vrstev: " + co + " není objekt");
		}
		return (Map<?, ?>) o;
	}

	private static String text(final Map<?, ?> m, final String klic) throws IOException {
		final Object o = m.get(klic);
		if (!(o instanceof String) || ((String) o).isEmpty()) {
			throw new IOException("Katalog vrstev: chybí " + klic);
		}
		return (String) o;
	}

	private static String volitelny(final Map<?, ?> m, final String klic) {
		final Object o = m.get(klic);
		return o instanceof String && !((String) o).isEmpty() ? (String) o : null;
	}

	public List<KatalogovaVrstva> getVrstvy() {
		return vrstvy;
	}

	public Optional<KatalogovaVrstva> podleId(final String id) {
		return vrstvy.stream().filter(v -> v.getId().equals(id)).findFirst();
	}

	/** Vrstva, ke které patří soubor s touto cestou ve složce vrstev (s lomítky); prázdné = soubor uživatele. */
	public Optional<KatalogovaVrstva> podleCesty(final String cesta) {
		return vrstvy.stream().filter(v -> v.getSoubory().stream().anyMatch(s -> s.getCesta().equalsIgnoreCase(cesta))).findFirst();
	}

	/** Vrstva daného typu k základní mapě s tímto klíčem (cesta mapy na serveru bez přípony); pro sloupce Správy map. */
	public Optional<KatalogovaVrstva> vrstva(final String klic, final Typ typ) {
		return vrstvy.stream().filter(v -> v.getKlic().equals(klic) && v.getTyp() == typ).findFirst();
	}

	/**
	 * Vrstvy k mapě v pořadí katalogu. Když pro region mapy vrstva daného typu není, nabídne vrstvu celé země (u regionu), nebo vrstvy jejích regionů (u celé
	 * země).
	 */
	public List<KatalogovaVrstva> vrstvyProMapu(final KatalogovaMapa mapa) {
		final String id = mapa.getId();
		final List<KatalogovaVrstva> vysledek = new ArrayList<>();
		final Set<Typ> presne = EnumSet.noneOf(Typ.class);
		for (final KatalogovaVrstva v : vrstvy) {
			if (v.getRegion().equals(id)) {
				vysledek.add(v);
				presne.add(v.getTyp());
			}
		}
		final int lomitko = id.lastIndexOf('/');
		// U „europe/czech-republic“ je nadřazené „europe“ celý světadíl, ne země.
		final String celek = lomitko > 0 && id.indexOf('/') < lomitko ? id.substring(0, lomitko) : null;
		final Set<Typ> zCelku = EnumSet.noneOf(Typ.class);
		for (final KatalogovaVrstva v : vrstvy) {
			if (!presne.contains(v.getTyp()) && v.getRegion().equals(celek)) {
				vysledek.add(v);
				zCelku.add(v.getTyp());
			}
		}
		for (final KatalogovaVrstva v : vrstvy) {
			if (!presne.contains(v.getTyp()) && !zCelku.contains(v.getTyp()) && v.getRegion().startsWith(id + "/")) {
				vysledek.add(v);
			}
		}
		return vysledek;
	}
}
