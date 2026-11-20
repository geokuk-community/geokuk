package cz.geokuk.plugins.challenge;

import java.awt.GraphicsEnvironment;

import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.core.coord.PoziceChangedEvent;
import cz.geokuk.core.coord.PoziceModel;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.napoveda.UkazatelVykonu;
import cz.geokuk.framework.Event0;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.plugins.challenge.NastaveniChallengeTest.MapoveUloziste;
import cz.geokuk.plugins.kesoid.EKesVztah;
import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.kind.kes.EKesDiffTerRating;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;

/** Esc a „N° ✕“ v liště se chovají jako klik do prázdné mapy: zruší i pozici programu, ◀ ▶ si pamatují poslední sektor a další klik na tutéž keš ji zvýrazní znovu. */
public class JPruhEscPoziceTest {

	private ChallengeModel model;
	private JPruhPlanovace pruh;
	private PoziceModel pozice;
	private Challenge c;
	private Wpt wpt;

	@Before
	public void setUp() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		model = new ChallengeModel();
		pruh = new JPruhPlanovace();
		model.inject(new EventFirer() {
			@Override
			public void fire(final Event0<?> event) {
				pruh.onEvent((ChallengeZmenenyEvent) event);
			}
		});
		final MapoveUloziste u = new MapoveUloziste();
		model.setUloziste(() -> u);
		model.initAfterInject();
		pruh.inject(model);
		pruh.setSize(1280, 700);
		final SledovaniVybraneKese sledovani = new SledovaniVybraneKese();
		sledovani.inject(model);
		pozice = new PoziceModel();
		pozice.inject(new EventFirer() {
			@Override
			public void fire(final Event0<?> event) {
				if (event instanceof PoziceChangedEvent) {
					sledovani.onEvent((PoziceChangedEvent) event);
				}
			}
		});
		pruh.inject(pozice);
		c = Sablony.cesko360();
		model.aktivuj(c);
		model.setZobrazena(c);
		model.setZapnuto(true);
		model.nastavVysledek(c, TextyPopupuPlanovaceTest.vysledek(new int[] { 11, 20 }, new int[0], new double[][] { { 11, 3200, 5000 }, { 20, 1000 } }), 50.0, 14.0);
		final Kes k = new Kes();
		k.setIdentifier("GC1");
		k.setVztahx(EKesVztah.NORMAL);
		k.setDifficulty(EKesDiffTerRating.values()[2]);
		k.setTerrain(EKesDiffTerRating.values()[2]);
		wpt = new Wpt();
		final double a = Math.toRadians(11.5);
		wpt.setWgs(new Wgs(50.0 + 3200 * Math.cos(a) / 111_000.0, 14.0 + 3200 * Math.sin(a) / 71_300.0));
		wpt.setSym("Geocache");
		k.addWpt(wpt);
		SwingUtilities.invokeAndWait(() -> {});
	}

	@After
	public void uklid() throws Exception {
		SwingUtilities.invokeAndWait(() -> {});
		UkazatelVykonu.setPodPruhem(false);
	}

	private void klikNaKes() throws Exception {
		SwingUtilities.invokeAndWait(() -> pozice.setPoziceNaObjekt(wpt));
		SwingUtilities.invokeAndWait(() -> {});
		pruh.doLayout();
	}

	private void esc() throws Exception {
		final javax.swing.Action a = pruh.getActionMap().get("zrusPolickoPlanovace");
		Assert.assertTrue("Esc patří liště", a.isEnabled());
		a.actionPerformed(new java.awt.event.ActionEvent(pruh, java.awt.event.ActionEvent.ACTION_PERFORMED, "esc"));
		SwingUtilities.invokeAndWait(() -> {});
		pruh.doLayout();
	}

	@Test
	public void poEscKlikNaTutezKesJiZnovuZvyrazni() throws Exception {
		klikNaKes();
		Assert.assertEquals("klik na keš zvýrazní její sektor", 11, model.zvyraznenySektor(c));
		esc();
		Assert.assertEquals("Esc zvýraznění zrušil", PosunVyberu.ZADNY_SEKTOR, model.zvyraznenySektor(c));
		Assert.assertTrue("a pozici programu, jako klik do prázdné mapy", pozice.getPoziceq().isNoPosition());
		klikNaKes();
		Assert.assertEquals("klik na tutéž keš ji zvýrazní znovu", 11, model.zvyraznenySektor(c));
	}

	@Test
	public void poEscSipkaPokracujeOdPoslednihoSektoru() throws Exception {
		klikNaKes();
		esc();
		Assert.assertEquals("paměť sektoru zůstala", 11, model.zapamatovanySektor(c));
		Assert.assertEquals("první ▶ znovu zvýrazní poslední sektor", "11°", ChallengeAkce.posun(model, true));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("druhý jde dál", "20°", ChallengeAkce.posun(model, true));
	}

	@Test
	public void escBezZvyrazneniPoziciNemeni() throws Exception {
		SwingUtilities.invokeAndWait(() -> pozice.setPoziceNaObjekt(wpt));
		model.setVybranaKes(null);
		model.zrusZvyrazneniKlikemMimo();
		SwingUtilities.invokeAndWait(() -> {});
		pruh.doLayout();
		Assert.assertEquals(PosunVyberu.ZADNY_SEKTOR, model.zvyraznenySektor(c));
		Assert.assertFalse("Esc nepatří liště, nic nezvýrazňuje", pruh.getActionMap().get("zrusPolickoPlanovace").isEnabled());
		Assert.assertFalse("pozice programu zůstala", pozice.getPoziceq().isNoPosition());
	}
}
