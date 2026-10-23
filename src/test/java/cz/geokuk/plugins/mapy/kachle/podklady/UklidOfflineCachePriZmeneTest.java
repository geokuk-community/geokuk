package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.io.File;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.mapy.kachle.data.Ka;

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
}
