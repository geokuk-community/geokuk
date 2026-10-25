package cz.geokuk.plugins.mapy.sprava;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.io.ByteStreams;

import cz.geokuk.core.coordinates.Wgs;

/** Katalog offline map ke stažení. Vestavěný je v jaru i s obrysy pokrytí, takže nepotřebuje síť. */
public final class KatalogMap {

	private static final String VESTAVENY = "katalog-map.json";

	private static volatile KatalogMap vestaveny;

	private final List<KatalogovaMapa> mapy;

	private KatalogMap(final List<KatalogovaMapa> mapy) {
		this.mapy = Collections.unmodifiableList(mapy);
	}

	public static KatalogMap vestaveny() {
		KatalogMap k = vestaveny;
		if (k == null) {
			synchronized (KatalogMap.class) {
				k = vestaveny;
				if (k == null) {
					try (InputStream in = KatalogMap.class.getResourceAsStream(VESTAVENY)) {
						if (in == null) {
							throw new IOException("V programu chybí " + VESTAVENY);
						}
						k = nacti(new String(ByteStreams.toByteArray(in), StandardCharsets.UTF_8), KatalogMap::vestavenyPolygon, true);
					} catch (final IOException e) {
						throw new UncheckedIOException(e);
					}
					vestaveny = k;
				}
			}
		}
		return k;
	}

	/** Zdroj obrysu pokrytí podle id mapy; null = obrys není, použije se obdélník z katalogu. */
	interface ZdrojPolygonu {
		PolygonPokryti polygon(String id) throws IOException;
	}

	private static PolygonPokryti vestavenyPolygon(final String id) throws IOException {
		try (InputStream in = KatalogMap.class.getResourceAsStream("poly/" + id + ".poly")) {
			if (in == null) {
				return null;
			}
			try (Reader r = new InputStreamReader(in, StandardCharsets.US_ASCII)) {
				return PolygonPokryti.nacti(r);
			}
		}
	}

	static KatalogMap nacti(final String json, final ZdrojPolygonu polygony) throws IOException {
		return nacti(json, polygony, false);
	}

	/** Obrysy se s {@code lenive} nečtou při načtení katalogu, ale při prvním použití; mapa bez obrysu a bez obdélníku pak hlásí chybu až tehdy. */
	static KatalogMap nacti(final String json, final ZdrojPolygonu polygony, final boolean lenive) throws IOException {
		final Map<?, ?> koren = objekt(JsonCteni.precti(json), "katalog");
		final List<URI> mapy = adresy(koren, "mapy");
		final List<URI> poly = adresy(koren, "polygony");
		final String zdroj = text(koren, "zdroj");
		final String licence = text(koren, "licence");
		final String atribuce = text(koren, "atribuce");
		final Object polozky = koren.get("polozky");
		if (!(polozky instanceof List)) {
			throw new IOException("Katalog map nemá položky");
		}
		final List<KatalogovaMapa> vysledek = new ArrayList<>();
		final Set<String> soubory = new HashSet<>();
		final Map<String, String> celky = new HashMap<>();
		for (final Object o : (List<?>) polozky) {
			final Map<?, ?> p = objekt(o, "položka katalogu");
			final String id = text(p, "id");
			if (!id.matches("[a-z0-9-]+(/[a-z0-9-]+)*")) {
				throw new IOException("Neplatné id mapy v katalogu: " + id);
			}
			final Object skupina = p.get("skupina");
			final String celek = p.get("celek") instanceof String ? (String) p.get("celek") : null;
			if (celek != null) {
				celky.put(id, celek);
			}
			final Object velikost = p.get("velikost");
			if (!(velikost instanceof Double) || (Double) velikost <= 0) {
				throw new IOException("Mapa " + id + " v katalogu nemá velikost");
			}
			final double[] ohraniceni = lenive ? ohraniceni(p.get("ohraniceni"), id) : null;
			final Object obdelnik = p.get("obdelnik");
			PolygonPokryti pokryti = null;
			KatalogovaMapa.PolygonZdroj zdrojPokryti = null;
			if (lenive && ohraniceni != null) {
				zdrojPokryti = () -> {
					final PolygonPokryti z = polygony.polygon(id);
					return z != null ? z : obdelnik(obdelnik, id);
				};
			} else {
				pokryti = polygony.polygon(id);
				if (pokryti == null) {
					pokryti = obdelnik(obdelnik, id);
				}
			}
			final KatalogovaMapa m = new KatalogovaMapa(id, text(p, "nazev"), skupina instanceof String ? (String) skupina : null, celek, datum(p.get("datum"), id),
					vyres(mapy, id + ".map"), vyres(poly, id + ".poly"), ((Double) velikost).longValue(), pokryti, zdrojPokryti, ohraniceni, zdroj, licence, atribuce);
			if (!soubory.add(m.getNazevSouboru().toLowerCase(Locale.ROOT))) {
				throw new IOException("Dvě mapy v katalogu mají stejné jméno souboru " + m.getNazevSouboru());
			}
			vysledek.add(m);
		}
		final KatalogMap k = new KatalogMap(vysledek);
		for (final Map.Entry<String, String> e : celky.entrySet()) {
			if (!k.podleId(e.getValue()).isPresent()) {
				throw new IOException("Mapa " + e.getKey() + " má v katalogu neznámou celou zemi " + e.getValue());
			}
		}
		return k;
	}

	/** Datum ve tvaru hlavičky Last-Modified (RFC 1123); chybí = null. */
	private static Instant datum(final Object o, final String id) throws IOException {
		if (o == null) {
			return null;
		}
		try {
			return ZonedDateTime.parse(String.valueOf(o), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
		} catch (final DateTimeParseException e) {
			throw new IOException("Mapa " + id + " má v katalogu neplatné datum: " + o, e);
		}
	}

	/** Jedna adresa, nebo seznam (první je hlavní, další záloha). */
	private static List<URI> adresy(final Map<?, ?> m, final String klic) throws IOException {
		final Object o = m.get(klic);
		final List<?> seznam = o instanceof List ? (List<?>) o : Collections.singletonList(o);
		final List<URI> vysledek = new ArrayList<>();
		for (final Object a : seznam) {
			if (!(a instanceof String) || !((String) a).matches("https?://.+/")) {
				throw new IOException("Katalog map: neplatná adresa v " + klic);
			}
			vysledek.add(URI.create((String) a));
		}
		if (vysledek.isEmpty()) {
			throw new IOException("Katalog map: chybí " + klic);
		}
		return vysledek;
	}

	private static List<URI> vyres(final List<URI> zaklady, final String cesta) {
		final List<URI> vysledek = new ArrayList<>(zaklady.size());
		for (final URI z : zaklady) {
			vysledek.add(z.resolve(cesta));
		}
		return Collections.unmodifiableList(vysledek);
	}

	/** Ohraničení obrysu {jih, západ, sever, východ} z katalogu; chybí = null. */
	private static double[] ohraniceni(final Object o, final String id) throws IOException {
		if (o == null) {
			return null;
		}
		if (!(o instanceof List) || ((List<?>) o).size() != 4) {
			throw new IOException("Mapa " + id + " má v katalogu neplatné ohraničení");
		}
		final double[] d = new double[4];
		for (int i = 0; i < 4; i++) {
			final Object c = ((List<?>) o).get(i);
			if (!(c instanceof Double)) {
				throw new IOException("Mapa " + id + " má v katalogu neplatné ohraničení");
			}
			d[i] = (Double) c;
		}
		if (!(d[0] < d[2]) || !(d[1] < d[3])) {
			throw new IOException("Mapa " + id + " má v katalogu prázdné ohraničení");
		}
		return d;
	}

	private static PolygonPokryti obdelnik(final Object o, final String id) throws IOException {
		if (!(o instanceof List) || ((List<?>) o).size() != 4) {
			throw new IOException("Mapa " + id + " v katalogu nemá obrys ani obdélník");
		}
		final double[] d = new double[4];
		for (int i = 0; i < 4; i++) {
			final Object c = ((List<?>) o).get(i);
			if (!(c instanceof Double)) {
				throw new IOException("Mapa " + id + " má v katalogu neplatný obdélník");
			}
			d[i] = (Double) c;
		}
		try {
			return PolygonPokryti.obdelnik(d[0], d[1], d[2], d[3]);
		} catch (final IllegalArgumentException e) {
			throw new IOException("Mapa " + id + " má v katalogu neplatný obdélník", e);
		}
	}

	private static Map<?, ?> objekt(final Object o, final String co) throws IOException {
		if (!(o instanceof Map)) {
			throw new IOException("Katalog map: " + co + " není objekt");
		}
		return (Map<?, ?>) o;
	}

	private static String text(final Map<?, ?> m, final String klic) throws IOException {
		final Object o = m.get(klic);
		if (!(o instanceof String) || ((String) o).isEmpty()) {
			throw new IOException("Katalog map: chybí " + klic);
		}
		return (String) o;
	}

	public List<KatalogovaMapa> getMapy() {
		return mapy;
	}

	public Optional<KatalogovaMapa> podleId(final String id) {
		return mapy.stream().filter(m -> m.getId().equals(id)).findFirst();
	}

	/** Katalogová mapa uložená pod tímto jménem souboru; prázdné = soubor uživatele. */
	public Optional<KatalogovaMapa> podleSouboru(final String nazevSouboru) {
		return mapy.stream().filter(m -> m.getNazevSouboru().equalsIgnoreCase(nazevSouboru)).findFirst();
	}

	/** Celá země, jejímž regionem mapa je, když je v katalogu. */
	public Optional<KatalogovaMapa> celek(final KatalogovaMapa region) {
		return region.getIdCelku() == null ? Optional.empty() : podleId(region.getIdCelku());
	}

	/** Regiony celé země v pořadí katalogu; prázdné, když mapa regiony nemá. */
	public List<KatalogovaMapa> regiony(final KatalogovaMapa celek) {
		final List<KatalogovaMapa> vysledek = new ArrayList<>();
		for (final KatalogovaMapa m : mapy) {
			if (celek.getId().equals(m.getIdCelku())) {
				vysledek.add(m);
			}
		}
		return vysledek;
	}

	/** Mapy, jejichž obrys pokrývá bod, od nejmenší (region dřív než země). */
	public List<KatalogovaMapa> pokryvajiBod(final Wgs bod) {
		final List<KatalogovaMapa> vysledek = new ArrayList<>();
		for (final KatalogovaMapa m : mapy) {
			if (m.pokryva(bod.lat, bod.lon)) {
				vysledek.add(m);
			}
		}
		vysledek.sort(Comparator.comparingDouble(m -> m.getPokryti().plocha()));
		return vysledek;
	}
}
