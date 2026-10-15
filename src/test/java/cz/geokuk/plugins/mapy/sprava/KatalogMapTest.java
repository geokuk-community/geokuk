package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;

public class KatalogMapTest {

	private static final String HLAVA = "{\"verze\": 1, \"zdroj\": \"server\", \"licence\": \"ODbL 1.0\", \"atribuce\": \"© OSM\", "
			+ "\"mapy\": \"https://example.org/maps/v5/\", \"polygony\": \"https://example.org/maps/poly/\", \"polozky\": [";

	private static KatalogMap katalog(final String polozky, final KatalogMap.ZdrojPolygonu polygony) throws IOException {
		return KatalogMap.nacti(HLAVA + polozky + "]}", polygony);
	}

	@Test
	public void vestavenyKatalog() {
		final KatalogMap k = KatalogMap.vestaveny();
		assertSame(k, KatalogMap.vestaveny());
		assertTrue(k.getMapy().size() >= 20);
		final KatalogovaMapa cr = k.podleId("europe/czech-republic").get();
		assertEquals("Česko", cr.getNazev());
		assertNull(cr.getSkupina());
		assertEquals("czech-republic.map", cr.getNazevSouboru());
		assertEquals("https://ftp-stud.hs-esslingen.de/pub/Mirrors/download.mapsforge.org/maps/v5/europe/czech-republic.map", cr.getUrlMapy().toString());
		assertEquals("https://download.mapsforge.org/maps/v5/europe/czech-republic.map", cr.getUrlyMapy().get(1).toString());
		assertEquals("https://ftp-stud.hs-esslingen.de/pub/Mirrors/download.mapsforge.org/maps/poly/europe/czech-republic.poly", cr.getUrlPolygonu().toString());
		assertEquals("https://download.mapsforge.org/maps/poly/europe/czech-republic.poly", cr.getUrlyPolygonu().get(1).toString());
		assertEquals(605_630_432L, cr.getPribliznaVelikost());
		assertEquals("ODbL 1.0", cr.getLicence());
		assertEquals("© přispěvatelé OpenStreetMap", cr.getAtribuce());
		assertEquals("Německo", k.podleId("europe/germany/bayern").get().getSkupina());
		assertSame(cr, k.podleSouboru("Czech-Republic.MAP").get());
		assertFalse(k.podleSouboru("paws_cz.map").isPresent());
		assertTrue(k.pokryvajiBod(new Wgs(0, 0)).isEmpty());
		for (final KatalogovaMapa m : k.getMapy()) {
			assertTrue(m.getId(), m.getUrlyMapy().get(1).toString().startsWith("https://download.mapsforge.org/maps/v5/europe/"));
			assertNotNull("obrys pokrytí v jaru: " + m.getId(), KatalogMap.class.getResource("poly/" + m.getId() + ".poly"));
			assertNotNull("datum na serveru: " + m.getId(), m.getDatumNaServeru());
		}
	}

	@Test
	public void pokrytiPodleSkutecnychObrysu() {
		final KatalogMap k = KatalogMap.vestaveny();
		assertEquals(Arrays.asList("europe/czech-republic"), ids(k.pokryvajiBod(new Wgs(50.08, 14.42)))); // Praha
		assertEquals(Arrays.asList("europe/czech-republic"), ids(k.pokryvajiBod(new Wgs(50.08, 12.37)))); // Cheb
		assertEquals("Mnichov: region před zemí", "europe/germany/bayern", k.pokryvajiBod(new Wgs(48.14, 11.58)).get(0).getId());
		assertEquals("europe/germany/sachsen", k.pokryvajiBod(new Wgs(51.05, 13.74)).get(0).getId()); // Drážďany
		assertEquals("europe/austria", k.pokryvajiBod(new Wgs(48.21, 16.37)).get(0).getId()); // Vídeň
		assertEquals("europe/slovakia", k.pokryvajiBod(new Wgs(48.15, 17.11)).get(0).getId()); // Bratislava
		assertEquals("europe/poland", k.pokryvajiBod(new Wgs(50.06, 19.94)).get(0).getId()); // Krakov
		assertEquals("europe/germany/berlin", k.pokryvajiBod(new Wgs(52.52, 13.40)).get(0).getId());
		assertTrue("Paříž", k.pokryvajiBod(new Wgs(48.86, 2.35)).isEmpty());
	}

	@Test
	public void obrysZeZdrojeMaPrednostAMensiMapaJdePrvni() throws IOException {
		final Map<String, String> poly = new HashMap<>();
		poly.put("europe/zeme", "z\n1\n10 40\n20 40\n20 50\n10 50\nEND\nEND\n");
		poly.put("europe/zeme/region", "r\n1\n10 40\n12 40\n12 42\n10 42\nEND\nEND\n");
		final KatalogMap k = katalog("{\"id\": \"europe/zeme/region\", \"nazev\": \"Region\", \"skupina\": \"Země\", \"velikost\": 5, \"obdelnik\": [0, 0, 1, 1]},"
				+ "{\"id\": \"europe/zeme\", \"nazev\": \"Země\", \"velikost\": 50},"
				+ "{\"id\": \"europe/jinde\", \"nazev\": \"Jinde\", \"velikost\": 7, \"obdelnik\": [41, 11, 41.5, 11.5]}", id -> {
					final String text = poly.get(id);
					return text == null ? null : PolygonPokryti.nacti(new StringReader(text));
				});
		assertEquals(Arrays.asList("europe/zeme/region", "europe/zeme", "europe/jinde"), ids(k.getMapy()));
		assertEquals(Arrays.asList("europe/jinde", "europe/zeme/region", "europe/zeme"), ids(k.pokryvajiBod(new Wgs(41.2, 11.2))));
		assertEquals(Collections.singletonList("europe/zeme"), ids(k.pokryvajiBod(new Wgs(45, 15))));
		assertTrue("obdélník z katalogu u regionu neplatí, má obrys", k.pokryvajiBod(new Wgs(0.5, 0.5)).isEmpty());
		assertEquals("https://example.org/maps/v5/europe/zeme/region.map", k.podleId("europe/zeme/region").get().getUrlMapy().toString());
		assertEquals(5, k.podleId("europe/zeme/region").get().getPribliznaVelikost());
	}

	@Test
	public void celekRegionyADatum() throws IOException {
		final KatalogMap k = katalog("{\"id\": \"europe/zeme\", \"nazev\": \"Země\", \"velikost\": 50, \"datum\": \"Fri, 28 Aug 2026 07:34:31 GMT\", \"obdelnik\": [40, 10, 50, 20]},"
				+ "{\"id\": \"europe/zeme/a\", \"nazev\": \"A\", \"skupina\": \"Země\", \"velikost\": 5, \"obdelnik\": [40, 10, 45, 15]},"
				+ "{\"id\": \"europe/zeme/b\", \"nazev\": \"B\", \"skupina\": \"Země\", \"velikost\": 5, \"obdelnik\": [45, 15, 50, 20]},"
				+ "{\"id\": \"europe/sama\", \"nazev\": \"Sama\", \"velikost\": 5, \"obdelnik\": [0, 0, 1, 1]},"
				+ "{\"id\": \"europe/bezcelku/c\", \"nazev\": \"C\", \"velikost\": 5, \"obdelnik\": [0, 0, 1, 1]}", id -> null);
		final KatalogovaMapa zeme = k.podleId("europe/zeme").get();
		assertEquals(java.time.Instant.parse("2026-08-28T07:34:31Z"), zeme.getDatumNaServeru());
		assertNull(k.podleId("europe/zeme/a").get().getDatumNaServeru());
		assertNull(zeme.getIdCelku());
		assertEquals("europe/zeme", k.podleId("europe/zeme/a").get().getIdCelku());
		assertEquals(Arrays.asList("europe/zeme/a", "europe/zeme/b"), ids(k.regiony(zeme)));
		assertSame(zeme, k.celek(k.podleId("europe/zeme/b").get()).get());
		assertFalse(k.celek(zeme).isPresent());
		assertTrue(k.regiony(k.podleId("europe/sama").get()).isEmpty());
		assertFalse("region bez celé země v katalogu", k.celek(k.podleId("europe/bezcelku/c").get()).isPresent());
		chyba("{\"id\": \"europe/a\", \"nazev\": \"A\", \"velikost\": 5, \"datum\": \"včera\", \"obdelnik\": [0, 0, 1, 1]}", "neplatné datum");
	}

	@Test
	public void chybnyKatalog() {
		chyba("{\"id\": \"europe/a\", \"nazev\": \"A\", \"velikost\": 5}", "nemá obrys ani obdélník");
		chyba("{\"id\": \"europe/a\", \"nazev\": \"A\", \"velikost\": 5, \"obdelnik\": [1, 1, 0, 2]}", "neplatný obdélník");
		chyba("{\"id\": \"europe/a\", \"nazev\": \"A\", \"obdelnik\": [0, 0, 1, 1]}", "nemá velikost");
		chyba("{\"id\": \"../a\", \"nazev\": \"A\", \"velikost\": 5, \"obdelnik\": [0, 0, 1, 1]}", "Neplatné id");
		chyba("{\"id\": \"europe/a\", \"velikost\": 5, \"obdelnik\": [0, 0, 1, 1]}", "chybí nazev");
		chyba("{\"id\": \"europe/a\", \"nazev\": \"A\", \"velikost\": 5, \"obdelnik\": [0, 0, 1, 1]}, {\"id\": \"asia/a\", \"nazev\": \"B\", \"velikost\": 5, \"obdelnik\": [0, 0, 1, 1]}",
				"stejné jméno souboru");
		chyba("{\"id\": \"europe/a\", \"nazev\": \"A\", \"velikost\": 5, \"obdelnik\": [0, 0, 1, 1]", "JSON");
	}

	@Test
	public void jsonEscapy() throws IOException {
		final KatalogMap k = katalog("{\"id\": \"europe/a\", \"nazev\": \"A \\\"b\\\" \\u010c\", \"velikost\": 5e3, \"obdelnik\": [-1.5, 0, 1, 1], \"navic\": [true, false, null, {}]}",
				id -> null);
		assertEquals("A \"b\" Č", k.getMapy().get(0).getNazev());
		assertEquals(5000, k.getMapy().get(0).getPribliznaVelikost());
	}

	@Test
	public void jsonEscapyVsechny() throws IOException {
		assertEquals("a\nb\rc\td\be\ff/g\\h\"", JsonCteni.precti("\"a\\nb\\rc\\td\\be\\ff\\/g\\\\h\\\"\""));
		for (final String spatny : new String[] { "\"\\x\"", "\"\\u+041\"", "\"\\u12\"", "\"\\u12G4\"" }) {
			try {
				JsonCteni.precti(spatny);
				fail(spatny);
			} catch (final IOException e) {
				assertTrue(e.getMessage(), e.getMessage().contains("JSON"));
			}
		}
	}

	@Test
	public void jsonHloubka() throws IOException {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 64; i++) {
			sb.append('[');
		}
		for (int i = 0; i < 64; i++) {
			sb.append(']');
		}
		assertNotNull(JsonCteni.precti(sb.toString()));
		final StringBuilder hluboky = new StringBuilder();
		for (int i = 0; i < 200_000; i++) {
			hluboky.append('[');
		}
		try {
			JsonCteni.precti(hluboky.toString());
			fail();
		} catch (final IOException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("hluboké"));
		}
	}

	private static void chyba(final String polozky, final String cast) {
		try {
			katalog(polozky, id -> null);
			fail("Čekám chybu: " + cast);
		} catch (final IOException e) {
			assertTrue(e.getMessage(), e.getMessage().contains(cast));
		}
	}

	private static List<String> ids(final List<KatalogovaMapa> mapy) {
		return mapy.stream().map(KatalogovaMapa::getId).collect(Collectors.toList());
	}
}
