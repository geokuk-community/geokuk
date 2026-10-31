package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Wgs;
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
	private boolean vMapach = true;
	private final Map<String, List<KaLoc>> hotove = new HashMap<>();
	private int dotazyNaHotovo;

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
		public List<KaLoc> zapamatovanyVzorek(final String k) {
			dotazyNaHotovo++;
			return hotove.get(k);
		}

		@Override
		public void zapamatujHotovo(final String k, final List<KaLoc> vzorek) {
			hotove.put(k, vzorek);
		}

		@Override
		public boolean vykresli(final KaLoc loc, final String k) {
			if (!vMapach) {
				return false;
			}
			vykreslene.add(loc);
			cache.add(loc);
			priVykresleni.run();
			return true;
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

	private static List<KaLoc> fronta(final File slozka) throws IOException {
		final OfflineMapy om = new OfflineMapy(() -> {});
		try {
			om.kontrolaSlozkyNs = 0;
			om.nastav(slozka, TemaOfflineMapy.VYCHOZI);
			final OfflineRenderer r = om.pouzij();
			try {
				return r.dlazdicePokryti(PredvykresleniPrehledu.Z_MIN, PredvykresleniPrehledu.Z_MAX);
			} finally {
				r.skonci();
			}
		} finally {
			om.zavri();
		}
	}

	@Test
	public void prehledovaMapaSvetaSeDoFrontyNepocita() throws IOException {
		final File jen = tmp.newFolder("jen-mala");
		OfflineMapyTest.zkopirujMapu(jen, "kukov.map");
		final File sPrehledem = tmp.newFolder("s-prehledem");
		OfflineMapyTest.zkopirujMapu(sPrehledem, "kukov.map");
		Files.copy(new File("distribuce/offline-mapy/prehled-svet-ne.map").toPath(), new File(sPrehledem, "prehled-svet-ne.map").toPath());
		Assert.assertEquals(fronta(jen), fronta(sPrehledem));
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
						Assert.assertEquals(x, loc.getFromSzUnsignedX());
						Assert.assertEquals(y, loc.getFromSzUnsignedY());
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

	@Test
	public void poPrehleduKresliOkoliPoziceOdZ12() {
		final Wgs praha = new Wgs(50.08, 14.42);
		p.setPozice(praha);
		cas += 10 * SEKUNDA;
		final List<KaLoc> okoli = PredvykresleniPrehledu.okoli(praha, PredvykresleniPrehledu.OKRUH_KM);
		Assert.assertEquals(pokryti.size() + okoli.size(), p.pracuj());
		Assert.assertEquals(pokryti, vykreslene.subList(0, pokryti.size()));
		Assert.assertEquals(okoli, vykreslene.subList(pokryti.size(), vykreslene.size()));
		Assert.assertEquals(KaLoc.ofXyz(2212, 1387, 12), okoli.get(0));
		Assert.assertEquals(10, okoli.get(okoli.size() - 1).getMoumer());
		Assert.assertTrue("okruh 15 km je desítky dlaždic, ne stovky: " + okoli.size(), okoli.size() > 20 && okoli.size() < 100);
	}

	@Test
	public void okoliMaStropNoveVykreslenychDlazdic() {
		cas += 10 * SEKUNDA;
		for (int i = 0; i < 20; i++) {
			p.setPozice(new Wgs(45 + i, 5 + i * 3));
			p.pracuj();
		}
		Assert.assertEquals(pokryti.size() + PredvykresleniPrehledu.STROP_OKOLI, vykreslene.size());
	}

	@Test
	public void dlazdiceMimoMapyDoStropuNepocita() {
		cas += 10 * SEKUNDA;
		p.pracuj();
		vMapach = false;
		for (int i = 0; i < 10; i++) {
			p.setPozice(new Wgs(45 + i, 5 + i * 3));
			p.pracuj();
		}
		vMapach = true;
		p.setPozice(new Wgs(50.08, 14.42));
		Assert.assertEquals(PredvykresleniPrehledu.okoli(new Wgs(50.08, 14.42), PredvykresleniPrehledu.OKRUH_KM).size(), p.pracuj());
	}

	@Test
	public void dokonceniSeZapamatujeAPoRestartuSePrehledNeprochazi() {
		cas += 10 * SEKUNDA;
		Assert.assertEquals(5, p.pracuj());
		Assert.assertEquals(pokryti, hotove.get(klic));

		final PredvykresleniPrehledu poRestartu = new PredvykresleniPrehledu(mapy, () -> volno, () -> cas);
		vykreslene.clear();
		sestaveniFronty = 0;
		cas += 10 * SEKUNDA;
		Assert.assertEquals(0, poRestartu.pracuj());
		Assert.assertEquals(0, sestaveniFronty);
		Assert.assertTrue(vykreslene.isEmpty());
	}

	@Test
	public void zapamatovaneHotovoBezDlazdicVCachiSeNepouzije() {
		cas += 10 * SEKUNDA;
		p.pracuj();
		final PredvykresleniPrehledu poRestartu = new PredvykresleniPrehledu(mapy, () -> volno, () -> cas);
		cache.clear();
		vykreslene.clear();
		cas += 10 * SEKUNDA;
		Assert.assertEquals("cache se mezitím smazala", 5, poRestartu.pracuj());
	}

	@Test
	public void staciChybetJednaDlazdiceVzorku() {
		cas += 10 * SEKUNDA;
		p.pracuj();
		final PredvykresleniPrehledu poRestartu = new PredvykresleniPrehledu(mapy, () -> volno, () -> cas);
		cache.remove(pokryti.get(3));
		vykreslene.clear();
		cas += 10 * SEKUNDA;
		Assert.assertEquals(1, poRestartu.pracuj());
		Assert.assertEquals(Arrays.asList(pokryti.get(3)), vykreslene);
	}

	@Test
	public void prazdnyNeboChybejiciZaznamNeplati() {
		hotove.put(klic, new ArrayList<>());
		cas += 10 * SEKUNDA;
		Assert.assertEquals("prázdný vzorek nic nedokazuje", 5, p.pracuj());

		final PredvykresleniPrehledu dalsi = new PredvykresleniPrehledu(mapy, () -> volno, () -> cas);
		hotove.remove(klic);
		cache.clear();
		cas += 10 * SEKUNDA;
		Assert.assertEquals("bez záznamu", 5, dalsi.pracuj());
	}

	@Test
	public void poZmeneSestavyseHotoveZoomyProchazejiZnovu() {
		cas += 10 * SEKUNDA;
		p.pracuj();
		final PredvykresleniPrehledu poRestartu = new PredvykresleniPrehledu(mapy, () -> volno, () -> cas);
		klic = "o00000002";
		cache.clear();
		cas += 10 * SEKUNDA;
		Assert.assertEquals(5, poRestartu.pracuj());
		Assert.assertTrue(hotove.containsKey("o00000002"));
	}

	@Test
	public void vzorekJeRovnomernyAOmezeny() {
		final List<KaLoc> mnoho = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			mnoho.add(KaLoc.ofXyz(i, 0, 9));
		}
		final List<KaLoc> vzorek = PredvykresleniPrehledu.vzorek(mnoho);
		Assert.assertEquals(PredvykresleniPrehledu.VZOREK, vzorek.size());
		Assert.assertEquals(mnoho.get(0), vzorek.get(0));
		Assert.assertTrue(mnoho.indexOf(vzorek.get(vzorek.size() - 1)) > 80);
		Assert.assertEquals(3, PredvykresleniPrehledu.vzorek(mnoho.subList(0, 3)).size());
		Assert.assertTrue(PredvykresleniPrehledu.vzorek(new ArrayList<>()).isEmpty());
	}

	@Test
	public void hotovostSeZjistujeJednouNaKlic() {
		cas += 10 * SEKUNDA;
		p.pracuj();
		Assert.assertEquals(1, dotazyNaHotovo);
	}
}
