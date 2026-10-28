package cz.geokuk.plugins.kesoid.importek;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.junit.Assert;
import org.junit.Test;

/** Soubor opensak-pouzite-sloupce.txt, podle kterého týdenní kontrola hlídá schéma OpenSAKu, odpovídá tomu, co načítání skutečně čte. */
public class OpensakPouziteSloupceTest {

	@Test
	public void souborOdpovidaSloupcumNacitani() throws IOException {
		final List<String> ocekavane = new ArrayList<>();
		OpensakDbLoader.pouziteSloupce().forEach((tabulka, sloupce) -> sloupce.forEach(sloupec -> ocekavane.add(tabulka + " " + sloupec)));
		Assert.assertEquals(ocekavane, radky("/opensak-pouzite-sloupce.txt"));
	}

	@Test
	public void pouziteSloupceJsouVeSchematuTestovaciDatabaze() throws IOException {
		final Set<String> schema = new HashSet<>(radky("/opensak-schema.txt").subList(1, radky("/opensak-schema.txt").size()));
		for (final String radek : radky("/opensak-pouzite-sloupce.txt")) {
			Assert.assertTrue(radek, schema.contains(radek));
		}
	}

	private static List<String> radky(final String zdroj) throws IOException {
		try (InputStream in = OpensakPouziteSloupceTest.class.getResourceAsStream(zdroj)) {
			final List<String> vysledek = new ArrayList<>();
			for (final String radek : new String(com.google.common.io.ByteStreams.toByteArray(in), StandardCharsets.UTF_8).split("\n")) {
				if (!radek.trim().isEmpty()) {
					vysledek.add(radek.trim());
				}
			}
			return vysledek;
		}
	}
}
