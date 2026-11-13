package cz.geokuk.plugins.mapy.stahovac;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.*;

import cz.geokuk.plugins.mapy.kachle.podklady.*;

/** Hromadné stahování počítá výsledky, při omezení ze strany serveru přestane a nedrží v paměti hotové dlaždice. */
public class DavkaStahovaniTest {

	private static KachloStav obrazek() {
		return new KachloStav(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
	}

	@Test
	public void hotovoAzPoVsechVysledcich() {
		final DavkaStahovani davka = new DavkaStahovani(2, () -> {});
		final DavkaStahovani.Dlazdice a = davka.dlazdice();
		final DavkaStahovani.Dlazdice b = davka.dlazdice();
		davka.zarazeno(a, Kanceler.EMPTY);
		davka.zarazeno(b, Kanceler.EMPTY);
		davka.zarazovaniSkonceno();
		a.send(obrazek());
		Assert.assertFalse(davka.jeHotova());
		b.send(new KachloStav(new IOException("výpadek")));
		Assert.assertTrue(davka.jeHotova());
		Assert.assertTrue(davka.popis(), davka.popis().startsWith("Hotovo: v cache je 1 z 2 dlaždic, chyb 1."));
	}

	@Test
	public void omezeniServeruZastaviDavku() {
		final AtomicInteger zruseno = new AtomicInteger();
		final DavkaStahovani davka = new DavkaStahovani(4, () -> {});
		final DavkaStahovani.Dlazdice a = davka.dlazdice();
		davka.zarazeno(a, zruseno::incrementAndGet);
		davka.zarazeno(davka.dlazdice(), zruseno::incrementAndGet);
		davka.zarazeno(davka.dlazdice(), zruseno::incrementAndGet);
		a.send(new KachloStav(new KachloDownloader.ChybaServeru(429, "Too Many Requests")));
		Assert.assertTrue(davka.jeZastavena());
		Assert.assertEquals("zbylé zařazené dlaždice se zruší", 2, zruseno.get());
		Assert.assertFalse("další se už nezařadí", davka.zarazeno(davka.dlazdice(), zruseno::incrementAndGet));
		Assert.assertEquals(3, zruseno.get());
		Assert.assertTrue(davka.popis(), davka.popis().contains("HTTP 429"));
	}

	/** Kanceler drží dlaždici i s obrázkem; po dokončení ho dávka nesmí držet, jinak paměť roste s počtem dlaždic. */
	@Test
	public void hotovaDlazdiceSeUvolni() throws InterruptedException {
		final DavkaStahovani davka = new DavkaStahovani(1000, () -> {});
		WeakReference<byte[]> obrazDlazdice = null;
		for (int i = 0; i < 1000; i++) {
			final byte[] obraz = new byte[64 * 1024];
			if (i == 0) {
				obrazDlazdice = new WeakReference<>(obraz);
			}
			final DavkaStahovani.Dlazdice d = davka.dlazdice();
			davka.zarazeno(d, () -> obraz[0]++);
			d.send(obrazek());
		}
		Assert.assertEquals(0, davka.pocetRozpracovanych());
		for (int i = 0; i < 50 && obrazDlazdice.get() != null; i++) {
			System.gc();
			Thread.sleep(20);
		}
		Assert.assertNull("hotová dlaždice zůstala v paměti", obrazDlazdice.get());
	}

	@Test
	public void dlazdiceHotovaPredZarazenimSeNedrzi() {
		final DavkaStahovani davka = new DavkaStahovani(1, () -> {});
		final DavkaStahovani.Dlazdice d = davka.dlazdice();
		d.send(obrazek());
		Assert.assertTrue(davka.zarazeno(d, Kanceler.EMPTY));
		Assert.assertEquals(0, davka.pocetRozpracovanych());
		davka.zarazovaniSkonceno();
		Assert.assertTrue(davka.jeHotova());
	}

	@Test
	public void rozpracovanaSeDrziAZastaveniJiZrusi() {
		final AtomicInteger zruseno = new AtomicInteger();
		final DavkaStahovani davka = new DavkaStahovani(2, () -> {});
		final DavkaStahovani.Dlazdice d = davka.dlazdice();
		davka.zarazeno(d, zruseno::incrementAndGet);
		Assert.assertEquals(1, davka.pocetRozpracovanych());
		davka.zastav("Zastaveno uživatelem.");
		Assert.assertEquals(1, zruseno.get());
		Assert.assertEquals(0, davka.pocetRozpracovanych());
	}

	/**
	 * Dlaždice rozesílá výsledek pod svým zámkem (Kachlice.onImageLoaded → Dlazdice.send) a její zrušení
	 * (Kachlice.kancluj) bere týž zámek; zařazení do zastavené dávky ji proto nesmí rušit pod zámkem dávky.
	 */
	@Test(timeout = 60_000)
	public void zruseniPriZarazeniDoZastaveneNeuvaznesVysledkemDlazdice() throws InterruptedException {
		final DavkaStahovani davka = new DavkaStahovani(2, () -> {});
		final DavkaStahovani.Dlazdice rozesilana = davka.dlazdice();
		davka.zarazeno(rozesilana, Kanceler.EMPTY);
		davka.zastav("Zastaveno uživatelem.");
		final AtomicInteger zruseno = new AtomicInteger();
		nesmiUvaznout(rozesilana, (zamekDlazdice, ruseniZacalo) -> {
			final boolean zarazena = davka.zarazeno(davka.dlazdice(), () -> {
				ruseniZacalo.countDown();
				synchronized (zamekDlazdice) {
					zruseno.incrementAndGet();
				}
			});
			Assert.assertFalse(zarazena);
		});
		Assert.assertEquals("dlaždice zastavené dávky se zruší", 1, zruseno.get());
		Assert.assertEquals("odmítnutá dlaždice se nedrží", 0, davka.pocetRozpracovanych());
	}

	/** Totéž pro zastavení dávky: rozpracované dlaždice se ruší až mimo zámek dávky. */
	@Test(timeout = 60_000)
	public void zastaveniNeuvaznesVysledkemDlazdice() throws InterruptedException {
		final DavkaStahovani davka = new DavkaStahovani(2, () -> {});
		final DavkaStahovani.Dlazdice rozesilana = davka.dlazdice();
		davka.zarazeno(rozesilana, Kanceler.EMPTY);
		final AtomicInteger zruseno = new AtomicInteger();
		final AtomicReference<Object> zamek = new AtomicReference<>();
		final AtomicReference<CountDownLatch> zacatek = new AtomicReference<>();
		davka.zarazeno(davka.dlazdice(), () -> {
			zacatek.get().countDown();
			synchronized (zamek.get()) {
				zruseno.incrementAndGet();
			}
		});
		nesmiUvaznout(rozesilana, (zamekDlazdice, ruseniZacalo) -> {
			zamek.set(zamekDlazdice);
			zacatek.set(ruseniZacalo);
			davka.zastav("Zastaveno uživatelem.");
		});
		Assert.assertEquals("rozpracovaná dlaždice se zruší", 1, zruseno.get());
		Assert.assertEquals(0, davka.pocetRozpracovanych());
	}

	private interface Ruseni {
		void spust(Object zamekDlazdice, CountDownLatch ruseniZacalo) throws Exception;
	}

	/**
	 * Jedno vlákno drží zámek dlaždice a čeká, až druhé začne rušit (zrušení bere týž zámek); pak pošle výsledek
	 * do dávky. Rušení pod zámkem dávky tím uvázne, což hlídá {@link ThreadMXBean#findDeadlockedThreads()}.
	 */
	private static void nesmiUvaznout(final DavkaStahovani.Dlazdice rozesilana, final Ruseni ruseni) throws InterruptedException {
		final Object zamekDlazdice = new Object();
		final CountDownLatch rozesilaniDrziZamek = new CountDownLatch(1);
		final CountDownLatch ruseniZacalo = new CountDownLatch(1);
		final AtomicReference<Throwable> chyba = new AtomicReference<>();

		final Thread rozesilani = new Thread(() -> {
			try {
				synchronized (zamekDlazdice) {
					rozesilaniDrziZamek.countDown();
					Assert.assertTrue(ruseniZacalo.await(30, TimeUnit.SECONDS));
					rozesilana.send(obrazek());
				}
			} catch (final Throwable t) {
				chyba.set(t);
			}
		}, "test-rozesilani-vysledku");
		final Thread ruseniVlakno = new Thread(() -> {
			try {
				Assert.assertTrue(rozesilaniDrziZamek.await(30, TimeUnit.SECONDS));
				ruseni.spust(zamekDlazdice, ruseniZacalo);
			} catch (final Throwable t) {
				chyba.set(t);
			}
		}, "test-ruseni");
		rozesilani.setDaemon(true);
		ruseniVlakno.setDaemon(true);
		rozesilani.start();
		ruseniVlakno.start();

		final ThreadMXBean vlakna = ManagementFactory.getThreadMXBean();
		final long konec = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while ((rozesilani.isAlive() || ruseniVlakno.isAlive()) && System.nanoTime() < konec) {
			final long[] uvazla = vlakna.findDeadlockedThreads();
			if (uvazla != null) {
				final List<Long> ids = new ArrayList<>();
				for (final long id : uvazla) {
					ids.add(id);
				}
				Assert.assertFalse("rušení a rozesílání výsledku dlaždice uvázla na zámcích",
						ids.contains(rozesilani.getId()) || ids.contains(ruseniVlakno.getId()));
			}
			rozesilani.join(10);
			ruseniVlakno.join(10);
		}
		Assert.assertFalse("vlákna nedoběhla", rozesilani.isAlive() || ruseniVlakno.isAlive());
		Assert.assertNull(String.valueOf(chyba.get()), chyba.get());
	}
}
