package cz.geokuk.plugins.mapy.kachle.podklady;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class PrioritniFrontaTest {

	private static class Uloha implements PrioritniFronta.Razena {
		final String jmeno;
		volatile long poradi;
		boolean zahozena;

		Uloha(final String jmeno, final long poradi) {
			this.jmeno = jmeno;
			this.poradi = poradi;
		}

		@Override
		public void run() {
		}

		@Override
		public long poradi(final long tedNs) {
			return poradi;
		}

		@Override
		public synchronized void zahod() {
			zahozena = true;
		}
	}

	@Test
	public void vybiraPodlePoradiAPriShodeDrivejsiho() {
		final PrioritniFronta f = new PrioritniFronta();
		final Uloha a = new Uloha("a", 5);
		final Uloha b = new Uloha("b", 1);
		final Uloha c = new Uloha("c", 5);
		final Uloha d = new Uloha("d", 1);
		f.offer(a);
		f.offer(b);
		f.offer(c);
		f.offer(d);
		assertSame(b, f.poll());
		assertSame(d, f.poll());
		assertSame(a, f.poll());
		assertSame(c, f.poll());
		assertNull(f.poll());
	}

	@Test
	public void poradiSeVyhodnocujeAzPriVyberu() {
		final PrioritniFronta f = new PrioritniFronta();
		final Uloha a = new Uloha("a", 1);
		final Uloha b = new Uloha("b", 2);
		f.offer(a);
		f.offer(b);
		a.poradi = 10; // uživatel mezitím posunul mapu
		assertSame(b, f.poll());
		assertSame(a, f.poll());
	}

	@Test
	public void zastaraleSePreskociAZahodi() {
		final PrioritniFronta f = new PrioritniFronta();
		final Uloha stara = new Uloha("stara", ImageReceiver.ZASTARALA);
		final Uloha dobra = new Uloha("dobra", 7);
		f.offer(stara);
		f.offer(dobra);
		assertSame(dobra, f.poll());
		assertTrue(stara.zahozena);
		assertEquals(0, f.size());
	}

	@Test
	public void obycejneUlohyJdouVPoradiVlozeni() {
		final PrioritniFronta f = new PrioritniFronta();
		final List<Runnable> v = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			final Runnable r = () -> {
			};
			v.add(r);
			f.offer(r);
		}
		for (final Runnable r : v) {
			assertSame(r, f.poll());
		}
	}

	@Test(timeout = 10_000)
	public void cekajiciTakeSeProbudiVlozenim() throws Exception {
		final PrioritniFronta f = new PrioritniFronta();
		final AtomicReference<Runnable> dostal = new AtomicReference<>();
		final CountDownLatch hotovo = new CountDownLatch(1);
		final Thread t = new Thread(() -> {
			try {
				dostal.set(f.take());
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			hotovo.countDown();
		});
		t.start();
		final Uloha stara = new Uloha("stara", ImageReceiver.ZASTARALA);
		f.offer(stara); // probudí, nic nevybere a musí zase čekat
		final Uloha dobra = new Uloha("dobra", 3);
		f.offer(dobra);
		assertTrue(hotovo.await(5, TimeUnit.SECONDS));
		assertSame(dobra, dostal.get());
		assertTrue(stara.zahozena);
	}

	@Test(timeout = 10_000)
	public void pollSTimeoutemVratiNullKdyzJeJenZastarale() throws Exception {
		final PrioritniFronta f = new PrioritniFronta();
		f.offer(new Uloha("stara", ImageReceiver.ZASTARALA));
		assertNull(f.poll(50, TimeUnit.MILLISECONDS));
		assertEquals(0, f.size());
	}

	@Test(timeout = 20_000)
	public void bezDeadlockuPriSoubeznemVkladaniAVybirani() throws Exception {
		final PrioritniFronta f = new PrioritniFronta();
		final int pocet = 2000;
		final CountDownLatch vybrano = new CountDownLatch(pocet);
		final List<Thread> vlakna = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			final Thread t = new Thread(() -> {
				try {
					for (;;) {
						final Runnable r = f.take();
						if (r != null) {
							vybrano.countDown();
						}
					}
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
			t.setDaemon(true);
			t.start();
			vlakna.add(t);
		}
		for (int i = 0; i < pocet; i++) {
			f.offer(new Uloha("u" + i, (i * 7919L) % 101));
		}
		assertTrue(vybrano.await(15, TimeUnit.SECONDS));
		vlakna.forEach(Thread::interrupt);
	}

	/** Posun mapy: vše, co zůstalo ve frontě po předchozím výřezu, se vyřídí podle nového výřezu a co z něj vypadlo, se nespustí. */
	@Test(timeout = 20_000)
	public void posunMapyViditelneJdouPrvniAZastaraleSeNespusti() throws Exception {
		final java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new PrioritniFronta());
		try {
			final CountDownLatch blokuje = new CountDownLatch(1);
			final CountDownLatch bezi = new CountDownLatch(1);
			pool.execute(() -> { // obsazuje jediné vlákno, dokud uživatel neposune mapu
				bezi.countDown();
				try {
					blokuje.await();
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
			assertTrue(bezi.await(5, TimeUnit.SECONDS));
			final List<String> spusteno = java.util.Collections.synchronizedList(new ArrayList<>());
			final List<Uloha> ulohy = new ArrayList<>();
			for (int i = 0; i < 100; i++) {
				final Uloha u = new Uloha("u" + i, i) { // původní výřez: pořadí podle čísla
					@Override
					public void run() {
						spusteno.add(jmeno);
					}
				};
				ulohy.add(u);
				pool.execute(u);
			}
			// nový výřez: sudé vypadly z mapy, liché jsou vidět, nejblíž středu ty s nejvyšším číslem
			for (int i = 0; i < 100; i++) {
				ulohy.get(i).poradi = i % 2 == 0 ? ImageReceiver.ZASTARALA : 1000 - i;
			}
			blokuje.countDown();
			pool.shutdown();
			assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
			assertEquals(50, spusteno.size());
			assertEquals("u99", spusteno.get(0));
			assertEquals("u1", spusteno.get(49));
			assertTrue(ulohy.get(0).zahozena);
		} finally {
			pool.shutdownNow();
		}
	}
}
