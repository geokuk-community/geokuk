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
		ziskavac.uklidOfflineCache("o00000001", a);
		ziskavac.uklidOfflineCache("o00000001", a);
		ziskavac.uklidOfflineCache("o00000002", b);
		ziskavac.uklidOfflineCache("o00000001", a);
		ziskavac.uklidOfflineCache("o00000002", b);
		Assert.assertEquals(Arrays.asList(Collections.singleton("o00000001"), new HashSet<>(Arrays.asList("o00000001", "o00000002"))), pruchody);
	}
}
