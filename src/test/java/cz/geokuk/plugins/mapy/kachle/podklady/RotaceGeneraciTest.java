package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class RotaceGeneraciTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Čas dostatečně pozdě, aby žádný soubor v testu nebyl „nedávno změněný“. */
	private static final long POZDE = System.currentTimeMillis() + 3_600_000;

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

	private Set<String> soubory() {
		return new HashSet<>(Arrays.asList(slozka.list()));
	}

	@Test
	public void bezSouboruNicNeudela() {
		final RotaceGeneraci.Stav stav = RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertEquals(1, stav.online);
		Assert.assertEquals(1, stav.offline);
		Assert.assertTrue("stav se bez rotace nezapisuje", soubory().isEmpty());
	}

	@Test
	public void souborPodLimitemASPresnymLimitemNerotuje() throws Exception {
		soubor("tiles-online-1.sqlite", 100);
		soubor("tiles-offline-1.sqlite", 50);
		final RotaceGeneraci.Stav stav = RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertEquals(1, stav.online);
		Assert.assertEquals(1, stav.offline);
		Assert.assertFalse(new File(slozka, RotaceGeneraci.STAV).exists());
	}

	@Test
	public void prekroceniLimituUdelaZAktualniPredchoziAnicNesmaze() throws Exception {
		soubor("tiles-online-1.sqlite", 101);
		soubor("tiles-offline-1.sqlite", 10);
		final RotaceGeneraci.Stav stav = RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertEquals(2, stav.online);
		Assert.assertEquals(1, stav.offline);
		Assert.assertTrue("předchozí generace zůstává", new File(slozka, "tiles-online-1.sqlite").isFile());
		Assert.assertTrue(new File(slozka, "tiles-offline-1.sqlite").isFile());
		Assert.assertEquals(Arrays.asList("online=2", "offline=1"), Files.readAllLines(new File(slozka, RotaceGeneraci.STAV).toPath(), StandardCharsets.UTF_8));
	}

	@Test
	public void dalsiRotaceSmazeNejstarsiGeneraci() throws Exception {
		soubor("tiles-online-1.sqlite", 7);
		soubor("tiles-online-2.sqlite", 101);
		Files.write(new File(slozka, RotaceGeneraci.STAV).toPath(), Arrays.asList("online=2", "offline=1"));
		final RotaceGeneraci.Stav stav = RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertEquals(3, stav.online);
		Assert.assertFalse(new File(slozka, "tiles-online-1.sqlite").exists());
		Assert.assertTrue(new File(slozka, "tiles-online-2.sqlite").isFile());
	}

	@Test
	public void rotaceKazdeRoleJeNezavisla() throws Exception {
		soubor("tiles-online-1.sqlite", 10);
		soubor("tiles-offline-1.sqlite", 200);
		final RotaceGeneraci.Stav stav = RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertEquals(1, stav.online);
		Assert.assertEquals(2, stav.offline);
	}

	@Test
	public void stavSeZapisePredMazanimASmazaniKtereSelzeSeZopakuje() throws Exception {
		soubor("tiles-online-1.sqlite", 7);
		soubor("tiles-online-2.sqlite", 101);
		Files.write(new File(slozka, RotaceGeneraci.STAV).toPath(), Arrays.asList("online=2", "offline=1"));
		final RotaceGeneraci.Stav stav = RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> {
			// Při pokusu o smazání už musí být nové číslo zapsané.
			Assert.assertEquals(Arrays.asList("online=3", "offline=1"), Files.readAllLines(new File(slozka, RotaceGeneraci.STAV).toPath(), StandardCharsets.UTF_8));
			throw new IOException("soubor je zamčený");
		});
		Assert.assertEquals(3, stav.online);
		Assert.assertTrue("nesmazaný zůstal", new File(slozka, "tiles-online-1.sqlite").isFile());
		Assert.assertTrue(new File(slozka, "tiles-online-2.sqlite").isFile());
		// Při dalším startu se smaže, aktuální číslo je pořád 3 a nic dalšího se neztratí.
		final RotaceGeneraci.Stav dalsi = RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertEquals(3, dalsi.online);
		Assert.assertFalse(new File(slozka, "tiles-online-1.sqlite").exists());
		Assert.assertTrue(new File(slozka, "tiles-online-2.sqlite").isFile());
	}

	@Test
	public void bezStavuSeBereNejvyssiCisloZeSouboru() throws Exception {
		soubor("tiles-online-4.sqlite", 10);
		soubor("tiles-online-3.sqlite", 10);
		soubor("tiles-online-1.sqlite", 10);
		final RotaceGeneraci.Stav stav = RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertEquals(4, stav.online);
		Assert.assertFalse("o dvě starší se smaže", new File(slozka, "tiles-online-1.sqlite").exists());
		Assert.assertTrue(new File(slozka, "tiles-online-3.sqlite").isFile());
		Assert.assertTrue(new File(slozka, "tiles-online-4.sqlite").isFile());
	}

	@Test
	public void poskozenyStavNicNesmaze() throws Exception {
		soubor("tiles-online-1.sqlite", 10);
		Files.write(new File(slozka, RotaceGeneraci.STAV).toPath(), "nesmysl\nonline=abc\n".getBytes(StandardCharsets.UTF_8));
		final RotaceGeneraci.Stav stav = RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertEquals(1, stav.online);
		Assert.assertTrue(new File(slozka, "tiles-online-1.sqlite").isFile());
	}

	@Test
	public void staryCommonSouborSeSmazeAzKdyzRotovalyObeRole() throws Exception {
		soubor("tiles.sqlite", 10);
		soubor("tiles.sqlite-journal", 1);
		soubor("tiles-online-1.sqlite", 101);
		soubor("tiles-offline-1.sqlite", 10);
		RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertTrue("rotovala jen online", new File(slozka, "tiles.sqlite").isFile());
		soubor("tiles-offline-1.sqlite", 101);
		RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertFalse("obě rotovaly", new File(slozka, "tiles.sqlite").exists());
		Assert.assertFalse(new File(slozka, "tiles.sqlite-journal").exists());
		Assert.assertTrue("předchozí generace zůstávají", new File(slozka, "tiles-online-1.sqlite").isFile());
		Assert.assertTrue(new File(slozka, "tiles-offline-1.sqlite").isFile());
	}

	@Test
	public void nejdeZapsatStavRotaceSeOdlozi() throws Exception {
		soubor("tiles-online-1.sqlite", 101);
		Assert.assertTrue(new File(slozka, RotaceGeneraci.STAV).mkdir());
		new File(slozka, RotaceGeneraci.STAV + "x").createNewFile();
		// Stav je složka, takže se nedá přepsat.
		final RotaceGeneraci.Stav stav = RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertEquals(1, stav.online);
		Assert.assertTrue(new File(slozka, "tiles-online-1.sqlite").isFile());
	}

	@Test
	public void vysokeCisloVeStavuNesmazeExistujiciGenerace() throws Exception {
		soubor("tiles-offline-1.sqlite", 10);
		soubor("tiles-offline-2.sqlite", 10);
		Files.write(new File(slozka, RotaceGeneraci.STAV).toPath(), Arrays.asList("online=1", "offline=50"));
		final RotaceGeneraci.Stav stav = RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertEquals(2, stav.offline);
		Assert.assertTrue(new File(slozka, "tiles-offline-1.sqlite").isFile());
		Assert.assertTrue(new File(slozka, "tiles-offline-2.sqlite").isFile());
	}

	@Test
	public void stavONaVetsiNezNejvyssiSouborSeBereJakoPoRotaci() throws Exception {
		soubor("tiles-offline-1.sqlite", 10);
		soubor("tiles-offline-2.sqlite", 10);
		Files.write(new File(slozka, RotaceGeneraci.STAV).toPath(), Arrays.asList("online=1", "offline=3"));
		final RotaceGeneraci.Stav stav = RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertEquals(3, stav.offline);
		Assert.assertFalse(new File(slozka, "tiles-offline-1.sqlite").exists());
		Assert.assertTrue(new File(slozka, "tiles-offline-2.sqlite").isFile());
	}

	@Test
	public void zurnalSeNesmazeKdyzHlavniSouborNejde() throws Exception {
		soubor("tiles-offline-1.sqlite", 10);
		soubor("tiles-offline-1.sqlite-journal", 5);
		soubor("tiles-offline-2.sqlite", 101);
		Files.write(new File(slozka, RotaceGeneraci.STAV).toPath(), Arrays.asList("online=1", "offline=2"));
		RotaceGeneraci.rotuj(slozka, 100, 100, POZDE, f -> {
			if (f.getName().equals("tiles-offline-1.sqlite")) {
				throw new IOException("zamčeno");
			}
			Files.deleteIfExists(f.toPath());
		});
		Assert.assertTrue(new File(slozka, "tiles-offline-1.sqlite").isFile());
		Assert.assertTrue("žurnál patří k nesmazanému souboru", new File(slozka, "tiles-offline-1.sqlite-journal").isFile());
	}

	@Test
	public void nedavnoZmenenyStarySouborSeNesmaze() throws Exception {
		final File stary = soubor("tiles-offline-1.sqlite", 10);
		soubor("tiles-offline-2.sqlite", 101);
		Files.write(new File(slozka, RotaceGeneraci.STAV).toPath(), Arrays.asList("online=1", "offline=2"));
		final long ted = System.currentTimeMillis();
		RotaceGeneraci.rotuj(slozka, 100, 100, ted, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertTrue("druhá instance do něj možná píše", stary.isFile());
		Assert.assertTrue(stary.setLastModified(ted - RotaceGeneraci.NEDAVNO_MS - 1000));
		RotaceGeneraci.rotuj(slozka, 100, 100, ted, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertFalse(stary.exists());
	}

	@Test
	public void onlineDlazdiceSeVeVychozimNastaveniNerotuji() throws Exception {
		soubor("tiles.sqlite", 10);
		soubor("tiles-online-1.sqlite", 600 << 10);
		soubor("tiles-offline-1.sqlite", 10);
		final RotaceGeneraci.Stav stav = RotaceGeneraci.rotuj(slozka, RotaceGeneraci.LIMIT_ONLINE, 100, POZDE, f -> Files.deleteIfExists(f.toPath()));
		Assert.assertEquals(1, stav.online);
		Assert.assertTrue(new File(slozka, "tiles.sqlite").isFile());
		Assert.assertTrue(new File(slozka, "tiles-online-1.sqlite").isFile());
	}
}
