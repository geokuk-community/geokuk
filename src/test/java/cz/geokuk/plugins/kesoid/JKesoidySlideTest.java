package cz.geokuk.plugins.kesoid;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.swing.SwingUtilities;

import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.util.exception.FExceptionDumper;

public class JKesoidySlideTest {

	/** Bez sady ikon výpočet sklivce selže; vlákno musí přežít, zpracovat další požadavky a chybu ohlásit jen jednou. */
	@Test(timeout = 30000)
	public void paintovaciVlaknoPrezijeVyjimku() throws Exception {
		JKesoidySlide.ohlaseneChyby.clear();
		final long vypisuPred = pocetVypisu();
		final List<Throwable> naEdt = new CopyOnWriteArrayList<>();
		final AtomicReference<Thread.UncaughtExceptionHandler> puvodni = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			puvodni.set(Thread.currentThread().getUncaughtExceptionHandler());
			Thread.currentThread().setUncaughtExceptionHandler((t, e) -> naEdt.add(e));
		});
		try {
			final JKesoidySlide slide = new JKesoidySlide(false);
			slide.zaplanujNaplneniSklivce(new Wpt(), new Mou(0, 0));
			pockejNaPrazdnouFrontu(slide);
			for (int i = 0; i < 20; i++) {
				slide.zaplanujNaplneniSklivce(new Wpt(), new Mou(i, i));
			}
			pockejNaPrazdnouFrontu(slide);
			assertTrue(JKesoidySlide.ohlaseneChyby.contains(NullPointerException.class));
			assertEquals("chyba se ohlásí jen jednou", vypisuPred + 1, pocetVypisu());
			SwingUtilities.invokeAndWait(() -> {});
		} finally {
			SwingUtilities.invokeAndWait(() -> Thread.currentThread().setUncaughtExceptionHandler(puvodni.get()));
		}
		assertEquals("výjimky na EDT", Collections.emptyList(), naEdt);
	}

	/** Po zpracování se waypoint smí naplánovat znovu, jinak by se ikona po změně (filtr, výlet) už nepřepočítala. */
	@Test(timeout = 30000)
	public void zpracovanyWaypointSePlanujeZnovu() throws Exception {
		final java.util.concurrent.atomic.AtomicInteger vypocty = new java.util.concurrent.atomic.AtomicInteger();
		final JKesoidySlide slide = new JKesoidySlide(false) {
			private static final long serialVersionUID = 1L;

			@Override
			void computeSklivec(final Wpt wpt) {
				vypocty.incrementAndGet();
			}
		};
		final Wpt wpt = new Wpt();
		slide.zaplanujNaplneniSklivce(wpt, new Mou(0, 0));
		pockejNaPrazdnouFrontu(slide);
		while (vypocty.get() < 1) {
			Thread.sleep(10);
		}
		slide.zaplanujNaplneniSklivce(wpt, new Mou(0, 0));
		pockejNaPrazdnouFrontu(slide);
		while (vypocty.get() < 2) {
			Thread.sleep(10);
		}
	}

	/** Waypoint, který už čeká ve frontě nebo se právě počítá, se při dalších překresleních neplánuje znovu. */
	@Test(timeout = 30000)
	public void cekajiciWaypointSeNeplanujeZnovu() throws Exception {
		final java.util.concurrent.CountDownLatch pocita = new java.util.concurrent.CountDownLatch(1);
		final java.util.concurrent.CountDownLatch uvolni = new java.util.concurrent.CountDownLatch(1);
		final JKesoidySlide slide = new JKesoidySlide(false) {
			private static final long serialVersionUID = 1L;

			@Override
			void computeSklivec(final Wpt wpt) {
				pocita.countDown();
				try {
					uvolni.await();
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		};
		final Wpt wpt = new Wpt();
		slide.zaplanujNaplneniSklivce(wpt, new Mou(0, 0));
		pocita.await(); // vlákno wpt převzalo a počítá ho
		for (int i = 0; i < 100; i++) {
			slide.zaplanujNaplneniSklivce(wpt, new Mou(0, 0));
		}
		assertTrue("ve frontě " + slide.frontaWaypointu.size(), slide.frontaWaypointu.size() <= 1);
		uvolni.countDown();
		pockejNaPrazdnouFrontu(slide);
	}

	private static long pocetVypisu() throws IOException {
		final Path slozka = FExceptionDumper.EXCEPTION_DIR.toPath();
		if (!Files.isDirectory(slozka)) {
			return 0;
		}
		try (Stream<Path> soubory = Files.walk(slozka)) {
			// Jen výpisy chyb; první výpis po smazání složky vytvoří i runNumber.txt.
			return soubory.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(".html")).count();
		}
	}

	private static void pockejNaPrazdnouFrontu(final JKesoidySlide slide) throws InterruptedException {
		final long konec = System.currentTimeMillis() + 5000;
		while (!slide.frontaWaypointu.isEmpty() && System.currentTimeMillis() < konec) {
			Thread.sleep(10);
		}
		assertTrue("Požadavek ve frontě nikdo nezpracoval", slide.frontaWaypointu.isEmpty());
		Thread.sleep(200); // poslední požadavek se po vyjmutí z fronty ještě zpracovává
	}
}
