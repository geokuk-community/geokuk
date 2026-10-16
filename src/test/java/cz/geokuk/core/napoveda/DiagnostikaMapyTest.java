package cz.geokuk.core.napoveda;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.mapy.kachle.podklady.TemaOfflineMapy;

/** Offline mapy v informacích pro hlášení chyby. */
public class DiagnostikaMapyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void popisMapTematuACache() throws Exception {
		final File slozka = tmp.newFolder("offline-mapy");
		try (InputStream is = getClass().getResourceAsStream("/offline-mapy/kukov.map")) {
			Files.copy(is, new File(slozka, "kukov.map").toPath());
		}
		Files.copy(new File("distribuce/offline-mapy/prehled-svet-ne.map").toPath(), new File(slozka, "prehled-svet-ne.map").toPath());
		Files.write(new File(slozka, "rozbita.map").toPath(), new byte[] { 1, 2, 3 });
		final File cache = tmp.newFolder("cache");
		Files.write(new File(cache, "tiles.sqlite").toPath(), new byte[2 * 1024 * 1024]);

		final String text = DiagnostikaMapy.popis(slozka, TemaOfflineMapy.VYCHOZI, null, "Ve složce " + slozka.getPath() + " nejsou offline mapy", cache);

		assertTrue(text, text.startsWith("Offline mapy:\n"));
		assertTrue(text, text.contains("  mapsforge: "));
		assertTrue(text, text.contains("  složka: " + Diagnostika.bezDomova(slozka) + "\n"));
		assertTrue(text, text.contains("  téma: Mapsforge (původní) (vestavěné)\n"));
		assertTrue(text, text.contains("  poslední chyba mapy: Ve složce " + Diagnostika.bezDomova(slozka) + " nejsou"));
		assertTrue(text, text.contains("  přehledová mapa: ano\n"));
		assertTrue(text, text.contains("  cache dlaždic: 2.0 MB, symboly témat 0.0 MB\n"));
		assertTrue(text, text.contains("  mapy (3):\n"));
		assertTrue(text, text.contains("    kukov.map: 0.0 MB, data 8. 10. 2026, oblast 49.99,14.39 – 50.01,14.42, zoom 0–21, formát v3, geokuk-test\n"));
		assertTrue(text, text.contains("    rozbita.map: 0.0 MB, nejde přečíst: "));
	}

	@Test
	public void oddilJeVTextuProHlaseni() {
		Diagnostika.setOfflineMapy(() -> "Offline mapy:\n  mapy (0):\n");
		try {
			final String text = Diagnostika.text(5, 5);
			assertTrue(text, text.contains("\nOffline mapy:\n  mapy (0):\n"));
			assertTrue("za údaji o programu", text.indexOf("Offline mapy:") > text.indexOf("Běží: "));
		} finally {
			Diagnostika.setOfflineMapy(null);
		}
	}

	@Test
	public void chybyBezDomovskeSlozky() throws Exception {
		final String domov = cz.geokuk.core.program.FConst.HOME_DIR.getAbsolutePath();
		final String text = DiagnostikaMapy.popis(tmp.newFolder("mapy"), TemaOfflineMapy.VYCHOZI, "file does not exist: " + domov + "/GeoKuk/tema.xml",
				"Ve složce " + domov + "/./GeoKuk/mapy nejsou offline mapy", null);
		assertTrue(text, text.contains("  chyba tématu: file does not exist: ~/GeoKuk/tema.xml\n"));
		assertTrue(text, text.contains("  poslední chyba mapy: Ve složce ~/./GeoKuk/mapy nejsou"));
		assertFalse(text, text.contains(domov));
	}

	@Test
	public void bezMapAPrehledu() throws Exception {
		final String text = DiagnostikaMapy.popis(tmp.newFolder("prazdna"), TemaOfflineMapy.VYCHOZI, "téma nejde načíst", null, null);
		assertTrue(text, text.contains("  chyba tématu: téma nejde načíst\n"));
		assertTrue(text, text.contains("  přehledová mapa: ne\n"));
		assertTrue(text, text.contains("  mapy (0):\n"));
		assertFalse(text, text.contains("cache dlaždic"));
	}
}
