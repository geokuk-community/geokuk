package cz.geokuk.plugins.mapy.sprava;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
						k = nacti(new String(ByteStreams.toByteArray(in), StandardCharsets.UTF_8), KatalogMap::vestavenyPolygon);
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
		final Map<?, ?> koren = objekt(JsonCteni.precti(json), "katalog");
		final URI mapy = URI.create(text(koren, "mapy"));
		final URI poly = URI.create(text(koren, "polygony"));
		final String zdroj = text(koren, "zdroj");
		final String licence = text(koren, "licence");
		final String atribuce = text(koren, "atribuce");
		final Object polozky = koren.get("polozky");
		if (!(polozky instanceof List)) {
			throw new IOException("Katalog map nemá položky");
		}
		final List<KatalogovaMapa> vysledek = new ArrayList<>();
		final Set<String> soubory = new HashSet<>();
		for (final Object o : (List<?>) polozky) {
			final Map<?, ?> p = objekt(o, "položka katalogu");
			final String id = text(p, "id");
			if (!id.matches("[a-z0-9-]+(/[a-z0-9-]+)*")) {
				throw new IOException("Neplatné id mapy v katalogu: " + id);
			}
			final Object skupina = p.get("skupina");
			final Object velikost = p.get("velikost");
			if (!(velikost instanceof Double) || (Double) velikost <= 0) {
				throw new IOException("Mapa " + id + " v katalogu nemá velikost");
			}
			PolygonPokryti pokryti = polygony.polygon(id);
			if (pokryti == null) {
				pokryti = obdelnik(p.get("obdelnik"), id);
			}
			final KatalogovaMapa m = new KatalogovaMapa(id, text(p, "nazev"), skupina instanceof String ? (String) skupina : null, mapy.resolve(id + ".map"),
					poly.resolve(id + ".poly"), ((Double) velikost).longValue(), pokryti, zdroj, licence, atribuce);
			if (!soubory.add(m.getNazevSouboru().toLowerCase(Locale.ROOT))) {
				throw new IOException("Dvě mapy v katalogu mají stejné jméno souboru " + m.getNazevSouboru());
			}
			vysledek.add(m);
		}
		return new KatalogMap(vysledek);
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

	/** Mapy, jejichž obrys pokrývá bod, od nejmenší (region dřív než země). */
	public List<KatalogovaMapa> pokryvajiBod(final Wgs bod) {
		final List<KatalogovaMapa> vysledek = new ArrayList<>();
		for (final KatalogovaMapa m : mapy) {
			if (m.getPokryti().obsahuje(bod)) {
				vysledek.add(m);
			}
		}
		vysledek.sort(Comparator.comparingDouble(m -> m.getPokryti().plocha()));
		return vysledek;
	}
}
