package cz.geokuk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

/** Texty pro uživatele bez překlepů, žertů a rad, které v programu neplatí. */
public class TextyGuiTest {

	private static final String[] ZAKAZANE = { "Grrrr", "číslem býti", "čudl bude", "Tady bude kecání", "Nastav na meritko", "Java Heap", "-Xmx1024m",
			"Složku můžete změnit v menu Soubor > Umístění souborů", "Tabulka problemu vymazana", "kotnrola", "aktualiozací", "Různo dekorace", "Matin Veverka",
			"domací", "\"Filte ", "bšechny", "nalezenýc ", "kromě kromě", "smailíka", "všechyn", "zorbazovat", "infroamcemi", "ze sadama", "sady ikok", "Spouřadnice",
			"apliakci", "vertkálním", "velikosti výrazňovacího", "zvýrazňovaích", "tak ,aby", "zdrojích ,z", "do GGT soubor,", "kalibrovaných mapy", "GoogleEarthj",
			"Datová složka geogetu", "ukazovala českou", "ukončit process", "Nejsou pocitadla", "Zapnuti vypnuti", "Výběr Filtru", "z diskové keše",
			"Výjimka vypadla" };

	@Test
	public void textyBezChyb() throws IOException {
		final List<String> nalezeno = new ArrayList<>();
		try (Stream<Path> soubory = Files.walk(Paths.get("src/main/java"))) {
			soubory.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
				try {
					final List<String> radky = Files.readAllLines(p, StandardCharsets.UTF_8);
					for (int i = 0; i < radky.size(); i++) {
						final String radek = radky.get(i);
						if (radek.trim().startsWith("//")) {
							continue;
						}
						for (final String z : ZAKAZANE) {
							if (radek.contains(z)) {
								nalezeno.add(p + ":" + (i + 1) + " " + z);
							}
						}
					}
				} catch (final IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
		Assert.assertEquals(Collections.emptyList(), nalezeno);
	}
}
