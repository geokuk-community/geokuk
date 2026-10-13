package cz.geokuk.plugins.kesoid.kind.cgp;

import static org.junit.Assert.*;

import org.junit.Test;

/** Souřadnice JTSK z popisu geodetického bodu v cizím GPX. */
public class CgpJtskTest {

	@Test
	public void souradniceZPopisu() {
		final JtskSouradnice s = CgpGpxWptProcak.extrahujJtsk("Kukova hora 715'123.45 1043'456.78 512.30 (TrB)");
		assertNotNull(s);
		assertEquals(715123.45, s.y, 1e-6);
		assertEquals(1043456.78, s.x, 1e-6);
		assertEquals(512.30, s.z, 1e-6);
		assertEquals("Kukova hora ", s.pred);
		assertEquals(" (TrB)", s.po);
	}

	@Test
	public void necisloNeshodiImport() {
		// Místo desetinné tečky jiný znak; dřív parseDouble vyhodil NumberFormatException a import souboru selhal.
		assertNull(CgpGpxWptProcak.extrahujJtsk("Kukova hora 715'123x45 1043'456.78 512.30"));
	}
}
