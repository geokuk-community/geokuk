package cz.geokuk.plugins.refbody;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;

public class TadyJsemDomaActionTest {

	@Test
	public void akceMaNazevSVypustkou() {
		Assert.assertEquals("Jsem doma…", new TadyJsemDomaAction().getValue(javax.swing.Action.NAME));
	}

	@Test
	public void dotazNazyvaDomovAUkazeDosavadni() {
		final String dotaz = TadyJsemDomaAction.textDotazu(new Wgs(49.8, 14.9), new Wgs(50.0, 14.4));
		Assert.assertEquals("Nastavit domov na " + new Wgs(49.8, 14.9) + "? Dosavadní domov: " + new Wgs(50.0, 14.4), dotaz);
		Assert.assertTrue(dotaz, dotaz.startsWith("Nastavit domov na N49°"));
	}
}
