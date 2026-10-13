package cz.geokuk.plugins.cesty.routovani;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Wgs;

/**
 * Hledání trasy na syntetické mapě {@code routovani/cesty.map}: mřížka cest lat 49.99/50.00/50.01 × lon 14.38–14.42 po 0.01°, dálnice úhlopříčně
 * 49.99/14.38 → 50.01/14.42, soukromá zkratka 49.99/14.39 → 50.00/14.40, izolovaná pěšina u 49.9952/14.385 a na severu dva konce 50.045/14.400 a 50.045/14.402
 * spojené jen okruhem přes 50.058.
 */
public class RoutovacTest {

	/** Metrů na 0.001° zeměpisné šířky a délky u 50° s. š. */
	private static final double LAT_M = 111.195;
	private static final double LON_M = 111.195 * Math.cos(Math.toRadians(50));

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File slozka;
	private Routovac routovac;

	@Before
	public void setUp() throws Exception {
		slozka = tmp.newFolder("mapy");
		zkopirujMapu(slozka);
		routovac = Routovac.zeSlozky(() -> slozka);
	}

	@After
	public void tearDown() {
		routovac.close();
	}

	static void zkopirujMapu(final File slozka) throws IOException {
		try (InputStream is = RoutovacTest.class.getResourceAsStream("/routovani/cesty.map")) {
			Files.copy(is, new File(slozka, "cesty.map").toPath());
		}
	}

	private Trasa najdi(final double lat1, final double lon1, final double lat2, final double lon2) {
		return routovac.najdi(new Wgs(lat1, lon1), new Wgs(lat2, lon2), () -> false);
	}

	@Test
	public void poMrizceBezDalniceASoukromeCesty() {
		final Trasa t = najdi(49.99, 14.38, 50.01, 14.40);
		assertEquals(Trasa.Stav.NALEZENO, t.getStav());
		// Pravoúhle po mřížce; dálnicí by to bylo 2,9 km, soukromou zkratkou 3,1 km.
		assertEquals(20 * LAT_M + 20 * LON_M, t.getDelka(), 15);
		assertEquals(new Wgs(49.99, 14.38), t.getBody().get(0));
		assertEquals(new Wgs(50.01, 14.40), t.getBody().get(t.getBody().size() - 1));
	}

	@Test
	public void presHraniceDlazdic() {
		// Cesta lat 50.00 protíná hranice dlaždic z14 u 14.3921 a 14.4141; mapa ji tam ořízne.
		final Trasa t = najdi(50.00, 14.376, 50.00, 14.424);
		assertEquals(Trasa.Stav.NALEZENO, t.getStav());
		assertEquals(48 * LON_M, t.getDelka(), 5);
	}

	@Test
	public void prichytiSeNaSitAneOstruvek() {
		// Start 107 m od izolované pěšiny, k mřížce (lon 14.38) 214 m.
		final Trasa t = najdi(49.9953, 14.3830, 49.99, 14.40);
		assertEquals(Trasa.Stav.NALEZENO, t.getStav());
		final Wgs prichyceni = t.getBody().get(1);
		assertEquals(14.38, prichyceni.lon, 1e-5);
		assertEquals(49.9953, prichyceni.lat, 1e-5);
		assertEquals(3 * LON_M + 5.3 * LAT_M + 20 * LON_M, t.getDelka(), 10);
	}

	@Test
	public void zvetsiObdelnikKdyzVNemTrasaNeni() {
		// Konce jsou od sebe 143 m, spojení vede okruhem 1,3 km na sever.
		final Trasa t = najdi(50.045, 14.400, 50.045, 14.402);
		assertEquals(Trasa.Stav.NALEZENO, t.getStav());
		assertEquals(2 * 13 * LAT_M + 2 * LON_M, t.getDelka(), 10);
	}

	@Test
	public void bodMimoMapu() {
		assertEquals(Trasa.Stav.BEZ_MAPY, najdi(49.5, 14.4, 50.0, 14.4).getStav());
		assertEquals(Trasa.Stav.BEZ_MAPY, najdi(50.0, 14.4, 50.0, 15.0).getStav());
	}

	@Test
	public void prazdnaSlozka() throws Exception {
		try (Routovac r = Routovac.zeSlozky(() -> new File(slozka, "neni"))) {
			assertEquals(Trasa.Stav.BEZ_MAPY, r.najdi(new Wgs(50, 14.4), new Wgs(50.01, 14.4), () -> false).getStav());
		}
	}

	@Test
	public void mapaBezPodrobnychCestSeNepouzije() throws Exception {
		// Přehledová mapa světa končí zoomem pod 14.
		final File prehled = new File("distribuce/offline-mapy/prehled-svet-ne.map");
		assumeExists(prehled);
		try (Routovac r = new Routovac(() -> Collections.singletonList(prehled))) {
			assertEquals(Trasa.Stav.BEZ_MAPY, r.najdi(new Wgs(50, 14.4), new Wgs(50.01, 14.4), () -> false).getStav());
		}
	}

	private static void assumeExists(final File f) {
		Assume.assumeTrue(f + " chybí", f.isFile());
	}

	@Test
	public void zadnaCestaVOkoli() {
		// Severní okruh a mřížka spolu nesouvisí; start leží na okruhu a k mřížce by vedla 4,5 km dlouhá rovná čára.
		final Trasa t = najdi(50.045, 14.400, 50.000, 14.400);
		assertEquals(Trasa.Stav.NENALEZENO, t.getStav());
		assertTrue(t.getBody().isEmpty());
	}

	@Test
	public void stejnyStartACil() {
		final Trasa t = najdi(50.0, 14.4, 50.0, 14.4);
		assertEquals(Trasa.Stav.NALEZENO, t.getStav());
		assertEquals(0, t.getDelka(), 0);
	}

	@Test
	public void zruseni() {
		assertEquals(Trasa.Stav.ZRUSENO, routovac.najdi(new Wgs(49.99, 14.38), new Wgs(50.01, 14.40), () -> true).getStav());
		final AtomicInteger dotazu = new AtomicInteger();
		// Zrušení během čtení dlaždic.
		assertEquals(Trasa.Stav.ZRUSENO, routovac.najdi(new Wgs(49.99, 14.38), new Wgs(50.01, 14.40), () -> dotazu.incrementAndGet() > 3).getStav());
		assertEquals(Trasa.Stav.NALEZENO, najdi(49.99, 14.38, 50.01, 14.40).getStav());
	}

	@Test
	public void preruseneVlaknoMapuNerozbije() {
		Thread.currentThread().interrupt();
		final Trasa t = najdi(49.99, 14.38, 50.01, 14.40);
		assertTrue("přerušení se vrátí", Thread.interrupted());
		assertEquals(Trasa.Stav.ZRUSENO, t.getStav());
		final Trasa znovu = najdi(49.99, 14.38, 50.01, 14.40);
		assertEquals(Trasa.Stav.NALEZENO, znovu.getStav());
		assertEquals(20 * LAT_M + 20 * LON_M, znovu.getDelka(), 15);
	}

	@Test
	public void chybaCteniDlazdiceSeNeulozi() {
		final AtomicInteger dotazu = new AtomicInteger();
		// Přerušení uprostřed čtení dlaždic zavře soubor mapy, čtení dalších dlaždic selže.
		try {
			routovac.najdi(new Wgs(49.99, 14.38), new Wgs(50.01, 14.40), () -> {
				if (dotazu.incrementAndGet() == 2) {
					Thread.currentThread().interrupt();
				}
				return false;
			});
			fail("čtení přerušené mapy selže");
		} catch (final IllegalStateException e) {
			// očekávané
		} finally {
			Thread.interrupted();
		}
		final Trasa znovu = najdi(49.99, 14.38, 50.01, 14.40);
		assertEquals(Trasa.Stav.NALEZENO, znovu.getStav());
		assertEquals(20 * LAT_M + 20 * LON_M, znovu.getDelka(), 15);
	}

	@Test
	public void zruseniNaPozadiMapuNerozbije() throws Exception {
		for (int i = 0; i < 20; i++) {
			routovac.najdiNaPozadi(new Wgs(49.99, 14.38), new Wgs(50.01, 14.40)).cancel(true);
		}
		final Future<Trasa> f = routovac.najdiNaPozadi(new Wgs(49.99, 14.38), new Wgs(50.01, 14.40));
		final Trasa t = f.get(30, TimeUnit.SECONDS);
		assertEquals(Trasa.Stav.NALEZENO, t.getStav());
		assertEquals(20 * LAT_M + 20 * LON_M, t.getDelka(), 15);
	}

	@Test
	public void naPozadi() throws Exception {
		final Future<Trasa> f = routovac.najdiNaPozadi(new Wgs(49.99, 14.38), new Wgs(50.01, 14.40));
		assertEquals(Trasa.Stav.NALEZENO, f.get(30, TimeUnit.SECONDS).getStav());
	}

	@Test
	public void zmenaMapyVeSlozce() throws Exception {
		assertEquals(Trasa.Stav.NALEZENO, najdi(49.99, 14.38, 50.01, 14.40).getStav());
		routovac.close();
		Files.delete(new File(slozka, "cesty.map").toPath());
		assertEquals(Trasa.Stav.BEZ_MAPY, najdi(49.99, 14.38, 50.01, 14.40).getStav());
		zkopirujMapu(slozka);
		assertEquals(Trasa.Stav.NALEZENO, najdi(49.99, 14.38, 50.01, 14.40).getStav());
	}
}
