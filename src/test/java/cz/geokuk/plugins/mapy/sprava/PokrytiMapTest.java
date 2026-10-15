package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Wgs;

public class PokrytiMapTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final Wgs KUKOV = new Wgs(50.003, 14.405);

	@Test
	public void souborUzivatelePodleHlavickyKatalogovaPodleObrysu() throws IOException {
		final File slozka = tmp.newFolder("mapy");
		final File uzivatel = HlavickaMapyTest.kukov(slozka);
		// Stejná mapa pod jménem katalogové, jejíž obrys Kukov nepokrývá.
		final File katalogova = new File(slozka, "region.map");
		Files.copy(uzivatel.toPath(), katalogova.toPath());
		final File poskozena = new File(slozka, "poskozena.map");
		Files.write(poskozena.toPath(), new byte[] { 1, 2, 3 });
		Files.write(new File(slozka, "kukov.map.stahuje").toPath(), Files.readAllBytes(uzivatel.toPath()));

		final KatalogMap k = KatalogMap.nacti("{\"zdroj\": \"s\", \"licence\": \"l\", \"atribuce\": \"a\", \"mapy\": \"https://x.org/m/\", \"polygony\": \"https://x.org/p/\","
				+ "\"polozky\": [{\"id\": \"europe/region\", \"nazev\": \"R\", \"velikost\": 1, \"obdelnik\": [0, 0, 1, 1]}]}", id -> null);
		assertEquals(Collections.singletonList(uzivatel), PokrytiMap.stazeneMapyProBod(slozka, KUKOV, k));
		assertTrue(PokrytiMap.stazeneMapyProBod(slozka, new Wgs(0.5, 0.5), k).contains(katalogova));
		assertTrue(PokrytiMap.stazeneMapyProBod(slozka, new Wgs(48, 17), k).isEmpty());
		assertTrue(PokrytiMap.stazeneMapyProBod(new File(slozka, "neni"), KUKOV, k).isEmpty());
	}

	@Test
	public void dvojiteMapy() throws IOException {
		final File slozka = tmp.newFolder("dvojite");
		final KatalogMap k = KatalogMap.nacti("{\"zdroj\": \"s\", \"licence\": \"l\", \"atribuce\": \"a\", \"mapy\": \"https://x.org/m/\", \"polygony\": \"https://x.org/p/\", \"polozky\": ["
				+ "{\"id\": \"europe/zeme\", \"nazev\": \"Z\", \"velikost\": 1, \"obdelnik\": [0, 0, 1, 1]},"
				+ "{\"id\": \"europe/zeme/a\", \"nazev\": \"A\", \"velikost\": 1, \"obdelnik\": [0, 0, 1, 1]},"
				+ "{\"id\": \"europe/zeme/b\", \"nazev\": \"B\", \"velikost\": 1, \"obdelnik\": [0, 0, 1, 1]},"
				+ "{\"id\": \"europe/jina\", \"nazev\": \"J\", \"velikost\": 1, \"obdelnik\": [0, 0, 1, 1]}]}", id -> null);
		Files.write(new File(slozka, "a.map").toPath(), new byte[1]);
		Files.write(new File(slozka, "jina.map").toPath(), new byte[1]);
		assertTrue("jen region, bez celé země", PokrytiMap.dvojiteMapy(slozka, k).isEmpty());
		Files.write(new File(slozka, "Zeme.MAP").toPath(), new byte[1]);
		Files.write(new File(slozka, "b.map.stahuje").toPath(), new byte[1]);
		final List<PokrytiMap.DvojitaMapa> d = PokrytiMap.dvojiteMapy(slozka, k);
		assertEquals(1, d.size());
		assertEquals("europe/zeme", d.get(0).getCelek().getId());
		assertEquals(1, d.get(0).getRegiony().size());
		assertEquals("europe/zeme/a", d.get(0).getRegiony().get(0).getId());
	}
}
