package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

import javax.imageio.ImageIO;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.*;
import cz.geokuk.util.file.Filex;

/** Cache se prochází, jen když se změní klíče, které v ní zůstávají; opakované otevření stejných map ji nezdržuje. */
public class UklidOfflineCachePriZmeneTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final List<Set<String>> pruchody = new ArrayList<>();

	/** Úklid po otevření map jen zapíše soubor klíčů; z cache nic nemaže ani neprochází, aby velká cache nezastavila čtení dlaždic. */
	@Test
	public void uklidNeprochaziAniNemazeCache() throws Exception {
		final File slozka = tmp.newFolder("offline-temata");
		final File a = new File(slozka, "symboly-0000000a.bin");
		final File b = new File(slozka, "symboly-0000000b.bin");
		final KachleZiskavac ziskavac = new KachleZiskavac();
		ziskavac.setKachleManager(new KachleManager() {
			@Override
			public boolean exists(final Ka ki) {
				return false;
			}

			@Override
			public Image load(final Ka ki) {
				return null;
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				return true;
			}

			@Override
			public int smazOfflineKrome(final Set<String> ponechat) {
				Assert.fail("úklid nesmí mazat dlaždice z cache");
				return 0;
			}

			@Override
			public int smazOfflineNesedici(final String globalni, final java.util.function.Function<KaLoc, String> ocekavany) {
				Assert.fail("úklid nesmí procházet cache");
				return 0;
			}
		});
		final org.mapsforge.core.model.BoundingBox kdekoli = new org.mapsforge.core.model.BoundingBox(10, 10, 11, 11);
		final OblastniKlic jedna = new OblastniKlic("o00000001", Collections.singletonList(new OblastniKlic.Zdroj(kdekoli, "mapa-1")), null, "aaaaaaaa");
		final OblastniKlic jinaSestava = new OblastniKlic("o00000001", Collections.singletonList(new OblastniKlic.Zdroj(kdekoli, "mapa-2")), null, "aaaaaaaa");
		ziskavac.uklidOfflineCache(jedna, a);
		ziskavac.uklidOfflineCache(jinaSestava, a);
		Assert.assertEquals(Collections.singleton("o00000001"), UklidOfflineCache.ponechane(slozka));
		ziskavac.uklidOfflineCache(new OblastniKlic("o00000002", Collections.emptyList(), null, "aaaaaaaa"), b);
		Assert.assertEquals("novější verze téže varianty nahradí starší v souboru klíčů", Collections.singleton("o00000002"), UklidOfflineCache.ponechane(slozka));
		ziskavac.uklidOfflineCache(new OblastniKlic("o00000003", Collections.emptyList(), null, "bbbbbbbb"), b);
		Assert.assertEquals(new HashSet<>(Arrays.asList("o00000003", "o00000002")), UklidOfflineCache.ponechane(slozka));
	}

	private static byte[] png() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}

	private static Ka misto(final int i) {
		return new Ka(KaLoc.ofJZ(new Mou(0x40000000 + (i << 19), 0x20000000), 13), EKaType.OFFLINE_MF);
	}

	/** Nad skutečnou cache úklid nic nesmaže: dlaždice všech klíčů zůstávají, i když soubor klíčů některé z nich už nezná. */
	@Test
	public void dlazdiceStarychKlicuZustavajiVCache() throws Exception {
		final File slozka = tmp.newFolder("offline-temata");
		final File cache = tmp.newFolder("cache");
		final KachleCacheFolderHolder holder = new KachleCacheFolderHolder();
		holder.setKachleCacheDir(new Filex(cache, false, true));
		final KachleDBManager manager = new KachleDBManager(holder);
		final KachleZiskavac ziskavac = new KachleZiskavac();
		ziskavac.setKachleManager(manager);
		final List<KachleManager.ItemToSave> davka = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			davka.add(new KachleManager.ItemToSave(misto(i), "o00000001", png()));
			davka.add(new KachleManager.ItemToSave(misto(i), "o00000002", png()));
		}
		Assert.assertTrue(manager.save(davka));
		ziskavac.uklidOfflineCache(new OblastniKlic("o00000001", Collections.emptyList(), null, "0123abcd"), new File(slozka, "symboly-0000000a.bin"));
		ziskavac.uklidOfflineCache(new OblastniKlic("o00000002", Collections.emptyList(), null, "0123abcd"), new File(slozka, "symboly-0000000b.bin"));
		Assert.assertEquals(Collections.singleton("o00000002"), UklidOfflineCache.ponechane(slozka));
		for (int i = 0; i < 100; i++) {
			Assert.assertTrue("starý klíč zůstává " + i, manager.exists(misto(i), "o00000001"));
			Assert.assertTrue("nový klíč zůstává " + i, manager.exists(misto(i), "o00000002"));
		}
	}

	/** Cache, kterou nejde otevřít, hlásí nedokončený úklid, ne nula smazaných dlaždic. */
	@Test
	public void cacheKterouNejdeOtevritHlasiNedokonceniUklidu() throws Exception {
		final File soubor = tmp.newFile("neni-slozka");
		final KachleCacheFolderHolder holder = new KachleCacheFolderHolder();
		holder.setKachleCacheDir(new Filex(soubor, false, true));
		final KachleDBManager db = new KachleDBManager(holder);
		Assert.assertEquals(KachleDBManager.NEDOKONCENO, db.smazOfflineKrome(Collections.singleton("o00000001")));
		Assert.assertEquals(KachleDBManager.NEDOKONCENO, db.smazOfflineNesedici("o00000001", loc -> "o00000001.00000000"));
	}
}
