package cz.geokuk.plugins.mapy.kachle.podklady;

import static org.junit.Assert.*;

import java.awt.Image;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Before;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.*;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleManager.ItemToSave;

/** Zápisová fronta dlaždic: časovač běží od první čekající dlaždice a dávky jsou malé, aby commit nedržel zámek cache dlouho. */
public class KachleUkladacCasovacTest {

	private static final Ka KACHLE = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13), EKaType.TURIST_M);

	private static final class Zaznam {
		final long kdy = System.nanoTime();
		final int pocet;

		Zaznam(final int pocet) {
			this.pocet = pocet;
		}
	}

	private final List<Zaznam> zapisy = new CopyOnWriteArrayList<>();
	private KachleZiskavac ziskavac;

	@Before
	public void setUp() {
		ziskavac = new KachleZiskavac();
		ziskavac.inject(new KachleModel() {
			@Override
			public boolean isUkladatMapyNaDisk() {
				return true;
			}
		});
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
				zapisy.add(new Zaznam(itemsToSave.size()));
				return true;
			}
		});
		ziskavac.ukladac.casPoKteremSeZahajiUkladaniMs = 300;
	}

	private void ukladej(final int pocet) {
		for (int i = 0; i < pocet; i++) {
			ziskavac.ukladac.zaplanujUlozeni(new Ukladanec(KACHLE, new byte[0], null));
		}
	}

	private int zapsano() {
		return zapisy.stream().mapToInt(z -> z.pocet).sum();
	}

	private void pockej(final int pocet) throws InterruptedException {
		final long konec = System.currentTimeMillis() + 10000;
		while (zapsano() < pocet && System.currentTimeMillis() < konec) {
			Thread.sleep(20);
		}
		assertEquals(pocet, zapsano());
	}

	@Test(timeout = 30000)
	public void dlazdiceNeodkladajiZapisDonekonecna() throws Exception {
		// Dlaždice chodí každých 100 ms, časovač je 300 ms: zapsat se musí během proudu, ne až po jeho konci.
		final long zacatek = System.nanoTime();
		for (int i = 0; i < 20; i++) {
			ukladej(1);
			Thread.sleep(100);
		}
		assertFalse("Zápis se do konce proudu nestihl", zapisy.isEmpty());
		assertTrue("První zápis po " + (zapisy.get(0).kdy - zacatek) / 1_000_000 + " ms", zapisy.get(0).kdy - zacatek < 1_000_000_000L);
		assertTrue("Za 2 s proudu a časovače 300 ms víc zápisů", zapisy.size() >= 3);
		pockej(20);
	}

	@Test(timeout = 30000)
	public void davkyMajiNejvic50() throws Exception {
		ukladej(130);
		pockej(130);
		assertTrue("Dávky: " + zapisy.stream().map(z -> z.pocet).collect(java.util.stream.Collectors.toList()), zapisy.stream().allMatch(z -> z.pocet <= 50));
	}

	@Test(timeout = 30000)
	public void dlazdiceNaDalsiKoloSeUlozi() throws Exception {
		ukladej(3);
		pockej(3);
		// Časovač prvního kola už doběhl, nová dlaždice musí naplánovat nový.
		ukladej(1);
		pockej(4);
		ukladej(2);
		pockej(6);
	}

	@Test(timeout = 30000)
	public void soubehPridavaniAZapisuNicNeztrati() throws Exception {
		final int vlaken = 4;
		final int naVlakno = 500;
		final List<Thread> vlakna = new ArrayList<>();
		for (int v = 0; v < vlaken; v++) {
			final Thread t = new Thread(() -> {
				for (int i = 0; i < naVlakno; i++) {
					ukladej(1);
					if (i % 50 == 0) {
						try {
							Thread.sleep(5);
						} catch (final InterruptedException e) {
							return;
						}
					}
				}
			});
			vlakna.add(t);
			t.start();
		}
		for (final Thread t : vlakna) {
			t.join();
		}
		pockej(vlaken * naVlakno);
		assertTrue(zapisy.stream().allMatch(z -> z.pocet <= 50));
	}

	/** Simuluje pohyb mapou: požadavek na dlaždici právě přišel. */
	private void pohybMapou(final java.util.concurrent.atomic.AtomicLong hodiny) throws Exception {
		final java.lang.reflect.Field f = KachleZiskavac.class.getDeclaredField("posledniPozadavek");
		f.setAccessible(true);
		f.setLong(ziskavac, hodiny.get());
	}

	@Test(timeout = 30000)
	public void priPohybuMapouSeZapisOdkladaAleJenDoHorniehoLimitu() throws Exception {
		final java.util.concurrent.atomic.AtomicLong hodiny = new java.util.concurrent.atomic.AtomicLong(1_000_000);
		ziskavac.ukladac.hodiny = hodiny::get;
		ziskavac.ukladac.necinnostMs = 1_000_000;
		ziskavac.ukladac.nejdelsiOdlozeniUkladaniMs = 700;
		pohybMapou(hodiny);
		ukladej(3);
		Thread.sleep(500);
		assertEquals("odloženo, mapou se hýbe", 0, zapsano());
		hodiny.addAndGet(701);
		pohybMapou(hodiny);
		pockej(3);
	}

	@Test(timeout = 30000)
	public void poZastaveniMapySeZapiseHnedPoUplynutiCasovace() throws Exception {
		final java.util.concurrent.atomic.AtomicLong hodiny = new java.util.concurrent.atomic.AtomicLong(1_000_000);
		ziskavac.ukladac.hodiny = hodiny::get;
		ziskavac.ukladac.necinnostMs = 50;
		ziskavac.ukladac.nejdelsiOdlozeniUkladaniMs = 60_000;
		pohybMapou(hodiny);
		ukladej(2);
		hodiny.addAndGet(100);
		pockej(2);
	}

	@Test(timeout = 30000)
	public void mnohoCekajicichSeUkladaiPriPohybu() throws Exception {
		final java.util.concurrent.atomic.AtomicLong hodiny = new java.util.concurrent.atomic.AtomicLong(1_000_000);
		ziskavac.ukladac.hodiny = hodiny::get;
		ziskavac.ukladac.necinnostMs = 1_000_000;
		ziskavac.ukladac.nejdelsiOdlozeniUkladaniMs = 1_000_000;
		ziskavac.ukladac.nejviceCekajicich = 60;
		pohybMapou(hodiny);
		ukladej(59);
		Thread.sleep(400);
		assertEquals(0, zapsano());
		ukladej(1);
		pockej(60);
		assertTrue(zapisy.stream().allMatch(z -> z.pocet <= 50));
	}

	@Test(timeout = 30000)
	public void bezPohybuMapouSeUkladaHnedVDavkach() throws Exception {
		ukladej(50);
		pockej(50);
	}
}
