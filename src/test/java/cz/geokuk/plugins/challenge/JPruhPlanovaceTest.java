package cz.geokuk.plugins.challenge;

import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.framework.RozmisteniPrekryvu;
import cz.geokuk.plugins.challenge.NastaveniChallengeTest.MapoveUloziste;

/** Pruh plánovače: rozložení prvků na třech stupních šířky, viditelnost podle stavu modelu a klikání jen v obdélníku pruhu. */
public class JPruhPlanovaceTest {

	@Test
	public void prvkyZaplniPruhNaVsechTrechStupnich() {
		for (final int sirka : RozmisteniPrekryvu.STUPNE_PRUHU) {
			final Rectangle pruh = new Rectangle(RozmisteniPrekryvu.OKRAJ, RozmisteniPrekryvu.OKRAJ, sirka, RozmisteniPrekryvu.VYSKA_PRUHU);
			final Rectangle[] r = JPruhPlanovace.rozloz(sirka);
			final List<Rectangle> videt = new ArrayList<>();
			for (final Rectangle x : r) {
				if (!x.isEmpty()) {
					Assert.assertTrue(sirka + ": " + x, pruh.contains(x));
					videt.add(x);
				}
			}
			for (int i = 0; i < videt.size(); i++) {
				for (int j = i + 1; j < videt.size(); j++) {
					Assert.assertFalse(sirka + ": " + videt.get(i) + " × " + videt.get(j), videt.get(i).intersects(videt.get(j)));
				}
			}
			Assert.assertEquals("šipky u pravého okraje s podšívkou", pruh.x + pruh.width - 4, r[JPruhPlanovace.DAL].x + r[JPruhPlanovace.DAL].width);
			Assert.assertEquals("vlevo podšívka", pruh.x + 4, videt.get(0).x);
		}
	}

	@Test
	public void stupneUbiraji() {
		final Rectangle[] uplny = JPruhPlanovace.rozloz(267);
		final Rectangle[] stredni = JPruhPlanovace.rozloz(178);
		final Rectangle[] uzky = JPruhPlanovace.rozloz(106);
		Assert.assertEquals(111, uplny[JPruhPlanovace.KANDIDATI].width);
		Assert.assertEquals("jen ikona trychtýře", 22, stredni[JPruhPlanovace.KANDIDATI].width);
		Assert.assertFalse(stredni[JPruhPlanovace.TYP].isEmpty());
		Assert.assertTrue(uzky[JPruhPlanovace.TYP].isEmpty());
		Assert.assertTrue(uzky[JPruhPlanovace.KANDIDATI].isEmpty());
		Assert.assertEquals("odznak zabere zbytek", 42, uzky[JPruhPlanovace.ODZNAK].width);
		Assert.assertEquals(24, uzky[JPruhPlanovace.ZPET].width);
	}

	@Test
	public void sirkaSeVoliPodleSirkyMapyBezRezervyVpravo() {
		Assert.assertEquals(267, RozmisteniPrekryvu.sirkaPruhu(RozmisteniPrekryvu.volnaSirkaPruhu(1280)));
		Assert.assertEquals(267, RozmisteniPrekryvu.sirkaPruhu(RozmisteniPrekryvu.volnaSirkaPruhu(521)));
		Assert.assertEquals(178, RozmisteniPrekryvu.sirkaPruhu(RozmisteniPrekryvu.volnaSirkaPruhu(520)));
		Assert.assertEquals(178, RozmisteniPrekryvu.sirkaPruhu(RozmisteniPrekryvu.volnaSirkaPruhu(432)));
		Assert.assertEquals(106, RozmisteniPrekryvu.sirkaPruhu(RozmisteniPrekryvu.volnaSirkaPruhu(431)));
		Assert.assertEquals(106, RozmisteniPrekryvu.sirkaPruhu(RozmisteniPrekryvu.volnaSirkaPruhu(300)));
	}

	private final List<ChallengeZmenenyEvent> udalosti = new ArrayList<>();

	private ChallengeModel model;
	private JPruhPlanovace pruh;

	private void priprav() {
		model = new ChallengeModel();
		pruh = new JPruhPlanovace();
		model.inject(new EventFirer() {
			@Override
			public void fire(final Event0<?> event) {
				udalosti.add((ChallengeZmenenyEvent) event);
				pruh.onEvent((ChallengeZmenenyEvent) event);
			}
		});
		final MapoveUloziste u = new MapoveUloziste();
		model.setUloziste(() -> u);
		model.initAfterInject();
		pruh.inject(model);
		pruh.setSize(1280, 700);
	}

	@Test
	public void pruhJeVidetJenKdyzJeZapnutoAJeCoUkazovat() throws Exception {
		priprav();
		Assert.assertFalse(pruh.isVisible());
		model.setZapnuto(true);
		javax.swing.SwingUtilities.invokeAndWait(() -> {});
		Assert.assertFalse("bez aktivní challenge není co ukázat", pruh.isVisible());
		model.aktivuj(Sablony.cesko360());
		javax.swing.SwingUtilities.invokeAndWait(() -> {});
		Assert.assertTrue(pruh.isVisible());
		Assert.assertTrue(model.jePruhViditelny());
		model.setZapnuto(false);
		Assert.assertFalse(pruh.isVisible());
		Assert.assertFalse(model.jePruhViditelny());
	}

	@Test
	public void mimoObdelnikPruhuMysPropousti() {
		priprav();
		model.aktivuj(Sablony.cesko360());
		model.setZapnuto(true);
		final Rectangle p = RozmisteniPrekryvu.pruhPlanovace(RozmisteniPrekryvu.volnaSirkaPruhu(1280));
		Assert.assertTrue(pruh.contains(p.x + 1, p.y + 1));
		Assert.assertTrue(pruh.contains(p.x + p.width - 1, p.y + p.height - 1));
		Assert.assertFalse(pruh.contains(p.x + p.width, p.y));
		Assert.assertFalse(pruh.contains(p.x, p.y + p.height));
		Assert.assertFalse(pruh.contains(p.x - 1, p.y));
		Assert.assertFalse("střed mapy", pruh.contains(640, 350));
	}

	@Test
	public void rozlozeniDetiSeMeniSeSirkouMapy() {
		priprav();
		model.aktivuj(Sablony.cesko360());
		model.setZapnuto(true);
		for (final int sirka : new int[] { 1280, 500, 400 }) {
			pruh.setSize(sirka, 700);
			pruh.doLayout();
			final Rectangle[] r = JPruhPlanovace.rozloz(RozmisteniPrekryvu.sirkaPruhu(RozmisteniPrekryvu.volnaSirkaPruhu(sirka)));
			int viditelnych = 0;
			for (final Component c : pruh.getComponents()) {
				if (c.isVisible()) {
					viditelnych++;
					Assert.assertTrue(sirka + ": " + c.getBounds(), pruh.contains(c.getX(), c.getY()));
				}
			}
			int ocekavano = 0;
			for (final Rectangle x : r) {
				ocekavano += x.isEmpty() ? 0 : 1;
			}
			Assert.assertEquals(String.valueOf(sirka), ocekavano, viditelnych);
		}
	}
}
