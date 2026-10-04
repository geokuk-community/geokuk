package cz.geokuk.core.program;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.util.file.Filex;

/** Umístění souborů ukazuje cestu tak, jak se uloží, a u relativní i výslednou cestu. */
public class JJedenSouborPanelTest {

	private final File koren = FConst.KOREN.getAbsoluteFile();

	@Test
	public void cestaVeSlozceGeokukJeRelativni() throws Exception {
		final JJedenSouborPanel panel = new JJedenSouborPanel(null, "Keše", true, true, true);
		panel.setFilex(new Filex(new File(koren, "data/gpx"), false, true));
		Assert.assertEquals("${GeoKuk}/data/gpx", panel.getZadanaCesta());
		Assert.assertEquals("Relativně ke složce GeoKuk: " + Filex.canonize(new File(koren, "data/gpx")).getPath(), panel.getVyslednaCesta());
		Assert.assertEquals(new Filex(Filex.canonize(new File(koren, "data/gpx")), false, true).getEffectiveFile(), panel.vezmiSouborAProver().getEffectiveFile());
	}

	@Test
	public void cestaMimoSlozkuJeAbsolutni() throws Exception {
		final File venku = new File(koren.getParentFile(), "jinde/gpx").getAbsoluteFile();
		final JJedenSouborPanel panel = new JJedenSouborPanel(null, "Keše", true, true, true);
		panel.setFilex(new Filex(venku, false, false));
		Assert.assertEquals(venku.getPath(), panel.getZadanaCesta());
		Assert.assertEquals(Filex.canonize(venku).getPath(), panel.getVyslednaCesta());
	}

	@Test
	public void popisVysledneCesty() {
		Assert.assertEquals("Relativně ke složce GeoKuk: " + new File(koren, "data").getPath(), JJedenSouborPanel.popisVysledneCesty(new File(koren, "data"), koren));
		Assert.assertEquals(koren.getPath(), JJedenSouborPanel.popisVysledneCesty(koren, koren));
	}

	/** Rozepsaná cesta, kterou systém nedovolí (ve Windows „C:“ složené se složkou programu, „a?b“), se ukáže, jak je. */
	@Test
	public void neplatnaCestaNespadne() {
		final File neplatna = new File(koren, "a\0b");
		Assert.assertEquals(neplatna.getPath(), JJedenSouborPanel.popisVysledneCesty(neplatna, koren));
		JJedenSouborPanel.popisVysledneCesty(new File(koren, "C:"), koren);
		JJedenSouborPanel.popisVysledneCesty(new File(koren, "a?b"), koren);
	}
}
