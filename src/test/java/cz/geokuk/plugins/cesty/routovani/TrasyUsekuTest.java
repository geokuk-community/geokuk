package cz.geokuk.plugins.cesty.routovani;

import static org.junit.Assert.*;

import java.io.File;
import java.util.concurrent.*;

import javax.swing.SwingUtilities;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.cesty.data.*;

/** Hledání tras úseků na pozadí nad syntetickou mapou {@code routovani/cesty.map} (popis v {@link RoutovacTest}). */
public class TrasyUsekuTest {

	private static final double LAT_M = 111.195;
	private static final double LON_M = 111.195 * Math.cos(Math.toRadians(50));

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final Updator updator = new Updator();
	private final BlockingQueue<Usek> spoctene = new LinkedBlockingQueue<>();
	private File slozka;
	private TrasyUseku trasy;
	private Doc doc;
	private Cesta cesta;

	@Before
	public void setUp() throws Exception {
		slozka = tmp.newFolder("mapy");
		RoutovacTest.zkopirujMapu(slozka);
		trasy = new TrasyUseku(Routovac.zeSlozky(() -> slozka), spoctene::add, 10);
		doc = new Doc();
		cesta = Cesta.create();
		updator.xadd(doc, cesta);
	}

	@After
	public void tearDown() throws Exception {
		naEdt(() -> trasy.close());
	}

	private static void naEdt(final Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	private Usek usekPoCeste(final Wgs a, final Wgs b) {
		updator.pridejNaKonec(cesta, a.toMou());
		final Bod bod = updator.pridejNaKonec(cesta, b.toMou());
		updator.setTyp(bod.getUvzad(), TypUseku.PO_CESTE);
		return bod.getUvzad();
	}

	@Test
	public void spocitaUsekPoCeste() throws Exception {
		final Usek usek = usekPoCeste(new Wgs(49.99, 14.38), new Wgs(50.01, 14.40));
		naEdt(() -> trasy.dopocitej(doc));
		assertSame(usek, spoctene.poll(30, TimeUnit.SECONDS));
		final boolean[] pocita = new boolean[1];
		naEdt(() -> pocita[0] = trasy.isPocita(usek));
		assertFalse(pocita[0]);
		assertEquals(Trasa.Stav.NALEZENO, usek.getStavTrasy());
		assertEquals(20 * LAT_M + 20 * LON_M, usek.dalka(), 15);
		assertEquals(usek.getBvzad().getMou(), usek.getTrasa().get(0));
		assertEquals(usek.getBvpred().getMou(), usek.getTrasa().get(usek.getTrasa().size() - 1));
	}

	@Test
	public void rovneAVzdusneSeNepocitaji() throws Exception {
		updator.pridejNaKonec(cesta, new Wgs(49.99, 14.38).toMou());
		final Bod b = updator.pridejNaKonec(cesta, new Wgs(50.01, 14.40).toMou());
		final Bod c = updator.pridejNaKonec(cesta, new Wgs(50.01, 14.42).toMou());
		updator.setVzdusny(c.getUvzad(), true);
		naEdt(() -> trasy.dopocitej(doc));
		assertNull(spoctene.poll(500, TimeUnit.MILLISECONDS));
		assertNull(b.getUvzad().getStavTrasy());
	}

	@Test
	public void mimoMapuBezMapy() throws Exception {
		final Usek usek = usekPoCeste(new Wgs(49.0, 14.38), new Wgs(49.01, 14.40));
		naEdt(() -> trasy.dopocitej(doc));
		assertSame(usek, spoctene.poll(30, TimeUnit.SECONDS));
		assertEquals(Trasa.Stav.BEZ_MAPY, usek.getStavTrasy());
		assertNull(usek.getTrasa());
	}

	@Test
	public void posunutyBodBehemHledaniSeSpocitaZnovu() throws Exception {
		final Usek usek = usekPoCeste(new Wgs(49.99, 14.38), new Wgs(50.01, 14.40));
		naEdt(() -> {
			trasy.dopocitej(doc);
			// Bod se posune dřív, než hledání skončí; výsledek pro starou polohu se nesmí uložit.
			updator.setMouable(cesta.getCil(), new Wgs(50.00, 14.40).toMou());
			trasy.dopocitej(doc);
		});
		assertSame(usek, spoctene.poll(30, TimeUnit.SECONDS));
		assertEquals(10 * LAT_M + 20 * LON_M, usek.dalka(), 15);
		assertNull(spoctene.poll(500, TimeUnit.MILLISECONDS));
	}

	@Test
	public void opakovanePosunutyBodNerozbijeMapu() throws Exception {
		final Usek usek = usekPoCeste(new Wgs(49.99, 14.38), new Wgs(50.01, 14.40));
		// Každý další požadavek zruší rozdělané hledání, přesto se pak trasa najde.
		for (int i = 0; i < 10; i++) {
			final Wgs cil = new Wgs(50.01 - i * 0.0005, 14.40);
			naEdt(() -> {
				updator.setMouable(cesta.getCil(), cil.toMou());
				trasy.dopocitej(doc);
			});
			Thread.sleep(15);
		}
		naEdt(() -> {
			updator.setMouable(cesta.getCil(), new Wgs(50.01, 14.40).toMou());
			trasy.dopocitej(doc);
		});
		Usek spocteny;
		do {
			spocteny = spoctene.poll(30, TimeUnit.SECONDS);
			assertNotNull(spocteny);
		} while (usek.getStavTrasy() == null);
		assertEquals(Trasa.Stav.NALEZENO, usek.getStavTrasy());
		assertEquals(20 * LAT_M + 20 * LON_M, usek.dalka(), 15);
	}

	@Test
	public void prepocitejZkusiZnovuBezMapy() throws Exception {
		final File jina = tmp.newFolder("prazdna");
		naEdt(() -> trasy.close());
		final File[] aktualni = { jina };
		trasy = new TrasyUseku(Routovac.zeSlozky(() -> aktualni[0]), spoctene::add, 10);
		final Usek usek = usekPoCeste(new Wgs(49.99, 14.38), new Wgs(50.01, 14.40));
		naEdt(() -> trasy.dopocitej(doc));
		assertSame(usek, spoctene.poll(30, TimeUnit.SECONDS));
		assertEquals(Trasa.Stav.BEZ_MAPY, usek.getStavTrasy());
		// Uživatel přidal mapu.
		aktualni[0] = slozka;
		naEdt(() -> trasy.dopocitej(doc));
		assertNull("výsledek bez mapy platí, dokud se nepřepočítá", spoctene.poll(300, TimeUnit.MILLISECONDS));
		naEdt(() -> trasy.prepocitej(doc));
		assertSame(usek, spoctene.poll(30, TimeUnit.SECONDS));
		assertEquals(Trasa.Stav.NALEZENO, usek.getStavTrasy());
	}
}
