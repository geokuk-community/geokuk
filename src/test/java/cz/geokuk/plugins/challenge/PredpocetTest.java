package cz.geokuk.plugins.challenge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.challenge.Challenge.ZdrojPravidel;
import cz.geokuk.plugins.challenge.OblastPolygony.Cast;
import cz.geokuk.plugins.challenge.OblastPolygony.Okraj;
import cz.geokuk.plugins.challenge.SouborPredpoctu.Tabulka;
import cz.geokuk.plugins.challenge.VypocetSektoru.Klasifikator;

public class PredpocetTest {

	private static final double LAT = 50.0875;
	private static final double LON = 14.4208;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private Path adresar;
	private Oblast oblast;
	private final List<Predpocet> otevrene = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		adresar = tmp.newFolder("predpocet").toPath();
		oblast = new OblastPolygony("o", "v1", Collections.singletonList(new Cast(OblastPolygonyTest.ctverec(49.5, 13.5, 50.7, 15.3), Collections.<Okraj> emptyList())));
	}

	private Predpocet predpocet(final String zdroj, final Oblast o, final ZdrojPravidel pravidla, final double lat) {
		final Predpocet p = new Predpocet(adresar, zdroj, pravidla, lat, LON, o);
		otevrene.add(p);
		return p;
	}

	@org.junit.After
	public void zavri() {
		for (final Predpocet p : otevrene) {
			p.zavri();
		}
	}

	private static List<VypocetSektoruTest.Kes> kese(final int pocet, final long seed) {
		final Random r = new Random(seed);
		final List<VypocetSektoruTest.Kes> v = new ArrayList<>();
		for (int i = 0; i < pocet; i++) {
			v.add(new VypocetSektoruTest.Kes("GC" + i, 49.0 + r.nextDouble() * 2.2, 13.0 + r.nextDouble() * 3.0, r.nextInt(3) == 0));
		}
		return v;
	}

	private static void pockej(final Predpocet p, final java.util.concurrent.Future<?> f) throws Exception {
		f.get(60, TimeUnit.SECONDS);
		Assert.assertEquals(StavPredpoctu.Faze.HOTOVO, p.stav().faze());
	}

	private Path jedinySoubor() throws IOException {
		try (java.util.stream.Stream<Path> s = Files.list(adresar)) {
			final List<Path> v = new ArrayList<>();
			s.filter(x -> x.getFileName().toString().startsWith("challenge-") && x.toString().endsWith(".bin")).forEach(v::add);
			Assert.assertEquals(v.toString(), 1, v.size());
			return v.get(0);
		}
	}

	@Test
	public void predpocitaneVysledkySeRovnajiPrimemuVypoctu() throws Exception {
		final List<VypocetSektoruTest.Kes> kese = kese(3000, 5);
		final Predpocet p = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p, p.spust(kese));
		Assert.assertEquals(3000, p.pocetPrepocitanych());
		final Klasifikator primy = VypocetSektoru.primy(ZdrojPravidel.PGC, LAT, LON, oblast);
		for (final VypocetSektoruTest.Kes k : kese) {
			Assert.assertEquals(k.id, primy.klasifikuj(k), p.klasifikator().klasifikuj(k));
		}
		final VysledekSektoru a = VypocetSektoru.spoctiSKlasifikaci(ZdrojPravidel.PGC, LAT, LON, primy, Collections.<String> emptySet(), kese, () -> false);
		final VysledekSektoru b = VypocetSektoru.spoctiSKlasifikaci(ZdrojPravidel.PGC, LAT, LON, p.klasifikator(), Collections.<String> emptySet(), kese, () -> false);
		Assert.assertEquals(a.chybejici(), b.chybejici());
		for (int s = 0; s < 360; s++) {
			Assert.assertEquals(a.jeNejisty(s), b.jeNejisty(s));
		}
	}

	@Test
	public void vysledekZPrimehoVypoctuSePoOblastiShodujeSPuvodnimRozhranim() {
		final List<VypocetSektoruTest.Kes> kese = kese(2000, 8);
		final VysledekSektoru puvodni = VypocetSektoru.spocti(ZdrojPravidel.STATOR, LAT, LON, oblast, Collections.<String> emptySet(), kese);
		final VysledekSektoru sKlasifikaci = VypocetSektoru.spoctiSKlasifikaci(ZdrojPravidel.STATOR, LAT, LON, VypocetSektoru.primy(ZdrojPravidel.STATOR, LAT, LON, oblast),
				Collections.<String> emptySet(), kese, () -> false);
		Assert.assertEquals(puvodni.chybejici(), sKlasifikaci.chybejici());
		Assert.assertEquals(puvodni.pocetZapocitanychNalezu(), sKlasifikaci.pocetZapocitanychNalezu());
	}

	@Test
	public void podruheseNicNepocitaAPoZmeneJedneKesePocitaJenJi() throws Exception {
		final List<VypocetSektoruTest.Kes> kese = kese(2500, 6);
		final Predpocet p1 = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p1, p1.spust(kese));
		final Path soubor = jedinySoubor();
		final long velikost = Files.size(soubor);

		final Predpocet p2 = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p2, p2.spust(kese));
		Assert.assertEquals("vše z uložení", 0, p2.pocetPrepocitanych());

		final List<VypocetSektoruTest.Kes> zmenene = new ArrayList<>(kese);
		final VypocetSektoruTest.Kes puvodni = zmenene.get(10);
		zmenene.set(10, new VypocetSektoruTest.Kes(puvodni.id, puvodni.lat + 0.01, puvodni.lon, puvodni.nalezena));
		final Predpocet p3 = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p3, p3.spust(zmenene));
		Assert.assertEquals("jen změněná keš", 1, p3.pocetPrepocitanych());

		zmenene.add(new VypocetSektoruTest.Kes("GCNOVA", 50.0, 14.0, true));
		final Predpocet p4 = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p4, p4.spust(zmenene));
		Assert.assertEquals("jen nová keš", 1, p4.pocetPrepocitanych());
		Assert.assertTrue("soubor s novou keší je větší", Files.size(soubor) > velikost);

		zmenene.remove(zmenene.size() - 1);
		final Predpocet p5 = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p5, p5.spust(zmenene));
		Assert.assertEquals(0, p5.pocetPrepocitanych());
		Assert.assertEquals("odebraná keš ze souboru zmizela", velikost, Files.size(soubor));
	}

	@Test
	public void jinyKlicPocitaVseASoubory() throws Exception {
		final List<VypocetSektoruTest.Kes> kese = kese(500, 7);
		final Predpocet p1 = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p1, p1.spust(kese));
		final Predpocet jinyZdroj = predpocet("z2", oblast, ZdrojPravidel.PGC, LAT);
		pockej(jinyZdroj, jinyZdroj.spust(kese));
		Assert.assertEquals(500, jinyZdroj.pocetPrepocitanych());
		final Predpocet jinyStred = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT + 0.5);
		pockej(jinyStred, jinyStred.spust(kese));
		Assert.assertEquals(500, jinyStred.pocetPrepocitanych());
		final Predpocet jinaPravidla = predpocet("z1", oblast, ZdrojPravidel.STATOR, LAT);
		pockej(jinaPravidla, jinaPravidla.spust(kese));
		Assert.assertEquals(500, jinaPravidla.pocetPrepocitanych());
		final Oblast jina = new OblastPolygony("o", "v2", Collections.singletonList(new Cast(OblastPolygonyTest.ctverec(49.5, 13.5, 50.7, 15.3), Collections.<Okraj> emptyList())));
		final Predpocet jinaVerze = predpocet("z1", jina, ZdrojPravidel.PGC, LAT);
		pockej(jinaVerze, jinaVerze.spust(kese));
		Assert.assertEquals("jiná verze oblasti", 500, jinaVerze.pocetPrepocitanych());
		try (java.util.stream.Stream<Path> s = Files.list(adresar)) {
			Assert.assertEquals("pět různých klíčů, pět souborů", 5, s.filter(x -> x.toString().endsWith(".bin")).count());
		}
		final Predpocet znovu = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(znovu, znovu.spust(kese));
		Assert.assertEquals(0, znovu.pocetPrepocitanych());
	}

	@Test
	public void poskozenyNeboCiziSouborSeZahodiAPocitaSeZnovu() throws Exception {
		final List<VypocetSektoruTest.Kes> kese = kese(800, 9);
		final Predpocet p1 = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p1, p1.spust(kese));
		final Path soubor = jedinySoubor();
		final byte[] dobry = Files.readAllBytes(soubor);

		final byte[] zlomeny = dobry.clone();
		zlomeny[zlomeny.length / 2] ^= 0x55;
		Files.write(soubor, zlomeny);
		Predpocet p = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p, p.spust(kese));
		Assert.assertEquals("po změně bajtu platí CRC jen u nového souboru", 800, p.pocetPrepocitanych());
		Assert.assertArrayEquals("opravený soubor je zase platný", dobry, Files.readAllBytes(soubor));

		Files.write(soubor, java.util.Arrays.copyOf(dobry, dobry.length - 20));
		p = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p, p.spust(kese));
		Assert.assertEquals("useknutý soubor", 800, p.pocetPrepocitanych());

		Files.write(soubor, "nesmysl".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		p = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p, p.spust(kese));
		Assert.assertEquals("cizí obsah", 800, p.pocetPrepocitanych());

		Files.write(soubor, new byte[0]);
		p = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p, p.spust(kese));
		Assert.assertEquals("prázdný soubor", 800, p.pocetPrepocitanych());
		Assert.assertArrayEquals(dobry, Files.readAllBytes(soubor));
	}

	@Test
	public void souborSJinymKlicemSeNenacte() throws Exception {
		final Tabulka t = new Tabulka(10);
		t.vloz(SouborPredpoctu.otisk("GC1"), 1, 2, (short) 3);
		final Path soubor = adresar.resolve("x.bin");
		SouborPredpoctu.zapis(soubor, "klic-a", t);
		Assert.assertNotNull(SouborPredpoctu.nacti(soubor, "klic-a"));
		Assert.assertNull(SouborPredpoctu.nacti(soubor, "klic-b"));
		Assert.assertEquals(3, SouborPredpoctu.nacti(soubor, "klic-a").najdi(SouborPredpoctu.otisk("GC1"), 1, 2));
		Assert.assertEquals("jiné souřadnice", Tabulka.NENI, SouborPredpoctu.nacti(soubor, "klic-a").najdi(SouborPredpoctu.otisk("GC1"), 1, 3));
		Assert.assertEquals("jiná keš", Tabulka.NENI, SouborPredpoctu.nacti(soubor, "klic-a").najdi(SouborPredpoctu.otisk("GC2"), 1, 2));
	}

	@Test
	public void tabulkaSeZvetsiAZachovaZaznamy() {
		final Tabulka t = new Tabulka(1);
		for (int i = 1; i <= 5000; i++) {
			t.vloz(SouborPredpoctu.otisk("GC" + i), i, -i, (short) (i % 3000));
		}
		Assert.assertEquals(5000, t.pocet());
		for (int i = 1; i <= 5000; i++) {
			Assert.assertEquals(i % 3000, t.najdi(SouborPredpoctu.otisk("GC" + i), i, -i));
		}
	}

	@Test
	public void stavyAPrubehSeHlasiPosluchaci() throws Exception {
		final List<StavPredpoctu> stavy = new CopyOnWriteArrayList<>();
		final Predpocet p = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		Assert.assertEquals(StavPredpoctu.Faze.NEBEZI, p.stav().faze());
		p.pridejPosluchace(stavy::add);
		pockej(p, p.spust(kese(20_000, 3)));
		Assert.assertEquals(StavPredpoctu.Faze.POCITA, stavy.get(0).faze());
		Assert.assertEquals(20_000, stavy.get(0).celkem());
		Assert.assertEquals(StavPredpoctu.Faze.HOTOVO, stavy.get(stavy.size() - 1).faze());
		Assert.assertEquals(1.0, p.stav().podil(), 0);
		long predchozi = -1;
		boolean prostredek = false;
		for (final StavPredpoctu s : stavy) {
			Assert.assertTrue(s.hotovo() >= predchozi);
			predchozi = s.hotovo();
			prostredek |= s.faze() == StavPredpoctu.Faze.POCITA && s.hotovo() > 0 && s.hotovo() < 20_000;
		}
		Assert.assertTrue("průběžné stavy", prostredek);
	}

	@Test
	public void vyjimkaVPosluchaciPredpocetNepreruší() throws Exception {
		final Predpocet p = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		p.pridejPosluchace(s -> {
			throw new IllegalStateException("posluchač");
		});
		pockej(p, p.spust(kese(300, 1)));
	}

	@Test
	public void zruseniZastaviPredpocetANeulozi() throws Exception {
		final CountDownLatch zacal = new CountDownLatch(1);
		final CountDownLatch pustit = new CountDownLatch(1);
		final List<VypocetSektoruTest.Kes> kese = kese(50_000, 2);
		final Predpocet p = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		p.pridejPosluchace(s -> {
			if (s.faze() == StavPredpoctu.Faze.POCITA && s.hotovo() > 0) {
				zacal.countDown();
				try {
					pustit.await(10, TimeUnit.SECONDS);
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		});
		final java.util.concurrent.Future<?> f = p.spust(kese);
		Assert.assertTrue(zacal.await(30, TimeUnit.SECONDS));
		p.zrus();
		pustit.countDown();
		f.get(30, TimeUnit.SECONDS);
		Assert.assertEquals(StavPredpoctu.Faze.ZRUSENO, p.stav().faze());
		try (java.util.stream.Stream<Path> s = Files.list(adresar)) {
			Assert.assertEquals("nic se neuložilo", 0, s.count());
		}
	}

	@Test
	public void novySpustZrusiBeziciAPoctyKlasifikatoruSeNepletou() throws Exception {
		final List<VypocetSektoruTest.Kes> kese = kese(30_000, 4);
		final Predpocet p = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		final java.util.concurrent.Future<?> prvni = p.spust(kese);
		final java.util.concurrent.Future<?> druhy = p.spust(kese);
		druhy.get(60, TimeUnit.SECONDS);
		Assert.assertTrue(prvni.isDone());
		Assert.assertEquals(StavPredpoctu.Faze.HOTOVO, p.stav().faze());
	}

	@Test
	public void chybaVeVypoctuJeStavChyba() throws Exception {
		final Oblast vadna = new Oblast() {
			@Override
			public boolean obsahuje(final double lat, final double lon) {
				throw new IllegalStateException("oblast je rozbitá");
			}

			@Override
			public String verze() {
				return "vadna";
			}
		};
		final Predpocet p = predpocet("z1", vadna, ZdrojPravidel.PGC, LAT);
		p.spust(kese(10, 1)).get(30, TimeUnit.SECONDS);
		Assert.assertEquals(StavPredpoctu.Faze.CHYBA, p.stav().faze());
		Assert.assertTrue(p.stav().chyba(), p.stav().chyba().contains("rozbitá"));
		Assert.assertTrue("klasifikátor dál počítá přímo", p.klasifikator() != null);
	}

	@Test
	public void ulozeniNaNedostupnemMisteNerozbijeVypocet() throws Exception {
		final Path soubor = tmp.newFile("neni-adresar").toPath();
		final Predpocet p = new Predpocet(soubor.resolve("podadresar"), "z1", ZdrojPravidel.PGC, LAT, LON, oblast);
		otevrene.add(p);
		pockej(p, p.spust(kese(100, 1)));
		Assert.assertEquals(100, p.pocetPrepocitanych());
	}

	@Test
	public void keseBezPredpoctuSeSpocitaPriDotazu() throws Exception {
		final List<VypocetSektoruTest.Kes> kese = kese(100, 1);
		final Predpocet p = predpocet("z1", oblast, ZdrojPravidel.PGC, LAT);
		pockej(p, p.spust(kese));
		final VypocetSektoruTest.Kes nova = new VypocetSektoruTest.Kes("GCNEW", 50.2, 14.9, true);
		Assert.assertEquals(VypocetSektoru.primy(ZdrojPravidel.PGC, LAT, LON, oblast).klasifikuj(nova), p.klasifikator().klasifikuj(nova));
	}

	@Test(expected = IllegalArgumentException.class)
	public void neplatnyStredJeChyba() {
		new Predpocet(adresar, "z", ZdrojPravidel.PGC, 95, 14, null);
	}

	@Test
	public void bezOblastiPredpocetFunguje() throws Exception {
		final Predpocet p = predpocet("z1", null, ZdrojPravidel.STATOR, LAT);
		final List<VypocetSektoruTest.Kes> kese = kese(300, 11);
		pockej(p, p.spust(kese));
		final AtomicInteger vOblasti = new AtomicInteger();
		for (final VypocetSektoruTest.Kes k : kese) {
			if (p.klasifikator().klasifikuj(k).jeVOblasti()) {
				vOblasti.incrementAndGet();
			}
		}
		Assert.assertEquals("bez omezení jsou všechny v oblasti", 300, vOblasti.get());
	}
}
