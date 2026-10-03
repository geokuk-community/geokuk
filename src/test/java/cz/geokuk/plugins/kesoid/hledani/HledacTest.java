package cz.geokuk.plugins.kesoid.hledani;

import static org.junit.Assert.*;

import org.junit.Test;

public class HledacTest {

	@Test
	public void textBezOhleduNaVelikostADiakritiku() {
		final Nalezenec nal = new Hledac.Porovnavac("zámek", false).porovnej("Pod ZAMKEM a u ZÁMKU");
		assertNull(nal);
		final Nalezenec zamku = new Hledac.Porovnavac("zamku", false).porovnej("Pod hradem u ZÁMKU");
		assertEquals(13, zamku.getPoc());
		assertEquals(18, zamku.getKon());
		assertNull(new Hledac.Porovnavac("hrad", false).porovnej("Pod zámkem"));
	}

	@Test
	public void regularniVyrazBezOhleduNaVelikostADiakritiku() {
		final Nalezenec nal = new Hledac.Porovnavac("z.mk[uy]", true).porovnej("Pod hradem u ZÁMKU");
		assertEquals(13, nal.getPoc());
		assertEquals(18, nal.getKon());
	}

	@Test
	public void velkaPismenaVRegularnimVyrazuMajiVyznam() {
		assertEquals("\\D je ne-číslice", 3, new Hledac.Porovnavac("\\D+", true).porovnej("123abc").getPoc());
		assertEquals("\\S je ne-mezera", 2, new Hledac.Porovnavac("\\S", true).porovnej("  x").getPoc());
		assertEquals("\\W je ne-písmeno", 3, new Hledac.Porovnavac("\\W", true).porovnej("abc-d").getPoc());
		assertEquals("\\Q…\\E je doslovný text", 3, new Hledac.Porovnavac("\\Qa.b\\E", true).porovnej("axbA.B").getPoc());
	}
}
