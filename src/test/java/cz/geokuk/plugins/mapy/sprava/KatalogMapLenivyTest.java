package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.google.common.io.ByteStreams;

/** Obrysy vestavěného katalogu se čtou až při prvním použití a ohraničení v katalogu odpovídá obrysům. */
public class KatalogMapLenivyTest {

	private static String vestavenyJson() throws IOException {
		try (InputStream in = KatalogMap.class.getResourceAsStream("katalog-map.json")) {
			return new String(ByteStreams.toByteArray(in), StandardCharsets.UTF_8);
		}
	}

	private static PolygonPokryti polygon(final String id) throws IOException {
		try (InputStream in = KatalogMap.class.getResourceAsStream("poly/" + id + ".poly")) {
			if (in == null) {
				return null;
			}
			try (Reader r = new InputStreamReader(in, StandardCharsets.US_ASCII)) {
				return PolygonPokryti.nacti(r);
			}
		}
	}

	@Test
	public void obrysySeCtouAzPriPouziti() throws Exception {
		final Set<String> precteno = new HashSet<>();
		final KatalogMap k = KatalogMap.nacti(vestavenyJson(), id -> {
			precteno.add(id);
			return polygon(id);
		}, true);
		assertEquals("při načtení katalogu se žádný obrys nečte", 0, precteno.size());
		// Praha: jen mapy, jejichž ohraničení bod obsahuje, čtou obrys.
		final java.util.List<KatalogovaMapa> pokryvajici = k.pokryvajiBod(new cz.geokuk.core.coordinates.Wgs(50.08, 14.42));
		assertFalse(pokryvajici.isEmpty());
		assertTrue("čte se málo obrysů, ne celý katalog: " + precteno.size(), precteno.size() < 20 && precteno.size() >= pokryvajici.size());
		assertTrue(precteno.contains(pokryvajici.get(0).getId()));
	}

	@Test
	public void ohraniceniVKatalogu() throws Exception {
		final KatalogMap lenive = KatalogMap.nacti(vestavenyJson(), KatalogMapLenivyTest::polygon, true);
		for (final KatalogovaMapa m : lenive.getMapy()) {
			final PolygonPokryti p = polygon(m.getId());
			assertNotNull("vestavěná mapa " + m.getId() + " má obrys", p);
			assertArrayEquals("ohraničení mapy " + m.getId() + " v katalogu-map.json odpovídá obrysu", new double[] { p.getJih(), p.getZapad(), p.getSever(), p.getVychod() }, m.getOhraniceni(), 0);
		}
	}

	@Test
	public void lenivyKatalogRozhodujeStejnJakoNaceny() throws Exception {
		final KatalogMap lenive = KatalogMap.nacti(vestavenyJson(), KatalogMapLenivyTest::polygon, true);
		final KatalogMap naceny = KatalogMap.nacti(vestavenyJson(), KatalogMapLenivyTest::polygon, false);
		for (double lat = 35; lat <= 71; lat += 3.7) {
			for (double lon = -25; lon <= 45; lon += 4.3) {
				final Set<String> a = new HashSet<>();
				final Set<String> b = new HashSet<>();
				for (final KatalogovaMapa m : lenive.pokryvajiBod(new cz.geokuk.core.coordinates.Wgs(lat, lon))) {
					a.add(m.getId());
				}
				for (final KatalogovaMapa m : naceny.pokryvajiBod(new cz.geokuk.core.coordinates.Wgs(lat, lon))) {
					b.add(m.getId());
				}
				assertEquals("mapy pro " + lat + ", " + lon, b, a);
			}
		}
		assertEquals(naceny.getMapy().size(), lenive.getMapy().size());
	}

	@Test
	public void bezOhraniceniSeObrysCteHned() throws Exception {
		final AtomicInteger cteni = new AtomicInteger();
		final KatalogMap k = KatalogMap.nacti(
				"{\"zdroj\": \"t\", \"licence\": \"l\", \"atribuce\": \"a\", \"mapy\": \"https://x.org/m/\", \"polygony\": \"https://x.org/p/\", \"polozky\": ["
						+ "{\"id\": \"a\", \"nazev\": \"A\", \"velikost\": 1, \"obdelnik\": [0, 0, 1, 1]}]}",
				id -> {
					cteni.incrementAndGet();
					return null;
				}, true);
		assertEquals("bez ohraničení v katalogu se obrys čte při načtení", 1, cteni.get());
		assertTrue(k.getMapy().get(0).pokryva(0.5, 0.5));
		assertFalse(k.getMapy().get(0).pokryva(2, 2));
	}

	@Test
	public void ohraniceniSeObdelnikemBezObrysuPouzijeObdelnik() throws Exception {
		final KatalogMap k = KatalogMap.nacti(
				"{\"zdroj\": \"t\", \"licence\": \"l\", \"atribuce\": \"a\", \"mapy\": \"https://x.org/m/\", \"polygony\": \"https://x.org/p/\", \"polozky\": ["
						+ "{\"id\": \"a\", \"nazev\": \"A\", \"velikost\": 1, \"ohraniceni\": [0, 0, 1, 1], \"obdelnik\": [0, 0, 1, 1]}]}",
				id -> null, true);
		final KatalogovaMapa m = k.getMapy().get(0);
		assertTrue(m.pokryva(0.5, 0.5));
		assertFalse(m.pokryva(1.5, 0.5));
		assertTrue(m.zasahujeDo(0.9, 0.9, 2, 2));
		assertFalse(m.zasahujeDo(1.1, 0, 2, 1));
	}

	@Test(expected = IOException.class)
	public void neplatneOhraniceni() throws Exception {
		KatalogMap.nacti("{\"zdroj\": \"t\", \"licence\": \"l\", \"atribuce\": \"a\", \"mapy\": \"https://x.org/m/\", \"polygony\": \"https://x.org/p/\", \"polozky\": ["
				+ "{\"id\": \"a\", \"nazev\": \"A\", \"velikost\": 1, \"ohraniceni\": [1, 0, 0, 1], \"obdelnik\": [0, 0, 1, 1]}]}", id -> null, true);
	}

	@Test
	public void mapaBezObrysuAObdelnikuHlasiChybuAzPriPouziti() throws Exception {
		final KatalogMap k = KatalogMap.nacti(
				"{\"zdroj\": \"t\", \"licence\": \"l\", \"atribuce\": \"a\", \"mapy\": \"https://x.org/m/\", \"polygony\": \"https://x.org/p/\", \"polozky\": ["
						+ "{\"id\": \"a\", \"nazev\": \"A\", \"velikost\": 1, \"ohraniceni\": [0, 0, 1, 1]}]}",
				id -> null, true);
		try {
			k.getMapy().get(0).getPokryti();
			fail("očekávána chyba");
		} catch (final java.io.UncheckedIOException e) {
			assertTrue(e.getCause().getMessage(), e.getCause().getMessage().contains("obrys"));
		}
	}
}
