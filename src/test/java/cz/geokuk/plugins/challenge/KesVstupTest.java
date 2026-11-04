package cz.geokuk.plugins.challenge;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.kesoid.EKesVztah;
import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;

public class KesVstupTest {

	private static Kes kes(final String id, final EKesVztah vztah, final String sym, final double lat, final double lon) {
		final Kes k = new Kes();
		k.setIdentifier(id);
		k.setVztahx(vztah);
		final Wpt w = new Wpt();
		w.setWgs(new Wgs(lat, lon));
		w.setSym(sym);
		k.addWpt(w);
		return k;
	}

	@Test
	public void prebereIdSouradniceANalezenost() {
		final KesVstup v = KesVstup.z(kes("GC1ABC", EKesVztah.FOUND, "Geocache Found", 50.1, 14.2));
		Assert.assertEquals("GC1ABC", v.id());
		Assert.assertEquals(50.1, v.lat(), 1e-9);
		Assert.assertEquals(14.2, v.lon(), 1e-9);
		Assert.assertTrue(v.nalezena());
		Assert.assertFalse(v.lab());
		Assert.assertFalse(v.locationless());
		Assert.assertEquals("log na gc.com model neumí", VstupKes.StavLogu.NEZNAMY, v.logNaGc());
	}

	@Test
	public void nenalezenaAVlastniNejsouNalezene() {
		Assert.assertFalse(KesVstup.z(kes("GC1", EKesVztah.NORMAL, "Geocache", 50.1, 14.2)).nalezena());
		Assert.assertFalse(KesVstup.z(kes("GC2", EKesVztah.OWN, "Geocache", 50.1, 14.2)).nalezena());
	}

	@Test
	public void labALocationlessPoznaPodleSymbolu() {
		Assert.assertTrue(KesVstup.z(kes("L1", EKesVztah.FOUND, "Lab Cache", 50.1, 14.2)).lab());
		Assert.assertTrue(KesVstup.z(kes("GC9", EKesVztah.FOUND, "Locationless Cache", 50.1, 14.2)).locationless());
		Assert.assertFalse(KesVstup.z(kes("GC9", EKesVztah.FOUND, null, 50.1, 14.2)).locationless());
	}

	@Test
	public void kesBezSouradnicNeniVstup() {
		Assert.assertNull(KesVstup.z(kes("GC3", EKesVztah.FOUND, "Geocache", 0, 0)));
	}
}
