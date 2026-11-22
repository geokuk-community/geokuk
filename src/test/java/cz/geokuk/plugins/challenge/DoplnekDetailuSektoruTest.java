package cz.geokuk.plugins.challenge;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.Event0;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.plugins.challenge.Challenge.OblastDef;
import cz.geokuk.plugins.challenge.Challenge.Stred;
import cz.geokuk.plugins.challenge.Challenge.Typ;
import cz.geokuk.plugins.challenge.Challenge.ZdrojPravidel;
import cz.geokuk.plugins.challenge.NastaveniChallengeTest.MapoveUloziste;
import cz.geokuk.plugins.kesoid.EKesVztah;
import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.detail.DoplnekDetailuEvent;
import cz.geokuk.plugins.kesoid.detail.DoplnekDetailuModel;
import cz.geokuk.plugins.kesoid.kind.kes.EKesDiffTerRating;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;

/** Řádek se sektorem v detailu keše: jen u zapnutého plánovače se zobrazenou challenge 360°, u hranice sektoru se znakem ≈. */
public class DoplnekDetailuSektoruTest {

	private static final double STRED_LAT = 50.0;
	private static final double STRED_LON = 14.0;

	private ChallengeModel model;
	private DoplnekDetailuModel doplnky;
	private DoplnekDetailuSektoru doplnek;
	private final List<DoplnekDetailuEvent> udalosti = new ArrayList<>();

	@Before
	public void setUp() {
		TvarCache.vymaz();
		model = new ChallengeModel();
		model.inject(new EventFirer() {
			@Override
			public void fire(final Event0<?> event) {}
		});
		final MapoveUloziste u = new MapoveUloziste();
		model.setUloziste(() -> u);
		model.initAfterInject();
		doplnky = new DoplnekDetailuModel();
		doplnky.inject(new EventFirer() {
			@Override
			public void fire(final Event0<?> event) {
				udalosti.add((DoplnekDetailuEvent) event);
			}
		});
		doplnek = new DoplnekDetailuSektoru();
		doplnek.inject(model);
		doplnek.inject(doplnky);
	}

	private static Wpt wptVAzimutu(final double azimut, final double metru) {
		final double a = Math.toRadians(azimut);
		final Kes k = new Kes();
		k.setIdentifier("GC1");
		k.setVztahx(EKesVztah.NORMAL);
		k.setDifficulty(EKesDiffTerRating.values()[2]);
		k.setTerrain(EKesDiffTerRating.values()[2]);
		final Wpt w = new Wpt();
		w.setWgs(new Wgs(STRED_LAT + metru * Math.cos(a) / 111_000.0, STRED_LON + metru * Math.sin(a) / (111_000.0 * Math.cos(Math.toRadians(STRED_LAT)))));
		w.setSym("Geocache");
		k.addWpt(w);
		return k.getMainWpt();
	}

	private void zobraz(final Typ typ) throws Exception {
		final Challenge c = model.ulozVlastni(new Challenge("A", typ, Stred.domov(), OblastDef.zadna(), ZdrojPravidel.PGC));
		model.aktivuj(c);
		model.setZapnuto(true);
		final BitSet ob = new BitSet(360);
		ob.set(0, 360);
		ob.clear(100);
		final VysledekSektoru.Kandidati[] poSektorech = new VysledekSektoru.Kandidati[360];
		model.nastavVysledek(c, new VysledekSektoru(ob, new BitSet(360), poSektorech, 359, new ArrayList<>()), STRED_LAT, STRED_LON);
		javax.swing.SwingUtilities.invokeAndWait(() -> {});
	}

	@Test
	public void bezChallengeNicNeregistruje() {
		doplnek.obnov();
		Assert.assertNull(doplnky.getDoplnek());
		Assert.assertTrue("bez změny se detail neobnovuje", udalosti.isEmpty());
		Assert.assertNull(doplnek.radek(wptVAzimutu(100.5, 2000)));
	}

	@Test
	public void sChallenge360UkazeSektor() throws Exception {
		zobraz(Typ.SEKTORY_360);
		doplnek.obnov();
		Assert.assertSame(doplnek, doplnky.getDoplnek());
		Assert.assertEquals(1, udalosti.size());
		Assert.assertEquals("Sektor 100°", doplnek.radek(wptVAzimutu(100.5, 2000)));
		Assert.assertEquals("Sektor 250°", doplnek.radek(wptVAzimutu(250.5, 2000)));
		doplnek.obnov();
		Assert.assertEquals("stejný přehled detail znovu neobnovuje", 1, udalosti.size());
	}

	@Test
	public void uHraniceSektoruZnakPriblizne() throws Exception {
		zobraz(Typ.SEKTORY_360);
		doplnek.obnov();
		Assert.assertEquals("Sektor ≈100°", doplnek.radek(wptVAzimutu(100.02, 2000)));
		Assert.assertEquals("Sektor ≈100°", doplnek.radek(wptVAzimutu(100.98, 2000)));
	}

	@Test
	public void poVypnutiPlanovaceSeOdregistruje() throws Exception {
		zobraz(Typ.SEKTORY_360);
		doplnek.obnov();
		model.setZapnuto(false);
		doplnek.obnov();
		Assert.assertNull(doplnky.getDoplnek());
		Assert.assertEquals(2, udalosti.size());
		Assert.assertNull(udalosti.get(1).getDoplnek());
		Assert.assertNull(doplnek.radek(wptVAzimutu(100.5, 2000)));
	}

	@Test
	public void uMaticeDTNicNeregistruje() throws Exception {
		final Challenge c = model.ulozVlastni(new Challenge("B", Typ.DT_MATICE, Stred.domov(), OblastDef.zadna(), ZdrojPravidel.PGC));
		model.aktivuj(c);
		model.setZapnuto(true);
		model.nastavVysledekPolicek(c, new VysledekPolicek(81, new ArrayList<String>(), new ArrayList<KandidatPolicka>()), STRED_LAT, STRED_LON);
		javax.swing.SwingUtilities.invokeAndWait(() -> {});
		Assert.assertNotNull("matice má přehled", model.prehled());
		doplnek.obnov();
		Assert.assertNull(doplnky.getDoplnek());
		Assert.assertNull(doplnek.radek(wptVAzimutu(100.5, 2000)));
	}
}
