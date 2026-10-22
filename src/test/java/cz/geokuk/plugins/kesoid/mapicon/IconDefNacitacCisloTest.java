package cz.geokuk.plugins.kesoid.mapicon;

import org.junit.Assert;
import org.junit.Test;

/** Nesmyslné číslo ve jménu ikony uživatelské sady (posun, priorita) nesmí vyřadit celou sadu ikon. */
public class IconDefNacitacCisloTest {

	@Test
	public void cisloMimoRozsahDaVychozi() {
		Assert.assertEquals(12, IconDefNacitac.cislo("_x12", 0, "a_x12.png"));
		Assert.assertEquals(-3, IconDefNacitac.cislo("_y-3", 0, "a_y-3.png"));
		Assert.assertEquals(0, IconDefNacitac.cislo("_x99999999999", 0, "a_x99999999999.png"));
		Assert.assertEquals(IconDef.VYCHOZI_PRIORITA, IconDefNacitac.cislo("_p99999999999", IconDef.VYCHOZI_PRIORITA, "a.png"));
	}
}
