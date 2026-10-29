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

	@Test
	public void stejneKliceSeNeprochazeji() throws Exception {
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
				pruchody.add(new HashSet<>(ponechat));
				return 0;
			}
		});
		ziskavac.uklidOfflineCache(OblastniKlic.bezSouboru("o00000001"), a);
		ziskavac.uklidOfflineCache(OblastniKlic.bezSouboru("o00000001"), a);
		ziskavac.uklidOfflineCache(OblastniKlic.bezSouboru("o00000002"), b);
		ziskavac.uklidOfflineCache(OblastniKlic.bezSouboru("o00000001"), a);
		ziskavac.uklidOfflineCache(OblastniKlic.bezSouboru("o00000002"), b);
		Assert.assertEquals(Arrays.asList(Collections.singleton("o00000001"), new HashSet<>(Arrays.asList("o00000001", "o00000002"))), pruchody);
	}

	/** Změna souborů sestavy při stejných klíčích zamete jen nesedící dlaždice; globální klíče se neprochází. */
	@Test
	public void zmenaSestavyZametaNesediciDlazdice() throws Exception {
		final File slozka = tmp.newFolder("offline-temata");
		final File a = new File(slozka, "symboly-0000000a.bin");
		final List<String> zameteno = new ArrayList<>();
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
				pruchody.add(new HashSet<>(ponechat));
				return 0;
			}

			@Override
			public int smazOfflineNesedici(final String globalni, final java.util.function.Function<cz.geokuk.plugins.mapy.kachle.data.KaLoc, String> ocekavany) {
				zameteno.add(globalni);
				return 0;
			}
		});
		final org.mapsforge.core.model.BoundingBox kdekoli = new org.mapsforge.core.model.BoundingBox(10, 10, 11, 11);
		final OblastniKlic jedna = new OblastniKlic("o00000001", Collections.singletonList(new OblastniKlic.Zdroj(kdekoli, "mapa-1")));
		final OblastniKlic dve = new OblastniKlic("o00000001", Collections.singletonList(new OblastniKlic.Zdroj(kdekoli, "mapa-2")));
		ziskavac.uklidOfflineCache(jedna, a);
		ziskavac.uklidOfflineCache(jedna, a);
		ziskavac.uklidOfflineCache(dve, a);
		ziskavac.uklidOfflineCache(dve, a);
		ziskavac.uklidOfflineCache(jedna, a);
		Assert.assertEquals("zametá se při prvním zápisu a při každé změně sestavy", Arrays.asList("o00000001", "o00000001", "o00000001"), zameteno);
		Assert.assertEquals("globální klíče se neprochází znovu", Collections.singletonList(Collections.singleton("o00000001")), pruchody);
	}

	private static byte[] png() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}

	private static Ka misto(final int i) {
		return new Ka(KaLoc.ofJZ(new Mou(0x40000000 + (i << 19), 0x20000000), 13), EKaType.OFFLINE_MF);
	}

	/**
	 * Nová verze téže varianty smaže dlaždice starého klíče, ale dlaždice nového klíče, i ty, které předvykreslení ukládá za běhu úklidu, zůstanou;
	 * jiná varianta se nedotkne.
	 */
	@Test
	public void novaVerzeVariantySmazeStarouAleNeDlazdiceKterePredvykresleniUkladaZaBehu() throws Exception {
		final File slozka = tmp.newFolder("offline-temata");
		final File cache = tmp.newFolder("cache");
		final KachleCacheFolderHolder holder = new KachleCacheFolderHolder();
		holder.setKachleCacheDir(new Filex(cache, false, true));
		final KachleDBManager manager = new KachleDBManager(holder);
		final KachleZiskavac ziskavac = new KachleZiskavac();
		ziskavac.setKachleManager(manager);

		final String varianta = "0123abcd";
		final String jinaVarianta = "0123abce";
		final File a = new File(slozka, "symboly-0000000a.bin");
		final File j = new File(slozka, "symboly-0000000f.bin");
		final File b = new File(slozka, "symboly-0000000b.bin");
		ziskavac.uklidOfflineCache(new OblastniKlic("o000000ff", Collections.emptyList(), null, jinaVarianta), j);
		ziskavac.uklidOfflineCache(new OblastniKlic("o00000001", Collections.emptyList(), null, varianta), a);

		final int pocet = 300;
		final List<KachleManager.ItemToSave> davka = new ArrayList<>();
		for (int i = 0; i < pocet; i++) {
			davka.add(new KachleManager.ItemToSave(misto(i), "o00000001", png()));
			davka.add(new KachleManager.ItemToSave(misto(i), "o000000ff", png()));
			davka.add(new KachleManager.ItemToSave(misto(i), "o00000002", png()));
		}
		Assert.assertTrue(manager.save(davka));

		// Úklid po aktualizaci tématu běží, zatímco předvykreslení ukládá další dlaždice nového klíče.
		final ExecutorService vlakno = Executors.newSingleThreadExecutor();
		final CountDownLatch zacal = new CountDownLatch(1);
		final Future<?> predvykresleni = vlakno.submit(() -> {
			zacal.countDown();
			for (int i = pocet; i < pocet + 100; i++) {
				manager.save(Collections.singleton(new KachleManager.ItemToSave(misto(i), "o00000002", png())));
			}
			return null;
		});
		Assert.assertTrue(zacal.await(10, TimeUnit.SECONDS));
		ziskavac.uklidOfflineCache(new OblastniKlic("o00000002", Collections.emptyList(), null, varianta), b);
		predvykresleni.get(60, TimeUnit.SECONDS);
		vlakno.shutdown();

		for (int i = 0; i < pocet + 100; i++) {
			Assert.assertTrue("nový klíč má dlaždici " + i, manager.exists(misto(i), "o00000002"));
			Assert.assertEquals("starý klíč téže varianty je pryč " + i, false, manager.exists(misto(i), "o00000001"));
			Assert.assertEquals("jiná varianta zůstává " + i, i < pocet, manager.exists(misto(i), "o000000ff"));
		}
		Assert.assertEquals(new HashSet<>(Arrays.asList("o00000002", "o000000ff")), UklidOfflineCache.ponechane(slozka));
	}

	/** Neúspěšné mazání dlaždic nezapíše soubor klíčů, takže se příští úklid zopakuje a nezůstanou sirotci. */
	@Test
	public void neuspesneMazaniSeZopakuje() throws Exception {
		final File slozka = tmp.newFolder("offline-temata");
		final File cache = tmp.newFolder("cache");
		final KachleCacheFolderHolder holder = new KachleCacheFolderHolder();
		holder.setKachleCacheDir(new Filex(cache, false, true));
		final KachleDBManager db = new KachleDBManager(holder);
		final int[] selhani = { 0 };
		final KachleManager rozbitelny = new KachleManager() {
			@Override
			public boolean exists(final Ka ki) {
				return db.exists(ki);
			}

			@Override
			public Image load(final Ka ki) {
				return db.load(ki);
			}

			@Override
			public boolean save(final Collection<ItemToSave> itemsToSave) {
				return db.save(itemsToSave);
			}

			@Override
			public int smazOfflineKrome(final Set<String> ponechat) {
				if (selhani[0]-- > 0) {
					return KachleDBManager.NEDOKONCENO;
				}
				return db.smazOfflineKrome(ponechat);
			}
		};
		final KachleZiskavac ziskavac = new KachleZiskavac();
		ziskavac.setKachleManager(rozbitelny);
		final String varianta = "0123abcd";
		final File a = new File(slozka, "symboly-0000000a.bin");
		final File b = new File(slozka, "symboly-0000000b.bin");
		ziskavac.uklidOfflineCache(new OblastniKlic("o00000001", Collections.emptyList(), null, varianta), a);
		final List<KachleManager.ItemToSave> davka = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			davka.add(new KachleManager.ItemToSave(misto(i), "o00000001", png()));
			davka.add(new KachleManager.ItemToSave(misto(i), "o00000002", png()));
		}
		Assert.assertTrue(db.save(davka));

		final OblastniKlic nova = new OblastniKlic("o00000002", Collections.emptyList(), null, varianta);
		selhani[0] = 1;
		ziskavac.uklidOfflineCache(nova, b);
		Assert.assertEquals("po selhání se soubor klíčů nezměnil", Collections.singleton("o00000001"), UklidOfflineCache.ponechane(slozka));
		Assert.assertTrue("dlaždice nahrazeného klíče zatím zůstávají", db.exists(misto(0), "o00000001"));

		ziskavac.uklidOfflineCache(nova, b);
		Assert.assertEquals(Collections.singleton("o00000002"), UklidOfflineCache.ponechane(slozka));
		for (int i = 0; i < 50; i++) {
			Assert.assertFalse("sirotek " + i, db.exists(misto(i), "o00000001"));
			Assert.assertTrue("nový klíč " + i, db.exists(misto(i), "o00000002"));
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
