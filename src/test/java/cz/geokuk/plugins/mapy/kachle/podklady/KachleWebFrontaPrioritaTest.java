package cz.geokuk.plugins.mapy.kachle.podklady;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/** Stahování online dlaždic vybírá z fronty podle toho, co uživatel vidí, ne podle pořadí zadání. */
public class KachleWebFrontaPrioritaTest {

	private static final class Uloha implements PrioritniFronta.Razena {
		final String jmeno;
		final long poradi;
		final List<String> spustene;
		volatile boolean zahozena;

		Uloha(final String jmeno, final long poradi, final List<String> spustene) {
			this.jmeno = jmeno;
			this.poradi = poradi;
			this.spustene = spustene;
		}

		@Override
		public void run() {
			spustene.add(jmeno);
		}

		@Override
		public long poradi(final long tedNs) {
			return poradi;
		}

		@Override
		public void zahod() {
			zahozena = true;
		}
	}

	@Test(timeout = 30000)
	public void webFrontaOnlineJdeDlePoradiAZastaraleZahazuje() throws Exception {
		final ExecutorService web = new KachleZiskavac().vlaknaWebOnline();
		final int vlaken = KachleZiskavac.NTHREADS_WEB_CORE_ONLINE;
		final List<CountDownLatch> zdrzeni = new ArrayList<>();
		final CountDownLatch obsazeno = new CountDownLatch(vlaken);
		for (int i = 0; i < vlaken; i++) {
			final CountDownLatch zdrzeni1 = new CountDownLatch(1);
			zdrzeni.add(zdrzeni1);
			web.execute(() -> {
				obsazeno.countDown();
				try {
					zdrzeni1.await();
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
		}
		assertTrue("všechna vlákna stahování obsazena", obsazeno.await(10, TimeUnit.SECONDS));

		final List<String> spustene = Collections.synchronizedList(new ArrayList<>());
		final Uloha stara = new Uloha("stara", 7, spustene);
		final Uloha mimo = new Uloha("mimo", 1_000_000, spustene);
		final Uloha stred = new Uloha("stred", 3, spustene);
		final Uloha zastarala = new Uloha("zastarala", ImageReceiver.ZASTARALA, spustene);
		final Uloha nejlepsi = new Uloha("nejlepsi", 1, spustene);
		// Zadáno v pořadí „nejhorší první“; vybírá se podle pořadí, ne podle zadání.
		for (final Uloha u : new Uloha[] { mimo, stara, zastarala, stred, nejlepsi }) {
			web.execute(u);
		}

		// Vlákna se uvolňují po jednom, takže pořadí spouštění je jednoznačné.
		for (int i = 0; i < 4; i++) {
			zdrzeni.get(i).countDown();
			final int ocekavano = i + 1;
			final long konec = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (spustene.size() < ocekavano && System.nanoTime() < konec) {
				Thread.sleep(5);
			}
		}
		assertEquals(java.util.Arrays.asList("nejlepsi", "stred", "stara", "mimo"), new ArrayList<>(spustene));
		assertTrue("zastaralá úloha se zahodila", zastarala.zahozena);
		assertFalse(spustene.contains("zastarala"));
		zdrzeni.get(4).countDown();
	}

	/** Stažení dlaždice ze skutečného získávání (Kachlice) jde do prioritní fronty, ne do obyčejné: úloha ve frontě je řazená. */
	@Test(timeout = 30000)
	public void stazeniDlazdiceZKachliceJdeDoPrioritniFronty() throws Exception {
		final KachleZiskavac ziskavac = new KachleZiskavac();
		ziskavac.inject(new cz.geokuk.core.onoffline.OnofflineModel() {
			@Override
			public boolean isOnlineMode() {
				return true;
			}
		});
		ziskavac.inject(new cz.geokuk.plugins.mapy.kachle.KachleModel() {
			@Override
			public boolean isUkladatMapyNaDisk() {
				return false;
			}
		});
		ziskavac.setKachleManager(new KachleManager() {
			@Override
			public boolean exists(final cz.geokuk.plugins.mapy.kachle.data.Ka ki) {
				return false;
			}

			@Override
			public java.awt.Image load(final cz.geokuk.plugins.mapy.kachle.data.Ka ki) {
				return null;
			}

			@Override
			public boolean save(final java.util.Collection<ItemToSave> itemsToSave) {
				return true;
			}
		});
		final ExecutorService web = ziskavac.vlaknaWebOnline();
		final int vlaken = KachleZiskavac.NTHREADS_WEB_CORE_ONLINE;
		final CountDownLatch zdrzeni = new CountDownLatch(1);
		final CountDownLatch obsazeno = new CountDownLatch(vlaken);
		for (int i = 0; i < vlaken; i++) {
			web.execute(() -> {
				obsazeno.countDown();
				try {
					zdrzeni.await();
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
		}
		assertTrue(obsazeno.await(10, TimeUnit.SECONDS));

		final java.util.concurrent.atomic.AtomicBoolean zastarale = new java.util.concurrent.atomic.AtomicBoolean();
		final cz.geokuk.plugins.mapy.kachle.data.Ka ka = new cz.geokuk.plugins.mapy.kachle.data.Ka(
				cz.geokuk.plugins.mapy.kachle.data.KaLoc.ofJZ(new cz.geokuk.core.coordinates.Mou(0x23800000, 0x28100000), 13), cz.geokuk.plugins.mapy.kachle.data.EKaType.OPEN_STREET);
		ziskavac.ziskejObsah(new KaOneReq(ka, new ImageReceiver() {
			@Override
			public void send(final KachloStav kachloStav) {
			}

			@Override
			public long dulezitost() {
				return zastarale.get() ? ImageReceiver.ZASTARALA : 0;
			}
		}, Priority.KACHLE), cz.geokuk.plugins.mapy.kachle.data.DiagnosticsData.create(null, null, null));

		try {
			final long konec = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
			while (ziskavac.frontaWebOnline().isEmpty() && System.nanoTime() < konec) {
				Thread.sleep(5);
			}
			assertFalse("stažení čeká ve frontě", ziskavac.frontaWebOnline().isEmpty());
			for (final Runnable r : ziskavac.frontaWebOnline()) {
				assertTrue("úloha ve frontě je řazená: " + r, r instanceof PrioritniFronta.Razena);
			}
		} finally {
			// Zastaralá úloha se při výběru zahodí, na síť se nesahá.
			zastarale.set(true);
			zdrzeni.countDown();
		}
	}
}
