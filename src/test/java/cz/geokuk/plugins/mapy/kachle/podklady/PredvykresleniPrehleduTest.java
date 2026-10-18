package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

public class PredvykresleniPrehleduTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final long SEKUNDA = TimeUnit.SECONDS.toNanos(1);

	private long cas = 1000 * SEKUNDA;
	private boolean volno = true;
	private String klic = "o00000001";
	private final List<KaLoc> pokryti = new ArrayList<>();
	private final Set<KaLoc> cache = new HashSet<>();
	private final List<KaLoc> vykreslene = new ArrayList<>();
	private int sestaveniFronty;
	private Runnable priVykresleni = () -> {};

	private final PredvykresleniPrehledu.Mapy mapy = new PredvykresleniPrehledu.Mapy() {
		@Override
		public String klic() {
			return klic;
		}

		@Override
		public List<KaLoc> dlazdice(final int zMin, final int zMax) {
			sestaveniFronty++;
			return pokryti;
		}

		@Override
		public boolean jeVCache(final KaLoc loc, final String k) {
			return cache.contains(loc);
		}

		@Override
		public void vykresli(final KaLoc loc, final String k) {
			vykreslene.add(loc);
			cache.add(loc);
			priVykresleni.run();
		}
	};

	private final PredvykresleniPrehledu p = new PredvykresleniPrehledu(mapy, () -> volno, () -> cas);

	@Before
	public void setUp() {
		for (int x = 0; x < 5; x++) {
			pokryti.add(KaLoc.ofXyz(x, 0, 3));
		}
	}

	@Test
	public void kresliAzPoDesetiSekundachNecinnosti() {
		cas += 9 * SEKUNDA;
		Assert.assertEquals(0, p.pracuj());
		cas += SEKUNDA;
		Assert.assertEquals(5, p.pracuj());
		Assert.assertEquals(pokryti, vykreslene);
	}

	@Test
	public void akceUzivatelePrerusiAPokracujeSeBezOpakovani() {
		cas += 10 * SEKUNDA;
		priVykresleni = () -> {
			if (vykreslene.size() == 2) {
				p.aktivita();
			}
		};
		Assert.assertEquals(2, p.pracuj());
		Assert.assertEquals(0, p.pracuj());
		cas += 10 * SEKUNDA;
		Assert.assertEquals(3, p.pracuj());
		Assert.assertEquals(pokryti, vykreslene);
	}

	@Test
	public void nicSeNekresliDvakrat() {
		cache.add(pokryti.get(1));
		cas += 10 * SEKUNDA;
		Assert.assertEquals(4, p.pracuj());
		Assert.assertEquals(0, p.pracuj());
		cas += 60 * SEKUNDA;
		Assert.assertEquals(0, p.pracuj());
		Assert.assertEquals(1, sestaveniFronty);
		Assert.assertFalse(vykreslene.contains(pokryti.get(1)));
		Assert.assertEquals(new HashSet<>(vykreslene).size(), vykreslene.size());
	}

	@Test
	public void bezVolnaSeNekresli() {
		volno = false;
		cas += 60 * SEKUNDA;
		Assert.assertEquals(0, p.pracuj());
		volno = true;
		Assert.assertEquals(5, p.pracuj());
	}

	@Test
	public void zmenaMapCiTematuKresliZnovu() {
		cas += 10 * SEKUNDA;
		p.pracuj();
		klic = "o00000002";
		cache.clear();
		Assert.assertEquals(5, p.pracuj());
		Assert.assertEquals(2, sestaveniFronty);
	}

	@Test
	public void zmenaKliceBehemKresleniZacneNovouFrontu() {
		cas += 10 * SEKUNDA;
		priVykresleni = () -> {
			if (vykreslene.size() == 2) {
				klic = "o00000002";
				cache.clear();
			}
		};
		p.pracuj();
		Assert.assertEquals(2 + 5, vykreslene.size());
		Assert.assertEquals(pokryti, vykreslene.subList(2, 7));
	}

	@Test
	public void bezMapNicNedela() {
		klic = null;
		cas += 10 * SEKUNDA;
		Assert.assertEquals(0, p.pracuj());
		Assert.assertEquals(0, sestaveniFronty);
	}

	@Test
	public void jenOdDvouJader() {
		Assert.assertFalse(PredvykresleniPrehledu.jeVhodne(1));
		Assert.assertTrue(PredvykresleniPrehledu.jeVhodne(2));
	}

	@Test
	public void frontaJsouDlazdicePokrytiMapy() throws IOException {
		final File slozka = tmp.newFolder("offline-mapy");
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final OfflineMapy om = new OfflineMapy(() -> {});
		try {
			om.kontrolaSlozkyNs = 0;
			om.nastav(slozka, TemaOfflineMapy.VYCHOZI);
			final OfflineRenderer r = om.pouzij();
			try {
				final List<KaLoc> fronta = r.dlazdicePokryti(PredvykresleniPrehledu.Z_MIN, PredvykresleniPrehledu.Z_MAX);
				Assert.assertEquals(new HashSet<>(fronta).size(), fronta.size());
				int z = PredvykresleniPrehledu.Z_MIN;
				final Set<Integer> zoomy = new HashSet<>();
				for (final KaLoc loc : fronta) {
					Assert.assertTrue(r.pokryva(loc));
					Assert.assertTrue(loc.getMoumer() >= z);
					z = loc.getMoumer();
					zoomy.add(z);
				}
				Assert.assertEquals(PredvykresleniPrehledu.Z_MAX - PredvykresleniPrehledu.Z_MIN + 1, zoomy.size());
				// Úplnost: na z7 žádná pokrytá dlaždice světa nechybí.
				int pokrytych = 0;
				for (int x = 0; x < 128; x++) {
					for (int y = 0; y < 128; y++) {
						final KaLoc loc = KaLoc.ofXyz(x, y, 7);
						Assert.assertEquals(loc.getFromSzUnsignedX() + "/" + loc.getFromSzUnsignedY(), x * 1000 + y, loc.getFromSzUnsignedX() * 1000 + loc.getFromSzUnsignedY());
						if (r.pokryva(loc)) {
							pokrytych++;
							Assert.assertTrue(fronta.contains(loc));
						}
					}
				}
				Assert.assertTrue(pokrytych > 0);
			} finally {
				r.skonci();
			}
		} finally {
			om.zavri();
		}
	}
}
