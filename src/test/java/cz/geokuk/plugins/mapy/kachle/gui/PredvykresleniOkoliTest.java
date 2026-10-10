package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import cz.geokuk.plugins.mapy.kachle.podklady.ImageReceiver;
import cz.geokuk.plugins.mapy.kachle.podklady.KachloStav;

public class PredvykresleniOkoliTest {

	private final List<Ka> zadano = new ArrayList<>();
	private final List<ImageReceiver> prijemci = new ArrayList<>();
	private final List<Ka> zruseno = new ArrayList<>();
	private boolean hotoveRovnou;

	private final PredvykresleniOkoli p = new PredvykresleniOkoli((ka, prijemce) -> {
		zadano.add(ka);
		prijemci.add(prijemce);
		if (hotoveRovnou) {
			prijemce.send(new KachloStav(new java.awt.image.BufferedImage(1, 1, 1)));
		}
		return () -> zruseno.add(ka);
	});

	private static List<Ka> dlazdice(final int n) {
		final List<Ka> vysledek = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			vysledek.add(new Ka(KaLoc.ofJZ(new Wgs(50, 14 + i).toMou(), 12), EKaType.OFFLINE_MF));
		}
		return vysledek;
	}

	@Test
	public void dlazdiceSeZadavajiPoJedne() {
		final List<Ka> d = dlazdice(3);
		p.spust(d);
		Assert.assertEquals(d.subList(0, 1), zadano);
		prijemci.get(0).send(new KachloStav(new RuntimeException("chyba")));
		Assert.assertEquals(d.subList(0, 2), zadano);
		prijemci.get(1).send(new KachloStav(new java.awt.image.BufferedImage(1, 1, 1)));
		prijemci.get(2).send(new KachloStav(new RuntimeException()));
		Assert.assertEquals(d, zadano);
		Assert.assertTrue(p.jeSpusteno());
	}

	@Test
	public void zruseniZrusiRozdelanouDlazdiciAZbytekNezada() {
		final List<Ka> d = dlazdice(3);
		p.spust(d);
		p.zrus();
		Assert.assertEquals(d.subList(0, 1), zruseno);
		prijemci.get(0).send(new KachloStav(new RuntimeException()));
		Assert.assertEquals(1, zadano.size());
		Assert.assertFalse(p.jeSpusteno());
	}

	@Test
	public void novaFrontaZrusiStarou() {
		final List<Ka> a = dlazdice(3);
		p.spust(a);
		final List<Ka> b = dlazdice(5).subList(3, 5);
		p.spust(b);
		Assert.assertEquals(a.get(0), zruseno.get(0));
		prijemci.get(0).send(new KachloStav(new RuntimeException())); // opožděná odpověď staré fronty
		Assert.assertEquals(2, zadano.size());
		Assert.assertEquals(b.get(0), zadano.get(1));
	}

	@Test
	public void dlazdiceHotoveRovnouSeProjdouVsechny() {
		hotoveRovnou = true;
		final List<Ka> d = dlazdice(40);
		p.spust(d);
		Assert.assertEquals(d, zadano);
		p.zrus();
		Assert.assertTrue(zruseno.size() <= 1);
	}

	@Test
	public void okoliJePrstenecOdStredu() {
		final Dimension d = new Dimension(1000, 700);
		final Kaputer k = new Kaputer(new Coord(13, new Wgs(50.08, 14.42).toMou(), d, 0.0));
		final Set<KaLoc> vyrez = new HashSet<>();
		for (int y = 0; y < k.getPocetKachliY(); y++) {
			for (int x = 0; x < k.getPocetKachliX(); x++) {
				vyrez.add(k.getKaloc(x, y));
			}
		}
		final List<Ka> okoli = PredvykresleniOkoli.okoli(k, EKaType.OFFLINE_MF, d.width, d.height);
		final int px = k.getPocetKachliX();
		final int py = k.getPocetKachliY();
		Assert.assertEquals((px + 2) * (py + 2) - px * py, okoli.size());
		final Set<KaLoc> jedinecne = new HashSet<>();
		for (final Ka ka : okoli) {
			Assert.assertFalse(vyrez.contains(ka.getLoc()));
			jedinecne.add(ka.getLoc());
		}
		Assert.assertEquals(okoli.size(), jedinecne.size());
	}

	@Test
	public void napredvykresleniJeDostJader() {
		Assert.assertFalse(PredvykresleniOkoli.jeVhodne(4));
		Assert.assertTrue(PredvykresleniOkoli.jeVhodne(8));
	}
}
