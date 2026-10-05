package cz.geokuk.plugins.kesoid;

import static org.junit.Assert.*;

import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.swing.SwingUtilities;

import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.program.FConst;
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

	@Test
	public void limitTecekAIkon() {
		assertEquals(0, JKesoidySlide.prekrocenyLimit(true, false, FConst.MAX_POC_TECEK_NA_MAPE));
		assertEquals(FConst.MAX_POC_TECEK_NA_MAPE, JKesoidySlide.prekrocenyLimit(true, false, FConst.MAX_POC_TECEK_NA_MAPE + 1));
		assertEquals(0, JKesoidySlide.prekrocenyLimit(false, false, FConst.MAX_POC_WPT_NA_MAPE));
		assertEquals(FConst.MAX_POC_WPT_NA_MAPE, JKesoidySlide.prekrocenyLimit(false, false, FConst.MAX_POC_WPT_NA_MAPE + 1));
		assertEquals("při tisku a exportu se limit neuplatní", 0, JKesoidySlide.prekrocenyLimit(true, true, Integer.MAX_VALUE));
	}

	@Test
	public void teckySeNekresliNadLimitem() {
		for (final boolean prekrocenLimit : new boolean[] { false, true }) {
			final List<String> volani = new ArrayList<>();
			final JKesoidySlide slide = new JKesoidySlide(false) {
				@Override
				void kresliTecky(final Graphics2D gg, final EnumMap<Wpt.EZOrder, List<Wpt>> mapa, final int pocet) {
					volani.add("tecky");
				}

				@Override
				void kresli(final Graphics2D gg, final EnumMap<Wpt.EZOrder, List<Wpt>> mapa, final boolean bezWaypointu) {
					volani.add(bezWaypointu ? "bezIkon" : "ikony");
				}
			};
			final EnumMap<Wpt.EZOrder, List<Wpt>> mapa = new EnumMap<>(Wpt.EZOrder.class);
			slide.kresliWaypointy(null, mapa, true, prekrocenLimit, 1);
			assertEquals(prekrocenLimit ? Arrays.asList("bezIkon") : Arrays.asList("tecky", "bezIkon"), volani);
		}
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
