package cz.geokuk.plugins.kesoid.importek;

import static cz.geokuk.plugins.kesoid.importek.ImportKesiTest.*;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Kesoid;

/** Varianty GPX z různých exportérů. */
public class ImportGpxVariantyTest {

	/** Keš bez nápovědy si nesmí vzít typ a název z logů a travel bugů. */
	@Test
	public void kesBezNapovedy() throws Exception {
		final String bezNapovedy = kes("GC3333", "Geocache", "Multi-cache", "Cizí", 1, true, false, "2", "")
				.replace("<groundspeak:encoded_hints>nápověda</groundspeak:encoded_hints>", "<groundspeak:logs><groundspeak:log><groundspeak:type>Didn't find it</groundspeak:type></groundspeak:log></groundspeak:logs>"
						+ "<groundspeak:travelbugs><groundspeak:travelbug><groundspeak:name>Cestovatel</groundspeak:name></groundspeak:travelbug></groundspeak:travelbugs>");
		final KesBag bag = importuj(gpx(bezNapovedy));
		Assert.assertEquals(1, bag.getKesoidy().size());
		final Kesoid kes = bag.getKesoidy().iterator().next();
		Assert.assertEquals("Keš GC3333", kes.getNazev());
	}

	/** Novější exporty používají jmenný prostor groundspeak cache/1/0/2. */
	@Test
	public void groundspeak102() throws Exception {
		final KesBag bag = importuj(gpx(kes("GC4444", "Geocache", "Multi-cache", "Cizí", 1, true, false, "2", "").replace("cache/1/0/1", "cache/1/0/2")));
		Assert.assertEquals("Keš GC4444", bag.getKesoidy().iterator().next().getNazev());
	}
}
