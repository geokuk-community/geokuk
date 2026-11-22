package cz.geokuk.plugins.kesoid.importek;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.kind.cgp.CzechGeodeticPoint;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;

/** Zastávka Adventure Labu s kódem GC + 4 znaky + „-N“ je keš, ne český geodetický bod. */
public class LabkaNeniGeodetickyBodTest {

	/** Jediný kešoid ze souboru s jedním waypointem. */
	private static Kesoid nacti(final String kod, final String typ) throws Exception {
		final java.util.List<Kesoid> kesoidy = new java.util.ArrayList<>(
				ImportKesiTest.importuj(ImportKesiTest.gpx(ImportKesiTest.kes(kod, "Geocache", typ, "Autor", 7, true, false, "1.5", ""))).getKesoidy());
		Assert.assertEquals(kod, 1, kesoidy.size());
		return kesoidy.get(0);
	}

	@Test
	public void zastavkaLabkyJeKes() throws Exception {
		Assert.assertTrue(nacti("GCH6E5-0", "Traditional Cache") instanceof Kes);
		Assert.assertTrue(nacti("GCH6E5-0", "Unknown Cache") instanceof Kes);
		Assert.assertTrue(nacti("GCYZ76-3", "Multi-cache") instanceof Kes);
	}

	@Test
	public void pseudokesGeodetickehoBoduZustava() throws Exception {
		Assert.assertTrue(nacti("GC1A2B3C", "Traditional Cache") instanceof CzechGeodeticPoint);
		Assert.assertTrue(nacti("GCABCDEF", "Unknown Cache") instanceof CzechGeodeticPoint);
	}
}
