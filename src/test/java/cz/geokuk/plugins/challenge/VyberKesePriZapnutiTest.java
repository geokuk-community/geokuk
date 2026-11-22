package cz.geokuk.plugins.challenge;

import java.util.ArrayList;
import java.util.BitSet;

import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.plugins.challenge.Challenge.OblastDef;
import cz.geokuk.plugins.challenge.Challenge.Stred;
import cz.geokuk.plugins.challenge.Challenge.Typ;
import cz.geokuk.plugins.challenge.Challenge.ZdrojPravidel;
import cz.geokuk.plugins.challenge.NastaveniChallengeTest.MapoveUloziste;

/** Keš vybraná před zapnutím plánovače nebo před změnou challenge sektor nevybírá; klik na keš po zapnutí ano. */
public class VyberKesePriZapnutiTest {

	private static final double STRED_LAT = 50.0;
	private static final double STRED_LON = 14.0;

	private ChallengeModel model;

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
	}

	/** Keš 150 km od středu v azimutu. */
	private static VybranaKes kes(final String id, final double azimut) {
		final double a = Math.toRadians(azimut);
		final double metru = 150_000;
		return new VybranaKes(id, STRED_LAT + metru * Math.cos(a) / 111_000.0, STRED_LON + metru * Math.sin(a) / (111_000.0 * Math.cos(Math.toRadians(STRED_LAT))), 2, 2);
	}

	private Challenge zobraz360(final String nazev) throws Exception {
		final Challenge c = model.ulozVlastni(new Challenge(nazev, Typ.SEKTORY_360, Stred.domov(), OblastDef.zadna(), ZdrojPravidel.PGC));
		model.aktivuj(c);
		model.setZapnuto(true);
		final BitSet ob = new BitSet(360);
		ob.set(0, 360);
		ob.clear(100);
		model.nastavVysledek(c, new VysledekSektoru(ob, new BitSet(360), new VysledekSektoru.Kandidati[360], 359, new ArrayList<>()), STRED_LAT, STRED_LON);
		SwingUtilities.invokeAndWait(() -> {});
		return c;
	}

	private int sektor(final VybranaKes k) {
		final int s = model.prehled().sektorBodu(k.lat(), k.lon());
		Assert.assertTrue(s >= 0);
		return s;
	}

	@Test
	public void kesVybranaPredZapnutimSektorNevybere() throws Exception {
		final VybranaKes k = kes("GC1", 250.5);
		SwingUtilities.invokeAndWait(() -> model.setVybranaKes(k));
		final Challenge c = zobraz360("A");
		Assert.assertEquals("po zapnutí se sektor dřív vybrané keše nevybírá", -1, model.zvyraznenySektor(c));
		SwingUtilities.invokeAndWait(() -> model.setVybranaKes(k));
		Assert.assertEquals("klik na tutéž keš po zapnutí sektor vybere", sektor(k), model.zvyraznenySektor(c));
	}

	@Test
	public void kesVybranaPoZapnutiSektorVybere() throws Exception {
		final Challenge c = zobraz360("A");
		final VybranaKes k = kes("GC2", 40.5);
		SwingUtilities.invokeAndWait(() -> model.setVybranaKes(k));
		Assert.assertEquals(sektor(k), model.zvyraznenySektor(c));
	}

	@Test
	public void kesVybranaPredZmenouChallengeSektorNevybere() throws Exception {
		zobraz360("A");
		SwingUtilities.invokeAndWait(() -> model.setVybranaKes(kes("GC3", 40.5)));
		final Challenge b = zobraz360("B");
		Assert.assertEquals("v jiné challenge se sektor dřív vybrané keše nevybírá", -1, model.zvyraznenySektor(b));
	}

	@Test
	public void vypnutiPlanovaceVyberNeprebira() throws Exception {
		final Challenge c = zobraz360("A");
		final VybranaKes k = kes("GC4", 40.5);
		SwingUtilities.invokeAndWait(() -> model.setVybranaKes(k));
		Assert.assertEquals(sektor(k), model.zvyraznenySektor(c));
		model.setZapnuto(false);
		model.setZapnuto(true);
		Assert.assertEquals("po novém zapnutí se sektor dřív vybrané keše nevybírá", -1, model.zvyraznenySektor(c));
	}
}
