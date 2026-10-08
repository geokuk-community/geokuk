package cz.geokuk.core.program;

import java.io.File;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.util.file.Filex;

/** Umístění souborů ukazuje cestu tak, jak se uloží, a u relativní i výslednou cestu. */
public class JJedenSouborPanelTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

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

	/** Neexistující datová složka jiného programu (GeoGet, GSAK, OpenSAK) se nezaloží, uložení skončí srozumitelnou chybou. */
	@Test
	public void neexistujiciSlozkaJinehoProgramuSeNezalozi() throws Exception {
		final File chybi = new File(tmp.getRoot(), "geoget/data");
		final JJedenSouborPanel panel = new JJedenSouborPanel(null, "Datová složka GeoGetu.", true, true, true).nezakladat();
		panel.setFilex(new Filex(chybi, false, true));
		try {
			panel.vezmiSouborAProver();
			Assert.fail("uložení neexistující složky musí skončit chybou");
		} catch (final JPrehledSouboru.YNejdeTo e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("Datová složka GeoGetu: \"" + panel.getVyslednaCesta() + "\" neexistuje nebo není dostupná."));
		}
		Assert.assertFalse("složka se nesmí založit", chybi.exists());
	}

	@Test
	public void souborMistoSlozkyJinehoProgramuNeniCitelnaSlozka() throws Exception {
		final File soubor = tmp.newFile("gsak");
		final JJedenSouborPanel panel = new JJedenSouborPanel(null, "Datová složka GSAK.", true, true, true).nezakladat();
		panel.setFilex(new Filex(soubor, false, true));
		try {
			panel.vezmiSouborAProver();
			Assert.fail();
		} catch (final JPrehledSouboru.YNejdeTo e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("není čitelná složka"));
		}
	}

	@Test
	public void vypnutaNeboExistujiciSlozkaJinehoProgramuProjde() throws Exception {
		final JJedenSouborPanel vypnuta = new JJedenSouborPanel(null, "Datová složka OpenSAKu.", true, true, true).nezakladat();
		vypnuta.setFilex(new Filex(new File(tmp.getRoot(), "neni"), false, false));
		vypnuta.vezmiSouborAProver();
		final JJedenSouborPanel prazdna = new JJedenSouborPanel(null, "Datová složka OpenSAKu.", true, true, true).nezakladat();
		prazdna.setFilex(new Filex(tmp.newFolder("opensak"), false, true));
		prazdna.vezmiSouborAProver();
	}

	/** Vlastní složky GeoKuku (GPX, rendrování) se dál zakládají. */
	@Test
	public void vlastniSlozkaSeZalozi() throws Exception {
		final File chybi = new File(tmp.getRoot(), "gpx");
		final JJedenSouborPanel panel = new JJedenSouborPanel(null, "Keše", true, true, true);
		panel.setFilex(new Filex(chybi, false, true));
		panel.vezmiSouborAProver();
		Assert.assertTrue(chybi.isDirectory());
	}
}
