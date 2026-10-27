package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.plugins.mapy.kachle.podklady.DiagnostikaDlazdic.Pohled;
import cz.geokuk.plugins.mapy.kachle.podklady.DiagnostikaDlazdic.Scenar;

public class DiagnostikaDlazdicTest {

	private static final long SIRKA = 1000;
	private static final long VYSKA = 800;

	@Before
	public void zapni() {
		DiagnostikaDlazdic.vynuluj();
		DiagnostikaDlazdic.zapniProTesty(true);
	}

	@After
	public void vypni() {
		DiagnostikaDlazdic.zapniProTesty(false);
		DiagnostikaDlazdic.vynuluj();
	}

	private static Deque<long[]> historie(final long[]... zaznamy) {
		final Deque<long[]> d = new ArrayDeque<>();
		for (final long[] z : zaznamy) {
			d.addLast(z);
		}
		return d;
	}

	@Test
	public void percentilyZSerazenehoPole() {
		final long[] s = new long[100];
		for (int i = 0; i < 100; i++) {
			s[i] = i + 1;
		}
		Assert.assertEquals(50, DiagnostikaDlazdic.percentil(s, 50));
		Assert.assertEquals(95, DiagnostikaDlazdic.percentil(s, 95));
		Assert.assertEquals(99, DiagnostikaDlazdic.percentil(s, 99));
		Assert.assertEquals(0, DiagnostikaDlazdic.percentil(new long[0], 50));
	}

	@Test
	public void prvniZobrazeniJeStart() {
		Assert.assertEquals(Scenar.START, DiagnostikaDlazdic.urci(Integer.MIN_VALUE, historie(), 13, 0, 0, SIRKA, VYSKA));
	}

	@Test
	public void stejneMeritkoJePosun() {
		Assert.assertEquals(Scenar.POSUN, DiagnostikaDlazdic.urci(13, historie(new long[] { 13, 0, 0 }), 13, 5000, 0, SIRKA, VYSKA));
	}

	@Test
	public void zoomPodleRozdiluUrovni() {
		final Deque<long[]> h = historie(new long[] { 13, 0, 0 });
		Assert.assertEquals(Scenar.ZOOM_1, DiagnostikaDlazdic.urci(13, h, 14, 0, 0, SIRKA, VYSKA));
		Assert.assertEquals(Scenar.ZOOM_1, DiagnostikaDlazdic.urci(13, h, 12, 0, 0, SIRKA, VYSKA));
		Assert.assertEquals(Scenar.ZOOM_2, DiagnostikaDlazdic.urci(13, h, 15, 0, 0, SIRKA, VYSKA));
		Assert.assertEquals(Scenar.ZOOM_3_A_VIC, DiagnostikaDlazdic.urci(13, h, 10, 0, 0, SIRKA, VYSKA));
	}

	@Test
	public void navratNaDrivejsiMistoAMeritko() {
		// Byl jsem na 13 u [0,0], pak na 10, teď se vracím na 13 skoro tamtéž.
		final Deque<long[]> h = historie(new long[] { 10, 50, 50 }, new long[] { 13, 100, 100 });
		Assert.assertEquals(Scenar.NAVRAT, DiagnostikaDlazdic.urci(10, h, 13, 200, 150, SIRKA, VYSKA));
	}

	@Test
	public void navratNeniBezprostrednePredchoziZobrazeni() {
		// Bezprostředně předchozí zobrazení je 13, takže 13 → 13 je posun, ne návrat.
		final Deque<long[]> h = historie(new long[] { 13, 0, 0 }, new long[] { 13, 0, 0 });
		Assert.assertEquals(Scenar.POSUN, DiagnostikaDlazdic.urci(13, h, 13, 0, 0, SIRKA, VYSKA));
	}

	@Test
	public void navratVyzadujeBlizkostMista() {
		final Deque<long[]> h = historie(new long[] { 10, 50, 50 }, new long[] { 13, 100, 100 });
		Assert.assertEquals(Scenar.ZOOM_3_A_VIC, DiagnostikaDlazdic.urci(10, h, 13, 100_000, 100, SIRKA, VYSKA));
	}

	@Test
	public void pohledPocitaPrvniAPosledniDlazdici() throws Exception {
		final Pohled pohled = DiagnostikaDlazdic.novyPohled("Podklad", 13, 0, 0, SIRKA, VYSKA, 2);
		Assert.assertNotNull(pohled);
		final long zadano = System.nanoTime();
		final KachloStav disk = new KachloStav((java.awt.Image) null);
		disk.setZdroj("DISK");
		DiagnostikaDlazdic.dlazdiceHotova(pohled, disk, zadano);
		Assert.assertEquals(-1, pohled.kompletniNs());
		final KachloStav render = new KachloStav((java.awt.Image) null);
		render.setZdroj("RENDER");
		DiagnostikaDlazdic.dlazdiceHotova(pohled, render, zadano);
		Assert.assertTrue(pohled.kompletniNs() >= 0);
		final String souhrn = DiagnostikaDlazdic.souhrn();
		Assert.assertTrue(souhrn, souhrn.contains("zobrazení Podklad / start: do první dlaždice"));
		Assert.assertTrue(souhrn, souhrn.contains("zobrazení Podklad / start: do poslední dlaždice"));
		Assert.assertTrue(souhrn, souhrn.contains("dlaždice start / DISK"));
		Assert.assertTrue(souhrn, souhrn.contains("dlaždice start / RENDER"));
		Assert.assertTrue(souhrn, souhrn.contains("Nejpomalejší zobrazení mapy:\n  Podklad / start: 2 dlaždic"));
	}

	@Test
	public void dalsiPohledPreruseniNedokonceneho() {
		DiagnostikaDlazdic.novyPohled("Podklad", 13, 0, 0, SIRKA, VYSKA, 4);
		DiagnostikaDlazdic.novyPohled("Podklad", 13, 5000, 0, SIRKA, VYSKA, 4);
		final String souhrn = DiagnostikaDlazdic.souhrn();
		Assert.assertTrue(souhrn, souhrn.contains("přerušeno před dokončením"));
	}

	@Test
	public void pohledBezNovychDlazdicSeNezaznamena() {
		Assert.assertNull(DiagnostikaDlazdic.novyPohled("Podklad", 13, 0, 0, SIRKA, VYSKA, 0));
		Assert.assertNotNull(DiagnostikaDlazdic.novyPohled("Podklad", 13, 5000, 0, SIRKA, VYSKA, 1));
	}

	@Test
	public void bezZapnutiNicNeznamena() {
		DiagnostikaDlazdic.zapniProTesty(false);
		Assert.assertNull(DiagnostikaDlazdic.novyPohled("Podklad", 13, 0, 0, SIRKA, VYSKA, 3));
		DiagnostikaDlazdic.dlazdiceHotova(null, new KachloStav((java.awt.Image) null), System.nanoTime());
		Assert.assertFalse(DiagnostikaDlazdic.souhrn().contains("dlaždice bez zobrazení"));
	}

	@Test
	public void dlazdiceBezPohleduJePredvykresleni() {
		final KachloStav pamet = new KachloStav((java.awt.Image) null);
		pamet.setZdroj("PAMĚŤ");
		DiagnostikaDlazdic.dlazdiceHotova(null, pamet, System.nanoTime());
		Assert.assertTrue(DiagnostikaDlazdic.souhrn().contains("dlaždice bez zobrazení (předvykreslení okolí) / PAMĚŤ"));
	}

	@Test
	public void fazeSeZapisiJednouProKazdouDlazdici() {
		final DiagnostikaDlazdic.Faze f = new DiagnostikaDlazdic.Faze();
		f.diskZadano(7, 2);
		f.diskStart();
		f.diskKonec();
		f.renderZadano(3, 1);
		f.renderStart();
		f.renderKonec();
		DiagnostikaDlazdic.faze(f, "RENDER");
		final String souhrn = DiagnostikaDlazdic.souhrn();
		Assert.assertTrue(souhrn, souhrn.contains("disk: čekání ve frontě před čtením"));
		Assert.assertTrue(souhrn, souhrn.contains("minutí"));
		Assert.assertTrue(souhrn, souhrn.contains("vykreslení offline mapy"));
		Assert.assertTrue(souhrn, souhrn.contains("fronta čtení z disku při zadání: čeká: n=1, p50 7, p95 7, p99 7, max 7"));
	}

	@Test
	public void zamekAZapisSeZapisouDoSouhrnu() {
		final DiagnostikaDlazdic.Cteni c = DiagnostikaDlazdic.zacniCteni();
		DiagnostikaDlazdic.spojeni(1_000_000);
		DiagnostikaDlazdic.zamekCteni(42_000_000);
		DiagnostikaDlazdic.dotaz(2_000_000);
		DiagnostikaDlazdic.dekodovani(3_000_000);
		DiagnostikaDlazdic.skonciCteni(c);
		DiagnostikaDlazdic.zapisDavky(5_000_000, 120_000_000, 50);
		final String souhrn = DiagnostikaDlazdic.souhrn();
		Assert.assertTrue(souhrn, souhrn.contains("čekání na zámek čtení (zápis ho drží): n=1, p50 42.0 ms"));
		Assert.assertTrue(souhrn, souhrn.contains("trvání transakce (čtení cache stojí): n=1, p50 120.0 ms"));
		Assert.assertTrue(souhrn, souhrn.contains("zápis dávky: počet dlaždic: n=1, p50 50"));
	}

	@Test
	public void pauzyOdpaduVUseku() {
		DiagnostikaDlazdic.zaznamenejOdpad(1000, 30);
		DiagnostikaDlazdic.zaznamenejOdpad(2000, 70);
		DiagnostikaDlazdic.zaznamenejOdpad(3000, 5);
		Assert.assertEquals(75, DiagnostikaDlazdic.pauzyOdpadu(1500, 3500));
		Assert.assertEquals(105, DiagnostikaDlazdic.pauzyOdpadu(0, 5000));
		Assert.assertEquals(0, DiagnostikaDlazdic.pauzyOdpadu(4000, 5000));
	}

	@Test
	public void vlastnostSeVyhodnotiOpatrne() {
		Assert.assertFalse(DiagnostikaDlazdic.jeZapnuto(null));
		Assert.assertFalse(DiagnostikaDlazdic.jeZapnuto(""));
		Assert.assertFalse(DiagnostikaDlazdic.jeZapnuto("false"));
		Assert.assertFalse(DiagnostikaDlazdic.jeZapnuto("0"));
		Assert.assertTrue(DiagnostikaDlazdic.jeZapnuto("true"));
		Assert.assertTrue(DiagnostikaDlazdic.jeZapnuto("ano"));
	}
}
