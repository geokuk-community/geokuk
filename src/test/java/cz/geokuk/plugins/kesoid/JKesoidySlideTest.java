package cz.geokuk.plugins.kesoid;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
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
		SwingUtilities.invokeAndWait(() -> Thread.currentThread().setUncaughtExceptionHandler((t, e) -> naEdt.add(e)));
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
		assertEquals("výjimky na EDT", Collections.emptyList(), naEdt);
	}

	private static long pocetVypisu() throws IOException {
		final Path slozka = FExceptionDumper.EXCEPTION_DIR.toPath();
		if (!Files.isDirectory(slozka)) {
			return 0;
		}
		try (Stream<Path> soubory = Files.walk(slozka)) {
			return soubory.filter(Files::isRegularFile).count();
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
