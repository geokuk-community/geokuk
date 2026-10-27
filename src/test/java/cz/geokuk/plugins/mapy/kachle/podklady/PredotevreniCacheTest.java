package cz.geokuk.plugins.mapy.kachle.podklady;

import static org.junit.Assert.*;

import java.awt.Image;
import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.util.file.Filex;
import cz.geokuk.plugins.mapy.kachle.data.Ka;

/** Spojení s cache se otevírá předem, ve všech vláknech, která z cache čtou nebo do ní zapisují. */
public class PredotevreniCacheTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private KachleDBManager manager(final File slozka) {
		final KachleCacheFolderHolder holder = new KachleCacheFolderHolder();
		holder.setKachleCacheDir(new Filex(slozka, false, true));
		return new KachleDBManager(holder);
	}

	@Test
	public void predotevriOtevreSpojeniVolajicihoVlakna() throws Exception {
		final File slozka = tmp.newFolder("cache");
		final KachleDBManager manager = manager(slozka);
		assertEquals(0, manager.connections.size());
		manager.predotevri();
		assertEquals(1, manager.connections.size());
		assertTrue(new File(slozka, "tiles.sqlite").isFile());
		manager.predotevri();
		assertEquals("opakované předotevření spojení nepřidává", 1, manager.connections.size());
	}

	@Test
	public void predotevreniNeniVidetVDatech() throws Exception {
		final KachleDBManager manager = manager(tmp.newFolder("cache"));
		manager.predotevri();
		assertNull(manager.load(new Ka(cz.geokuk.plugins.mapy.kachle.data.KaLoc.ofJZ(new cz.geokuk.core.coordinates.Mou(0x40000000, 0x20000000), 13), cz.geokuk.plugins.mapy.kachle.data.EKaType.TURIST_M)));
	}

	@Test
	public void predotevreniBezPouzitelneSlozkyNespadne() throws Exception {
		final File soubor = tmp.newFile("neni-slozka");
		final KachleDBManager manager = manager(new File(soubor, "pod"));
		manager.predotevri();
		assertEquals(0, manager.connections.size());
	}

	/** Zásobník volání z jednotlivých vláken pro kontrolu, že každé vlákno čtení a zápisu dostalo svou úlohu. */
	@Test(timeout = 20000)
	public void ziskavacPredotevreVKazdemVlaknuCteniAJednomVlaknuZapisu() throws Exception {
		final Set<Thread> vlakna = ConcurrentHashMap.newKeySet();
		final CountDownLatch hotovo = new CountDownLatch(KachleZiskavac.NTHREADS_DISK + 1);
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
			public void predotevri() {
				vlakna.add(Thread.currentThread());
				hotovo.countDown();
				try {
					// Vlákna se musí překrývat, jinak by jedno vlákno stihlo víc úloh a ostatní by žádnou nedostala.
					Thread.sleep(200);
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		});
		assertTrue(hotovo.await(10, TimeUnit.SECONDS));
		assertEquals(KachleZiskavac.NTHREADS_DISK + 1, vlakna.size());
	}
}
