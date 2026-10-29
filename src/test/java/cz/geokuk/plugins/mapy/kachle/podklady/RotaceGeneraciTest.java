package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class RotaceGeneraciTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Čas dostatečně pozdě, aby žádný soubor v testu nebyl „nedávno změněný“. */
	private static final long POZDE = System.currentTimeMillis() + 3_600_000;

	private static final RotaceGeneraci.Mazani MAZANI = f -> Files.deleteIfExists(f.toPath());

	private static final java.util.concurrent.Executor PRIMO = Runnable::run;

	private File slozka;

	@Before
	public void setUp() throws Exception {
		slozka = tmp.newFolder("cache");
	}

	private File soubor(final String jmeno, final int bajtu) throws IOException {
		final File f = new File(slozka, jmeno);
		Files.write(f.toPath(), new byte[bajtu]);
		return f;
	}

	private void stav(final String... radky) throws IOException {
		Files.write(new File(slozka, RotaceGeneraci.STAV).toPath(), Arrays.asList(radky));
	}

	private boolean je(final String jmeno) {
		return new File(slozka, jmeno).exists();
	}

	@Test
	public void bezSouboruNicNeudela() {
		Assert.assertEquals(1, RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI));
		Assert.assertEquals("stav se bez rotace nezapisuje", 0, slozka.list().length);
	}

	@Test
	public void souborPodLimitemASPresnymLimitemNerotuje() throws Exception {
		soubor("tiles-offline-1.sqlite", 100);
		Assert.assertEquals(1, RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI));
		Assert.assertFalse(je(RotaceGeneraci.STAV));
	}

	@Test
	public void prekroceniLimituUdelaZAktualniPredchoziAnicNesmaze() throws Exception {
		soubor("tiles-offline-1.sqlite", 101);
		Assert.assertEquals(2, RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI));
		Assert.assertTrue("předchozí generace zůstává", je("tiles-offline-1.sqlite"));
		Assert.assertEquals(Collections.singletonList("offline=2"), Files.readAllLines(new File(slozka, RotaceGeneraci.STAV).toPath(), StandardCharsets.UTF_8));
	}

	@Test
	public void dalsiRotaceSmazeNejstarsiGeneraci() throws Exception {
		soubor("tiles-offline-1.sqlite", 7);
		soubor("tiles-offline-2.sqlite", 101);
		stav("offline=2");
		Assert.assertEquals(3, RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI));
		Assert.assertFalse(je("tiles-offline-1.sqlite"));
		Assert.assertTrue(je("tiles-offline-2.sqlite"));
	}

	@Test
	public void stavSeZapisePredMazanimASmazaniKtereSelzeSeZopakuje() throws Exception {
		soubor("tiles-offline-1.sqlite", 7);
		soubor("tiles-offline-2.sqlite", 101);
		stav("offline=2");
		Assert.assertEquals(3, RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, f -> {
			// Při pokusu o smazání už musí být nové číslo zapsané.
			Assert.assertEquals(Collections.singletonList("offline=3"), Files.readAllLines(new File(slozka, RotaceGeneraci.STAV).toPath(), StandardCharsets.UTF_8));
			throw new IOException("soubor je zamčený");
		}));
		Assert.assertTrue("nesmazaný zůstal", je("tiles-offline-1.sqlite"));
		Assert.assertTrue(je("tiles-offline-2.sqlite"));
		Assert.assertEquals(3, RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI));
		Assert.assertFalse(je("tiles-offline-1.sqlite"));
		Assert.assertTrue(je("tiles-offline-2.sqlite"));
	}

	@Test
	public void bezStavuSeBereNejvyssiCisloZeSouboru() throws Exception {
		soubor("tiles-offline-4.sqlite", 10);
		soubor("tiles-offline-3.sqlite", 10);
		soubor("tiles-offline-1.sqlite", 10);
		Assert.assertEquals(4, RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI));
		Assert.assertFalse("o dvě starší se smaže", je("tiles-offline-1.sqlite"));
		Assert.assertTrue(je("tiles-offline-3.sqlite"));
		Assert.assertTrue(je("tiles-offline-4.sqlite"));
	}

	@Test
	public void poskozenyStavNicNesmaze() throws Exception {
		soubor("tiles-offline-1.sqlite", 10);
		Files.write(new File(slozka, RotaceGeneraci.STAV).toPath(), "nesmysl\noffline=abc\n".getBytes(StandardCharsets.UTF_8));
		Assert.assertEquals(1, RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI));
		Assert.assertTrue(je("tiles-offline-1.sqlite"));
	}

	@Test
	public void nejdeZapsatStavRotaceSeOdlozi() throws Exception {
		soubor("tiles-offline-1.sqlite", 101);
		Assert.assertTrue(new File(slozka, RotaceGeneraci.STAV).mkdir());
		Assert.assertEquals(1, RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI));
		Assert.assertTrue(je("tiles-offline-1.sqlite"));
	}

	@Test
	public void vysokeCisloVeStavuNesmazeExistujiciGenerace() throws Exception {
		soubor("tiles-offline-1.sqlite", 10);
		soubor("tiles-offline-2.sqlite", 10);
		stav("offline=50");
		Assert.assertEquals(2, RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI));
		Assert.assertTrue(je("tiles-offline-1.sqlite"));
		Assert.assertTrue(je("tiles-offline-2.sqlite"));
	}

	@Test
	public void stavONaVetsiNezNejvyssiSouborSeBereJakoPoRotaci() throws Exception {
		soubor("tiles-offline-1.sqlite", 10);
		soubor("tiles-offline-2.sqlite", 10);
		stav("offline=3");
		Assert.assertEquals(3, RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI));
		Assert.assertFalse(je("tiles-offline-1.sqlite"));
		Assert.assertTrue(je("tiles-offline-2.sqlite"));
	}

	@Test
	public void zurnalSeNesmazeKdyzHlavniSouborNejde() throws Exception {
		soubor("tiles-offline-1.sqlite", 10);
		soubor("tiles-offline-1.sqlite-journal", 5);
		soubor("tiles-offline-2.sqlite", 101);
		stav("offline=2");
		RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, f -> {
			if (f.getName().equals("tiles-offline-1.sqlite")) {
				throw new IOException("zamčeno");
			}
			Files.deleteIfExists(f.toPath());
		});
		Assert.assertTrue(je("tiles-offline-1.sqlite"));
		Assert.assertTrue("žurnál patří k nesmazanému souboru", je("tiles-offline-1.sqlite-journal"));
	}

	@Test
	public void zurnalSeSmazeSHlavnimSouborem() throws Exception {
		soubor("tiles-offline-1.sqlite", 10);
		soubor("tiles-offline-1.sqlite-journal", 5);
		soubor("tiles-offline-2.sqlite", 101);
		stav("offline=2");
		RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI);
		Assert.assertFalse(je("tiles-offline-1.sqlite"));
		Assert.assertFalse(je("tiles-offline-1.sqlite-journal"));
	}

	@Test
	public void nedavnoZmenenyStarySouborSeNesmaze() throws Exception {
		final File stary = soubor("tiles-offline-1.sqlite", 10);
		soubor("tiles-offline-2.sqlite", 101);
		stav("offline=2");
		final long ted = System.currentTimeMillis();
		RotaceGeneraci.rotuj(slozka, 100, ted, PRIMO, MAZANI);
		Assert.assertTrue("druhá instance do něj možná píše", stary.isFile());
		Assert.assertTrue(stary.setLastModified(ted - RotaceGeneraci.NEDAVNO_MS - 1000));
		RotaceGeneraci.rotuj(slozka, 100, ted, PRIMO, MAZANI);
		Assert.assertFalse(stary.exists());
	}

	@Test
	public void onlineASpolecnySouborSeNikdyNemazou() throws Exception {
		soubor("tiles.sqlite", 600);
		soubor("tiles.sqlite-journal", 5);
		soubor("tiles-online-1.sqlite", 600);
		soubor("tiles-offline-1.sqlite", 7);
		soubor("tiles-offline-2.sqlite", 101);
		stav("offline=2");
		Assert.assertEquals(3, RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI));
		final Set<String> zbylo = new HashSet<>(Arrays.asList(slozka.list()));
		Assert.assertTrue(zbylo.toString(), zbylo.containsAll(Arrays.asList("tiles.sqlite", "tiles.sqlite-journal", "tiles-online-1.sqlite", "tiles-offline-2.sqlite")));
		Assert.assertFalse(zbylo.contains("tiles-offline-1.sqlite"));
	}

	@Test
	public void odlozeneVadneCacheStarychGeneraciSeMazouTaky() throws Exception {
		soubor("tiles-offline-1.sqlite", 10);
		soubor("tiles-offline-1.sqlite.vadna", 10);
		soubor("tiles-offline-1.sqlite.vadna.1", 10);
		soubor("tiles-offline-1.sqlite.vadna-journal", 2);
		soubor("tiles-offline-2.sqlite.vadna", 10);
		soubor("tiles-offline-2.sqlite", 101);
		stav("offline=2");
		RotaceGeneraci.rotuj(slozka, 100, POZDE, PRIMO, MAZANI);
		Assert.assertFalse(je("tiles-offline-1.sqlite"));
		Assert.assertFalse(je("tiles-offline-1.sqlite.vadna"));
		Assert.assertFalse(je("tiles-offline-1.sqlite.vadna.1"));
		Assert.assertFalse(je("tiles-offline-1.sqlite.vadna-journal"));
		Assert.assertTrue("vadná cache předchozí generace zůstává", je("tiles-offline-2.sqlite.vadna"));
	}

	@Test
	public void mazaniBeziVeVykonavateliAStavSeZapiseHned() throws Exception {
		soubor("tiles-offline-1.sqlite", 7);
		soubor("tiles-offline-2.sqlite", 101);
		stav("offline=2");
		final java.util.List<Runnable> ulohy = new java.util.ArrayList<>();
		Assert.assertEquals(3, RotaceGeneraci.rotuj(slozka, 100, POZDE, ulohy::add, MAZANI));
		Assert.assertEquals(Collections.singletonList("offline=3"), Files.readAllLines(new File(slozka, RotaceGeneraci.STAV).toPath(), StandardCharsets.UTF_8));
		Assert.assertTrue("zatím se nesmazalo", je("tiles-offline-1.sqlite"));
		ulohy.forEach(Runnable::run);
		Assert.assertFalse(je("tiles-offline-1.sqlite"));
	}
}
