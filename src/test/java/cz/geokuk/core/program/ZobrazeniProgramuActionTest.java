package cz.geokuk.core.program;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.framework.MyPreferences;
import cz.geokuk.start.Start;

/** Volby zobrazení se ukládají pod klíči, které čte spouštěč. */
public class ZobrazeniProgramuActionTest {

	private final MyPreferences pref = MyPreferences.current().node("test-zobrazeni-programu");

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	@Test
	public void vychoziJeObojeZapnuto() {
		Assert.assertFalse("beze změny", ZobrazeniProgramuAction.uloz(pref, true, true));
		Assert.assertTrue(pref.getBoolean(Start.ZVETSENI_KLIC, false));
		Assert.assertTrue(pref.getBoolean(Start.DIRECT3D_KLIC, false));
	}

	@Test
	public void vypnutiSeUloziAHlasiZmenu() {
		Assert.assertTrue(ZobrazeniProgramuAction.uloz(pref, false, true));
		Assert.assertFalse(pref.getBoolean(Start.ZVETSENI_KLIC, true));
		Assert.assertTrue(ZobrazeniProgramuAction.uloz(pref, false, false));
		Assert.assertFalse(pref.getBoolean(Start.DIRECT3D_KLIC, true));
		Assert.assertFalse(ZobrazeniProgramuAction.uloz(pref, false, false));
	}

	@Test
	public void popisUkazujeMeritko() {
		Assert.assertEquals("Zvětšovat podle Windows (teď 125 %)", ZobrazeniProgramuAction.popisZvetseni(1.25));
		Assert.assertEquals("Zvětšovat podle Windows", ZobrazeniProgramuAction.popisZvetseni(1.0));
	}
}
