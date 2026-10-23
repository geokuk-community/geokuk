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
}
