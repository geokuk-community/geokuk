package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;

public class PolygonPokrytiTest {

	/** Čtverec 10–20° v. d., 40–50° s. š. s dírou 14–16 / 44–46 a ostrov 30–31 / 40–41. */
	static final String CTVEREC_S_DIROU = "ctverec\n" //
			+ "1\n" //
			+ "   1.000000E+01   4.000000E+01\n" //
			+ "   2.000000E+01   4.000000E+01\n" //
			+ "   2.000000E+01   5.000000E+01\n" //
			+ "   1.000000E+01   5.000000E+01\n" //
			+ "   1.000000E+01   4.000000E+01\n" //
			+ "END\n" //
			+ "!2\n" //
			+ "  14 44\n  16 44\n  16 46\n  14 46\n" //
			+ "END\n" //
			+ "ostrov\n" //
			+ "\t30 40\n\t31 40\n\t31 41\n" //
			+ "END\n" //
			+ "END\n";

	private static PolygonPokryti nacti(final String text) throws IOException {
		return PolygonPokryti.nacti(new StringReader(text));
	}

	@Test
	public void bodUvnitrVneVDireANaOstrove() throws IOException {
		final PolygonPokryti p = nacti(CTVEREC_S_DIROU);
		assertTrue(p.obsahuje(new Wgs(42, 12)));
		assertTrue(p.obsahuje(new Wgs(49.9, 19.9)));
		assertFalse("díra", p.obsahuje(new Wgs(45, 15)));
		assertFalse(p.obsahuje(new Wgs(39.9, 15)));
		assertFalse(p.obsahuje(new Wgs(45, 20.1)));
		assertFalse(p.obsahuje(new Wgs(45, 25)));
		assertTrue("ostrov", p.obsahuje(new Wgs(40.2, 30.5)));
		assertFalse("ostrov je trojúhelník", p.obsahuje(new Wgs(40.9, 30.1)));
	}

	@Test
	public void nekonvexniObrys() throws IOException {
		// Tvar „U“: dno 0–3 × 0–1, ramena 0–1 a 2–3 do výšky 3.
		final PolygonPokryti p = nacti("u\n1\n0 0\n3 0\n3 3\n2 3\n2 1\n1 1\n1 3\n0 3\nEND\nEND\n");
		assertTrue(p.obsahuje(new Wgs(2, 0.5)));
		assertTrue(p.obsahuje(new Wgs(2, 2.5)));
		assertFalse("mezi rameny", p.obsahuje(new Wgs(2, 1.5)));
		assertTrue(p.obsahuje(new Wgs(0.5, 1.5)));
	}

	@Test
	public void obdelnikAPlocha() throws IOException {
		final PolygonPokryti p = nacti(CTVEREC_S_DIROU);
		assertEquals(40, p.getJih(), 0);
		assertEquals(50, p.getSever(), 0);
		assertEquals(10, p.getZapad(), 0);
		assertEquals(31, p.getVychod(), 0);
		// 5 vrcholů včetně opakovaného prvního = 100 stupňů², ostrov 0,5, díra −4.
		assertEquals(96.5, p.plocha(), 1e-9);
		assertEquals(2, p.getObrysy().size());
		assertEquals(1, p.getDiry().size());
		assertEquals(new Wgs(40, 10).lat, p.getObrysy().get(0).get(0).lat, 0);
		assertEquals(10, p.getObrysy().get(0).get(0).lon, 0);
	}

	@Test
	public void obdelnikBezSouboru() {
		final PolygonPokryti p = PolygonPokryti.obdelnik(48.5, 12, 51, 19);
		assertTrue(p.obsahuje(new Wgs(50, 14.4)));
		assertFalse(p.obsahuje(new Wgs(47, 14.4)));
		assertEquals(17.5, p.plocha(), 1e-9);
	}

	@Test
	public void windowsKonceRadkuAPrazdneRadky() throws IOException {
		final PolygonPokryti p = nacti("x\r\n\r\n1\r\n0 0\r\n2 0\r\n\r\n2 2\r\n0 2\r\nEND\r\nEND\r\n");
		assertTrue(p.obsahuje(new Wgs(1, 1)));
	}

	@Test
	public void chybneSoubory() {
		chyba("", "Prázdný");
		chyba("x\n1\n0 0\n1 0\n1 1\nEND\n", "nekončí řádkem END");
		chyba("x\n1\n0 0\n1 0\n1 1\n", "nekončí řádkem END");
		chyba("x\n1\n0 0\n1 0\nEND\nEND\n", "méně než tři");
		chyba("x\n1\n0 0\n1 0 5\n1 1\nEND\nEND\n", "řádek 4");
		chyba("x\n1\n0 0\n1 a\n1 1\nEND\nEND\n", "nemá čísla");
		chyba("x\n1\n0 0\n1 95\n1 1\nEND\nEND\n", "mimo rozsah");
		chyba("x\n!1\n0 0\n1 0\n1 1\nEND\nEND\n", "žádný obrys");
		chyba("x\nEND\n", "žádný obrys");
	}

	private static void chyba(final String text, final String cast) {
		try {
			nacti(text);
			fail("Čekám chybu: " + cast);
		} catch (final IOException e) {
			assertTrue(e.getMessage(), e.getMessage().toLowerCase().contains(cast.toLowerCase()));
		}
	}
}
