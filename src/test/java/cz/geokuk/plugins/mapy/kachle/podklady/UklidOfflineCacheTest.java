package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** V cache offline mapy zůstávají dlaždice a symboly jen posledních tří klíčů. */
public class UklidOfflineCacheTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File slozka;

	@Before
	public void setUp() throws Exception {
		slozka = tmp.newFolder("offline-temata");
	}

	private File symboly(final String crc) throws Exception {
		final File f = new File(slozka, "symboly-" + crc + ".bin");
		f.createNewFile();
		return f;
	}

	@Test
	public void zustavajiTriPosledniKlice() throws Exception {
		final File a = symboly("0000000a");
		final File stary = symboly("0000000f");
		final File jiny = new File(slozka, "jiny.bin");
		jiny.createNewFile();
		Assert.assertEquals(Collections.singleton("o00000001"), UklidOfflineCache.zaznamenej(slozka, "o00000001", a, "00000000"));
		final File b = symboly("0000000b");
		UklidOfflineCache.zaznamenej(slozka, "o00000002", b, "00000000");
		Assert.assertEquals(Arrays.asList("o00000003", "o00000002", "o00000001"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000003", a, "00000000")));
		Assert.assertTrue(a.isFile() && b.isFile());
		Assert.assertFalse("symboly bez klíče se smažou", stary.isFile());

		// Návrat k dřívějšímu klíči ho posune dopředu, nic se nemaže.
		Assert.assertEquals(Arrays.asList("o00000001", "o00000003", "o00000002"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000001", a, "00000000")));

		final File c = symboly("0000000c");
		Assert.assertEquals(Arrays.asList("o00000004", "o00000001", "o00000003"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000004", c, "00000000")));
		Assert.assertTrue("symboly posledních klíčů zůstávají", a.isFile() && c.isFile());
		Assert.assertFalse("symboly vypadlého klíče se smažou", b.isFile());
		Assert.assertTrue("cizí soubory zůstávají", jiny.isFile());
	}

	/** Záznam ze starého tvaru souboru klíčů (bez otisku sestavy) se zahodí, dlaždice starého tvaru tak při prvním úklidu vypadnou. */
	@Test
	public void starySouborKlicuSeIgnoruje() throws Exception {
		final File a = symboly("0000000a");
		Files.write(new File(slozka, UklidOfflineCache.SOUBOR).toPath(), Arrays.asList("o00000009 symboly-0000000a.bin"), StandardCharsets.UTF_8);
		Assert.assertEquals(Collections.emptySet(), UklidOfflineCache.ponechane(slozka));
		Assert.assertEquals(Collections.singleton("o00000001"), UklidOfflineCache.zaznamenej(slozka, "o00000001", a, "0000abcd"));
		Assert.assertEquals("0000abcd", UklidOfflineCache.sestava(slozka, "o00000001"));
		Assert.assertNull(UklidOfflineCache.sestava(slozka, "o00000009"));
		Assert.assertEquals(Collections.singleton("o00000001"), UklidOfflineCache.ponechane(slozka));
		Assert.assertTrue(a.isFile());
	}

	/** Soubor symbolů, který nejde smazat, úklid nezastaví: klíče se zapíšou a vrátí. */
	@Test
	public void nesmazatelnySouborSymbolu() throws Exception {
		final File a = symboly("0000000a");
		final File zamceny = new File(slozka, "symboly-0000000f.bin");
		Assert.assertTrue(zamceny.mkdir());
		Files.createFile(new File(zamceny, "obsah").toPath());
		Assert.assertEquals(Collections.singleton("o00000001"), UklidOfflineCache.zaznamenej(slozka, "o00000001", a, "00000000"));
		Assert.assertEquals(Collections.singleton("o00000001"), UklidOfflineCache.ponechane(slozka));
	}

	private static final String VARIANTA_A = "aaaaaaaa";
	private static final String VARIANTA_B = "bbbbbbbb";
	private static final String VARIANTA_C = "cccccccc";

	private List<String> radky() throws Exception {
		return Files.readAllLines(new File(slozka, UklidOfflineCache.SOUBOR).toPath(), StandardCharsets.UTF_8);
	}

	/** Novější verze téže varianty nahradí starší hned, jiné varianty zůstávají. */
	@Test
	public void novejsiVerzeStejneVariantyNahradiStarsiJineVariantyZustavaji() throws Exception {
		final File a1 = symboly("0000000a");
		UklidOfflineCache.zaznamenej(slozka, "o00000001", a1, "00000000", VARIANTA_A);
		final File b1 = symboly("0000000b");
		UklidOfflineCache.zaznamenej(slozka, "o00000002", b1, "00000000", VARIANTA_B);
		final File a2 = symboly("0000000c");
		Assert.assertEquals(Arrays.asList("o00000003", "o00000002"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000003", a2, "00000000", VARIANTA_A)));
		Assert.assertFalse("symboly nahrazeného klíče se smažou", a1.isFile());
		Assert.assertTrue(b1.isFile() && a2.isFile());
	}

	/** Jiné vrstvy, velikost, písmo i předpona dávají jinou identitu, takže se navzájem nemažou; strop tří platí pro různé identity. */
	@Test
	public void ruzneIdentityZustavajiDoStropuTri() throws Exception {
		final File s = symboly("0000000a");
		UklidOfflineCache.zaznamenej(slozka, "o00000001", s, "00000000", VARIANTA_A);
		UklidOfflineCache.zaznamenej(slozka, "o00000002", s, "00000000", VARIANTA_B);
		Assert.assertEquals(Arrays.asList("t00000003", "o00000002", "o00000001"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "t00000003", s, "00000000", VARIANTA_C)));
		Assert.assertEquals("čtvrtá identita vytlačí nejstarší", Arrays.asList("o00000004", "t00000003", "o00000002"),
				new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000004", s, "00000000", "dddddddd")));
		Assert.assertEquals("nová verze první varianty nic jiného nesmaže", Arrays.asList("o00000005", "o00000004", "t00000003"),
				new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000005", s, "00000000", VARIANTA_A)));
	}

	/** Aktuální klíč zůstává při jakémkoli zápisu, i když se zapíše znovu nebo má stejnou identitu jako starší záznam. */
	@Test
	public void aktualniKlicSeNikdyNesmaze() throws Exception {
		final File s = symboly("0000000a");
		UklidOfflineCache.zaznamenej(slozka, "o00000001", s, "00000000", VARIANTA_A);
		Assert.assertEquals(Collections.singleton("o00000001"), UklidOfflineCache.zaznamenej(slozka, "o00000001", s, "00000000", VARIANTA_A));
		Assert.assertEquals(Collections.singleton("o00000002"), UklidOfflineCache.zaznamenej(slozka, "o00000002", s, "00000000", VARIANTA_A));
		Assert.assertEquals("klíč bez identity nic nemaže", Arrays.asList("o00000003", "o00000002"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000003", s, "00000000")));
	}

	/** Po aktualizaci programu zůstane ze záznamů bez identity jen nejnovější, ostatní i s jejich symboly vypadnou; další zápisy už nic dalšího nemažou. */
	@Test
	public void zaznamyBezIdentityZustaneJenNejnovejsi() throws Exception {
		final File l1 = symboly("00000011");
		final File l2 = symboly("00000012");
		final File l3 = symboly("00000013");
		final File nove = symboly("0000000a");
		Files.write(new File(slozka, UklidOfflineCache.SOUBOR).toPath(), Arrays.asList("o00000a01 symboly-00000011.bin 00000000", "o00000a02 symboly-00000012.bin 00000000", "o00000a03 symboly-00000013.bin 00000000"),
				StandardCharsets.UTF_8);
		Assert.assertEquals(new HashSet<>(Arrays.asList("o00000a01", "o00000a02", "o00000a03")), UklidOfflineCache.ponechane(slozka));
		Assert.assertEquals(Arrays.asList("o00000001", "o00000a01"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000001", nove, "00000000", VARIANTA_A)));
		Assert.assertTrue(l1.isFile() && nove.isFile());
		Assert.assertFalse("symboly zahozených záznamů bez identity se smažou", l2.isFile() || l3.isFile());
		Assert.assertEquals(Arrays.asList("o00000002", "o00000a01"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000002", nove, "00000000", VARIANTA_A)));
		Assert.assertEquals(Arrays.asList("o00000003", "o00000002", "o00000a01"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000003", nove, "00000000", VARIANTA_B)));
	}

	/** Zápis bez identity (neznámá varianta) staré záznamy nechá, jak byly. */
	@Test
	public void bezIdentityNicNemaze() throws Exception {
		final File s = symboly("0000000a");
		Files.write(new File(slozka, UklidOfflineCache.SOUBOR).toPath(), Arrays.asList("o00000a01 symboly-0000000a.bin 00000000", "o00000a02 symboly-0000000a.bin 00000000"), StandardCharsets.UTF_8);
		Assert.assertEquals(Arrays.asList("o00000001", "o00000a01", "o00000a02"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000001", s, "00000000")));
	}

	/**
	 * Tvar souboru: první tři sloupce jako dosud, čtvrtý je identita. Starší verze programu řádky se čtyřmi sloupci nezná a přeskočí je, po návratu na starší verzi se tedy cache
	 * jiných klíčů zamete (přijato).
	 */
	@Test
	public void tvarSouboruKlicuAPoskozenaIdentita() throws Exception {
		final File s = symboly("0000000a");
		UklidOfflineCache.zaznamenej(slozka, "o00000001", s, "0000abcd", VARIANTA_A);
		Assert.assertEquals(Collections.singletonList("o00000001 symboly-0000000a.bin 0000abcd aaaaaaaa"), radky());
		Assert.assertEquals("0000abcd", UklidOfflineCache.sestava(slozka, "o00000001"));
		Files.write(new File(slozka, UklidOfflineCache.SOUBOR).toPath(), Arrays.asList("o00000001 symboly-0000000a.bin 0000abcd aaaaaaaa", "o00000002 symboly-0000000a.bin 0000abcd nesmysl",
				"o00000003 symboly-0000000a.bin 0000abcd aaaaaaaa bbbbbbbb"), StandardCharsets.UTF_8);
		Assert.assertEquals("řádky s nesmyslnou identitou nebo čtvrtým dalším sloupcem se přeskočí", Collections.singleton("o00000001"), UklidOfflineCache.ponechane(slozka));
	}

	/** Nejčastější cesta migrace: aktuální klíč už v souboru je jako záznam bez identity; dostane identitu a z ostatních zůstane nejnovější. */
	@Test
	public void aktualniKlicBezIdentityDostaneIdentituZOstatnichZustaneNejnovejsi() throws Exception {
		final File a = symboly("0000000a");
		final File b = symboly("0000000b");
		final File c = symboly("0000000c");
		Files.write(new File(slozka, UklidOfflineCache.SOUBOR).toPath(),
				Arrays.asList("o00000001 symboly-0000000a.bin 0000abcd", "o00000002 symboly-0000000b.bin 0000abcd", "o00000003 symboly-0000000c.bin 0000abcd"), StandardCharsets.UTF_8);
		Assert.assertEquals(Arrays.asList("o00000001", "o00000002"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000001", a, "0000abcd", VARIANTA_A)));
		Assert.assertEquals(Arrays.asList("o00000001 symboly-0000000a.bin 0000abcd aaaaaaaa", "o00000002 symboly-0000000b.bin 0000abcd"), radky());
		Assert.assertTrue(a.isFile() && b.isFile());
		Assert.assertFalse(c.isFile());
	}

	/** Podklad a překryv se uklízejí zvlášť: nejnovější záznam bez identity zůstane pro každý z nich, i s jeho soubory symbolů. */
	@Test
	public void migraceZachovaNejnovejsiBezIdentityProPodkladIPrekryv() throws Exception {
		final File o1 = symboly("00000011");
		final File o2 = symboly("00000012");
		final File t3 = symboly("00000013");
		final File nove = symboly("0000000a");
		Files.write(new File(slozka, UklidOfflineCache.SOUBOR).toPath(),
				Arrays.asList("o00000a01 symboly-00000011.bin 00000000", "o00000a02 symboly-00000012.bin 00000000", "t00000a03 symboly-00000013.bin 00000000"), StandardCharsets.UTF_8);
		Assert.assertEquals(Arrays.asList("o00000001", "o00000a01", "t00000a03"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000001", nove, "00000000", VARIANTA_A)));
		Assert.assertTrue("symboly aktuálního překryvu zůstávají", t3.isFile() && o1.isFile());
		Assert.assertFalse("druhý starší podklad vypadl", o2.isFile());
		// Zápis překryvu s identitou: jeho bezidentitní záznam se nahradí, podkladový zůstane.
		Assert.assertEquals(Arrays.asList("t00000002", "o00000001", "o00000a01"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "t00000002", nove, "00000000", VARIANTA_B)));
	}

	/** Zastaralé jsou klíče, které soubor dosud vedl a po zápisu v něm nebudou: nahrazená verze, vypadlé bezidentitní záznamy i vytlačené stropem tří. */
	@Test
	public void planUrciZastaraleKlice() throws Exception {
		final File s = symboly("0000000a");
		UklidOfflineCache.zaznamenej(slozka, "o00000001", s, "00000000", VARIANTA_A);
		UklidOfflineCache.zaznamenej(slozka, "o00000002", s, "00000000", VARIANTA_B);
		UklidOfflineCache.zaznamenej(slozka, "o00000003", s, "00000000", VARIANTA_C);
		Assert.assertEquals(Collections.singleton("o00000001"), UklidOfflineCache.naplanuj(slozka, "o00000004", s, "00000000", VARIANTA_A).zastarale());
		Assert.assertEquals("nic se neuložilo", Arrays.asList("o00000003", "o00000002", "o00000001"), new ArrayList<>(UklidOfflineCache.ponechane(slozka)));
		Assert.assertEquals("čtvrtá identita vytlačí nejstarší", Collections.singleton("o00000001"), UklidOfflineCache.naplanuj(slozka, "o00000004", s, "00000000", "dddddddd").zastarale());
		Assert.assertEquals("znovu zapsaný klíč nic nezastaralí", Collections.emptySet(), UklidOfflineCache.naplanuj(slozka, "o00000003", s, "00000000", VARIANTA_C).zastarale());
	}

	@Test
	public void zastaralaJeJenDlazdiceOfflineKlicuKtereNezustaly() {
		final java.util.Set<String> ponechat = new java.util.HashSet<>(Arrays.asList("o0000000a", "t0000000c"));
		Assert.assertFalse(UklidOfflineCache.jeZastarala("o0000000a", ponechat));
		Assert.assertFalse(UklidOfflineCache.jeZastarala("o0000000a.0000ffff", ponechat));
		Assert.assertTrue(UklidOfflineCache.jeZastarala("o0000000b", ponechat));
		Assert.assertTrue(UklidOfflineCache.jeZastarala("o0000000b.0000ffff", ponechat));
		Assert.assertTrue(UklidOfflineCache.jeZastarala("t0000000d.00000001", ponechat));
		Assert.assertFalse("online typ", UklidOfflineCache.jeZastarala("TURIST_M", ponechat));
		Assert.assertFalse(UklidOfflineCache.jeZastarala("", ponechat));
		Assert.assertFalse(UklidOfflineCache.jeZastarala(null, ponechat));
		Assert.assertFalse("neznámý tvar", UklidOfflineCache.jeZastarala("oXYZ", ponechat));
	}

	@Test
	public void bezPonechanychKlicuPrefixuNicNeniZastarale() {
		Assert.assertFalse(UklidOfflineCache.jeZastarala("o0000000b", java.util.Collections.emptySet()));
		Assert.assertFalse("překryv bez svého klíče", UklidOfflineCache.jeZastarala("t0000000d", java.util.Collections.singleton("o0000000a")));
		Assert.assertTrue(UklidOfflineCache.jeZastarala("o0000000b", java.util.Collections.singleton("o0000000a")));
	}

	/** Soubor klíčů s více záznamy, než se ponechává, se čte jen do počtu ponechaných. */
	@Test
	public void ponechaneJeNejvicTriKlice() throws Exception {
		Files.write(new File(slozka, UklidOfflineCache.SOUBOR).toPath(), Arrays.asList("o00000004 symboly-0000000a.bin 00000000 aaaaaaaa", "o00000003 symboly-0000000a.bin 00000000 bbbbbbbb",
				"o00000002 symboly-0000000a.bin 00000000 cccccccc", "o00000001 symboly-0000000a.bin 00000000 dddddddd"), StandardCharsets.UTF_8);
		Assert.assertEquals(Arrays.asList("o00000004", "o00000003", "o00000002"), new ArrayList<>(UklidOfflineCache.ponechane(slozka)));
	}
}
