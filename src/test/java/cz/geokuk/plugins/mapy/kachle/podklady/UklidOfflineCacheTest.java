package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
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
		Assert.assertEquals(Collections.singleton("o00000001"), UklidOfflineCache.zaznamenej(slozka, "o00000001", a));
		final File b = symboly("0000000b");
		UklidOfflineCache.zaznamenej(slozka, "o00000002", b);
		Assert.assertEquals(Arrays.asList("o00000003", "o00000002", "o00000001"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000003", a)));
		Assert.assertTrue(a.isFile() && b.isFile());
		Assert.assertFalse("symboly bez klíče se smažou", stary.isFile());

		// Návrat k dřívějšímu klíči ho posune dopředu, nic se nemaže.
		Assert.assertEquals(Arrays.asList("o00000001", "o00000003", "o00000002"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000001", a)));

		final File c = symboly("0000000c");
		Assert.assertEquals(Arrays.asList("o00000004", "o00000001", "o00000003"), new ArrayList<>(UklidOfflineCache.zaznamenej(slozka, "o00000004", c)));
		Assert.assertTrue("symboly posledních klíčů zůstávají", a.isFile() && c.isFile());
		Assert.assertFalse("symboly vypadlého klíče se smažou", b.isFile());
		Assert.assertTrue("cizí soubory zůstávají", jiny.isFile());
	}
}
