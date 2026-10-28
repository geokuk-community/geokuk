package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


/** Soubor opensak-pouzite-sloupce.txt, podle kterého týdenní kontrola hlídá schéma OpenSAKu, odpovídá tomu, co načítání skutečně čte. */
public class OpensakPouziteSloupceTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/**
	 * Načítání čte právě sloupce ze seznamu: bez libovolného jiného sloupce proběhne stejně jako s ním (čtení z ORDER BY, JOIN nebo WHERE by tady spadlo), bez sloupce ze seznamu
	 * se načte jinak nebo skončí chybou.
	 */
	@Test
	public void bezSloupceMimoSeznamSeNacteStejneABezSloupceZeSeznamuJinak() throws Exception {
		final Set<String> pouzite = new HashSet<>(radky("/opensak-pouzite-sloupce.txt"));
		final File zaklad = new File(tmp.getRoot(), "zaklad.db");
		OpensakTestDb.zaloz(zaklad, OpensakTestDb.verze());
		OpensakDbLoaderTest.naplnKese(zaklad);
		final List<String> ocekavane = projekce(OpensakDbLoaderTest.nacti(zaklad));
		Assert.assertFalse(ocekavane.isEmpty());
		for (final Map.Entry<String, List<String>> tabulka : OpensakTestDb.schema().entrySet()) {
			for (final String sloupec : tabulka.getValue()) {
				final File db = new File(tmp.getRoot(), tabulka.getKey() + "-" + sloupec + ".db");
				OpensakTestDb.zaloz(db, OpensakTestDb.verze());
				OpensakDbLoaderTest.naplnKese(db);
				try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
					s.execute("ALTER TABLE " + tabulka.getKey() + " DROP COLUMN " + sloupec);
				}
				List<String> vysledek;
				try {
					vysledek = projekce(OpensakDbLoaderTest.nacti(db));
				} catch (final Exception e) {
					vysledek = null;
				}
				final String radek = tabulka.getKey() + " " + sloupec;
				if (pouzite.contains(radek)) {
					Assert.assertNotEquals("sloupec ze seznamu nemá vliv na načtení: " + radek, ocekavane, vysledek);
				} else {
					Assert.assertEquals("sloupec mimo seznam se čte: " + radek, ocekavane, vysledek);
				}
			}
		}
	}

	private static List<String> projekce(final Map<String, GpxWpt> nactene) {
		final List<String> vysledek = new ArrayList<>();
		for (final GpxWpt w : nactene.values()) {
			final Groundspeak g = w.groundspeak;
			vysledek.add(String.join("|", String.valueOf(w.name), String.valueOf(w.time), String.valueOf(w.sym), String.valueOf(w.desc), String.valueOf(w.wgs),
					g == null ? "-" : String.join(",", String.valueOf(g.name), String.valueOf(g.type), String.valueOf(g.container), String.valueOf(g.difficulty), String.valueOf(g.terrain),
							String.valueOf(g.placedBy), String.valueOf(g.owner), String.valueOf(g.ownerid), String.valueOf(g.country), String.valueOf(g.state), String.valueOf(g.archived),
							String.valueOf(g.availaible), String.valueOf(g.hintZDatabaze != null && g.hintZDatabaze.apply(w.name) != null)),
					String.join(",", String.valueOf(w.gpxg.found), String.valueOf(w.gpxg.favorites), String.valueOf(w.gpxg.elevation))));
		}
		Collections.sort(vysledek);
		return vysledek;
	}

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
